package git.yannynz.organizadorproducao.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import git.yannynz.organizadorproducao.model.OpImport;
import git.yannynz.organizadorproducao.model.Order;
import git.yannynz.organizadorproducao.model.dto.OpImportRequestDTO;
import git.yannynz.organizadorproducao.repository.OpImportRepository;
import git.yannynz.organizadorproducao.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class OpImportService {

  private final OpImportRepository repo;
  private final OrderRepository orderRepo;
  private final ObjectMapper mapper;
  private final SimpMessagingTemplate ws;

  public OpImportService(OpImportRepository repo, OrderRepository orderRepo, ObjectMapper mapper, SimpMessagingTemplate ws) {
    this.repo = repo;
    this.orderRepo = orderRepo;
    this.mapper = mapper;
    this.ws = ws;
  }

  // ---- WS helpers ----
private void notifyOrder(Order order) {
  ws.convertAndSend("/topic/orders", order);
}

private void notifyOpImported(OpImport saved, OpImportRequestDTO req, boolean linkedNow) {
  var payload = new java.util.LinkedHashMap<String, Object>();

  payload.put("numeroOp", saved.getNumeroOp());                              // assumindo não-nulo
  payload.put("emborrachada", saved.isEmborrachada());

  // Só adiciona se não for nulo
  if (saved.getSharePath() != null && !saved.getSharePath().isBlank())
    payload.put("sharePath", saved.getSharePath());

  if (saved.getDataOp() != null)
    payload.put("dataOp", saved.getDataOp().toString());

  if (saved.getFacaId() != null)
    payload.put("facaId", saved.getFacaId());

  payload.put("linkedNow", linkedNow);

  // Conta materiais com null-safe
  int materiaisCount = (saved.getMateriais() == null || saved.getMateriais().isNull())
      ? 0
      : saved.getMateriais().size();
  payload.put("materiaisCount", materiaisCount);

  ws.convertAndSend("/topic/ops", payload);
}



  /** Acrescenta "emborrachada" em observacao, sem duplicar, e envia WS. */
  @Transactional
  protected boolean markOrderAsEmborrachada(Order f) {
    if (f == null) return false;

    String obs = Optional.ofNullable(f.getObservacao()).orElse("").trim();
    String obsLower = obs.toLowerCase(Locale.ROOT);

    if (!obsLower.contains("emborrachada")) {
      String nova = obs.isBlank() ? "emborrachada" : obs + "; emborrachada";
      f.setObservacao(nova);
      orderRepo.save(f);

      notifyOrder(f);
      return true;
    }
    return false;
  }

  /** Tenta localizar pedido por NR e aplicar marcação; retorna true se aplicou. */
  @Transactional
  protected boolean tryPropagateToOrder(String numeroOp, boolean emborrachada) {
    if (!emborrachada || numeroOp == null || numeroOp.isBlank()) return false;

    return orderRepo.findTopByNrOrderByIdDesc(numeroOp)
        .map(this::markOrderAsEmborrachada)
        .orElse(false);
  }

  /** Re-tenta de forma assíncrona (backoff simples) sem bloquear a thread do listener. */
  @Async("opExecutor")
  protected void schedulePropagationRetries(String numeroOp, boolean emborrachada) {
    if (!emborrachada || numeroOp == null || numeroOp.isBlank()) return;

    int attempts = 3;
    long waitMs = 1500L;

    for (int i = 0; i < attempts; i++) {
      try {
        TimeUnit.MILLISECONDS.sleep(waitMs);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        return;
      }
      boolean applied = tryPropagateToOrder(numeroOp, emborrachada);
      if (applied) return;
      waitMs *= 2; // backoff exponencial simples: 1.5s, 3s, 6s
    }
    // Se ainda não aplicou, o agendador periódico (reconcileOpsSemFaca) fará o restante.
  }

  // ---------------------------
  // Importação de OP
  // ---------------------------
  @Transactional
public void importar(OpImportRequestDTO req) {
  if (req.getNumeroOp() == null || req.getNumeroOp().isBlank()) return;

  OpImport op = repo.findByNumeroOp(req.getNumeroOp()).orElseGet(OpImport::new);
  op.setNumeroOp(req.getNumeroOp());
  op.setSharePath(req.getSharePath());
  op.setEmborrachada(Boolean.TRUE.equals(req.getEmborrachada()));

  if (req.getDataOp() != null && !req.getDataOp().isBlank()) {
    op.setDataOp(java.time.LocalDate.parse(req.getDataOp()));
  }
  if (req.getMateriais() != null) {
    op.setMateriais(mapper.valueToTree(req.getMateriais())); // ArrayNode
  }

  final OpImport saved = repo.save(op);

  // tenta linkar com pedido e já propagar 'emborrachada'
  final boolean[] linkedNow = { false };
  orderRepo.findTopByNrOrderByIdDesc(req.getNumeroOp()).ifPresent(f -> {
    if (saved.getFacaId() == null || !saved.getFacaId().equals(f.getId())) {
      saved.setFacaId(f.getId());
      repo.save(saved);
      linkedNow[0] = true;
    }
    if (Boolean.TRUE.equals(req.getEmborrachada())) {
      // atualiza observação no pedido + WS /topic/orders
      markOrderAsEmborrachada(f);
    }
  });

  // se ainda não tinha pedido, agenda re-tentativa
  if (Boolean.TRUE.equals(req.getEmborrachada())) {
    schedulePropagationRetries(req.getNumeroOp(), true);
  }

  // WS da própria OP (para um painel/toast no front)
  notifyOpImported(saved, req, linkedNow[0]);
}


  // ---------------------------
  // Faca montada
  // ---------------------------
  @Transactional
  public void onFacaMontada(Long facaId) {
    boolean emborrachada = repo.findTopByFacaIdOrderByCreatedAtDesc(facaId)
        .map(OpImport::isEmborrachada)
        .orElse(false);

    if (!emborrachada) {
      Order f = orderRepo.findById(facaId).orElseThrow();
      f.setStatus(2); // Prontas p/ Entrega
      orderRepo.save(f);
      notifyOrder(f);
    }
  }

  // ---------------------------
  // RabbitMQ
  // ---------------------------
  @RabbitListener(queues = "op.imported", containerFactory = "stringListenerFactory")
  public void onMessage(String json) throws Exception {
    var req = mapper.readValue(json, OpImportRequestDTO.class);
    importarAsync(req); // dispara assíncrono
  }

  @Async("opExecutor")
  @Transactional
  public void importarAsync(OpImportRequestDTO req) { importar(req); }

  // ---------------------------
  // Link do Pedido recém-criado com a OP (quando o Pedido nasce depois)
  // ---------------------------
  @Async("opExecutor")
  @Transactional
  public void tryLinkAsync(String nr, Long facaId) {
    if (nr == null || nr.isBlank() || facaId == null) return;

    repo.findByNumeroOp(nr).ifPresent(op -> {
      boolean dirty = false;
      if (op.getFacaId() == null || !op.getFacaId().equals(facaId)) {
        op.setFacaId(facaId);
        dirty = true;
      }
      if (dirty) repo.save(op);

      // Propaga "emborrachada" para o Pedido agora que ele existe
      if (op.isEmborrachada()) {
        orderRepo.findById(facaId).ifPresent(this::markOrderAsEmborrachada);
      }
    });
  }

  // ---------------------------
  // Reconciliação periódica: liga OPs sem faca e propaga "emborrachada"
  // ---------------------------
  @Scheduled(fixedDelay = 50_000) // 0.9 min
  @Transactional
  public void reconcileOpsSemFaca() {
    repo.findAll().stream()
        .filter(o -> o.getNumeroOp() != null && !o.getNumeroOp().isBlank())
        .forEach(o -> orderRepo.findTopByNrOrderByIdDesc(o.getNumeroOp()).ifPresent(f -> {
          boolean dirty = false;

          if (o.getFacaId() == null || !o.getFacaId().equals(f.getId())) {
            o.setFacaId(f.getId());
            dirty = true;
          }
          if (dirty) repo.save(o);

          if (o.isEmborrachada()) {
            markOrderAsEmborrachada(f); // idempotente
          }
        }));
  }
}

