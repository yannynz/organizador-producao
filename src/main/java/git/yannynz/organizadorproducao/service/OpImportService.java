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

@Transactional
public void importar(OpImportRequestDTO req) {
  if (req.getNumeroOp() == null || req.getNumeroOp().isBlank()) return;

  OpImport op = repo.findByNumeroOp(req.getNumeroOp()).orElseGet(OpImport::new);
  op.setNumeroOp(req.getNumeroOp());
  op.setSharePath(req.getSharePath());
  op.setEmborrachada(Boolean.TRUE.equals(req.getEmborrachada()));

  if (req.getDataOp()!=null && !req.getDataOp().isBlank()) {
    op.setDataOp(java.time.LocalDate.parse(req.getDataOp()));
  }
  if (req.getMateriais()!=null) {
    op.setMateriais(mapper.valueToTree(req.getMateriais())); // ArrayNode
  }

  // >>> não reatribua 'op' depois; crie uma referência 'saved' e use ela no lambda
  final OpImport saved = repo.save(op);

  orderRepo.findTopByNrOrderByIdDesc(req.getNumeroOp()).ifPresent(f -> {
    boolean changed = false;

    if (saved.getFacaId() == null || !saved.getFacaId().equals(f.getId())) {
      saved.setFacaId(f.getId());
      repo.save(saved);
    }

    if (Boolean.TRUE.equals(req.getEmborrachada())) {
      String obs = java.util.Optional.ofNullable(f.getObservacao()).orElse("");
      if (!obs.toLowerCase().contains("emborrachada")) {
        f.setObservacao(obs.isBlank() ? "emborrachada" : obs + "; emborrachada");
        orderRepo.save(f);
        changed = true;
      }
    }

    if (changed) {
      ws.convertAndSend("/topic/orders",
        new git.yannynz.organizadorproducao.service.WebSocketMessage("update", f));
    }
  });

  // Notifica sempre que importar a OP
  ws.convertAndSend("/topic/ops", java.util.Map.of(
    "type", "imported",
    "numeroOp", req.getNumeroOp(),
    "emborrachada", Boolean.TRUE.equals(req.getEmborrachada()),
    "sharePath", req.getSharePath()
  ));
}



  @Transactional
  public void onFacaMontada(Long facaId) {
    boolean emborrachada = repo.findTopByFacaIdOrderByCreatedAtDesc(facaId)
        .map(OpImport::isEmborrachada)
        .orElse(false);

    if (!emborrachada) {
      Order f = orderRepo.findById(facaId).orElseThrow();
      f.setStatus(2); // Prontas p/ Entrega
      orderRepo.save(f);
    }

  }

@RabbitListener(queues = "op.imported")
public void onMessage(String json) throws Exception {
  var req = mapper.readValue(json, OpImportRequestDTO.class);
  importarAsync(req); // dispara assíncrono
}


@Async("opExecutor")
@Transactional
public void importarAsync(OpImportRequestDTO req) { importar(req); }


@Async("opExecutor")
@Transactional
public void tryLinkAsync(String nr, Long facaId) {
  if (nr == null || nr.isBlank() || facaId == null) return;

  repo.findByNumeroOp(nr).ifPresent(op -> {
    if (op.getFacaId()==null || !op.getFacaId().equals(facaId)) {
      op.setFacaId(facaId);
      repo.save(op);
    }
  });
}

@Scheduled(fixedDelay = 300_000) // 5 min
public void reconcileOpsSemFaca() {
  repo.findAll().stream()
      .filter(o -> o.getFacaId() == null && o.getNumeroOp()!=null && !o.getNumeroOp().isBlank())
      .forEach(o ->
          orderRepo.findTopByNrOrderByIdDesc(o.getNumeroOp()).ifPresent(f -> {
            o.setFacaId(f.getId());
            repo.save(o);
          })
      );
}




}

