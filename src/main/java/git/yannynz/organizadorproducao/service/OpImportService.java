package git.yannynz.organizadorproducao.service;

import git.yannynz.organizadorproducao.model.OpImport;
import git.yannynz.organizadorproducao.model.Order;
import git.yannynz.organizadorproducao.model.dto.OpImportRequestDTO;
import git.yannynz.organizadorproducao.repository.OpImportRepository;
import git.yannynz.organizadorproducao.repository.OrderRepository;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class OpImportService {

    private final OpImportRepository repo;
    private final OrderRepository orderRepo;
    private final ObjectMapper mapper;
    private final SimpMessagingTemplate ws;
    private final Logger log = LoggerFactory.getLogger(OpImportService.class);

    private final ClienteAutoEnrichmentService clienteAuto;

    public OpImportService(OpImportRepository repo, OrderRepository orderRepo, ObjectMapper mapper,
            SimpMessagingTemplate ws, ClienteAutoEnrichmentService clienteAuto) {
        this.repo = repo;
        this.orderRepo = orderRepo;
        this.mapper = mapper;
        this.ws = ws;
        this.clienteAuto = clienteAuto;
    }

    // ---- WS helpers ----
    private void notifyOrder(Order order) {
        ws.convertAndSend("/topic/orders", order);
    }

    private void notifyOpImported(OpImport saved, OpImportRequestDTO req, boolean linkedNow) {
        var payload = new java.util.LinkedHashMap<String, Object>();

        payload.put("numeroOp", saved.getNumeroOp()); // assumindo não-nulo
        payload.put("emborrachada", saved.isEmborrachada());
        payload.put("vaiVinco", Boolean.TRUE.equals(saved.getVaiVinco()));

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

    /**
     * Marca pedido como emborrachada respeitando travas manuais, sem tocar na
     * observação.
     */
    @Transactional
    protected boolean markOrderAsEmborrachada(Order f, Optional<OpImport> maybeOp) {
        if (f == null)
            return false;

        boolean manualLock = maybeOp.map(OpImport::isManualLockEmborrachada).orElse(false);
        if (manualLock || f.isEmborrachada())
            return false;

        f.setEmborrachada(true);
        orderRepo.save(f);
        notifyOrder(f);
        return true;
    }

    /**
     * Tenta localizar pedido por NR e aplicar marcação; retorna true se aplicou.
     */
    @Transactional
    protected boolean tryPropagateToOrder(String numeroOp, boolean emborrachada) {
        if (!emborrachada || numeroOp == null || numeroOp.isBlank())
            return false;

        Optional<OpImport> maybeOp = repo.findByNumeroOp(numeroOp);

        return orderRepo.findTopByNrOrderByIdDesc(numeroOp)
                .map(order -> markOrderAsEmborrachada(order, maybeOp))
                .orElse(false);
    }

    /**
     * Re-tenta de forma assíncrona (backoff simples) sem bloquear a thread do
     * listener.
     */
    @Async("opExecutor")
    protected void schedulePropagationRetries(String numeroOp, boolean emborrachada) {
        if (!emborrachada || numeroOp == null || numeroOp.isBlank())
            return;

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
            if (applied)
                return;
            waitMs *= 2; // backoff exponencial simples: 1.5s, 3s, 6s
        }
        // Se ainda não aplicou, o agendador periódico (reconcileOpsSemFaca) fará o
        // restante.
    }

    // ---------------------------
    // Importação de OP
    // ---------------------------
    @Transactional
    public void importar(OpImportRequestDTO req) {
        try {
            log.info("[IMPORT] recebida mensagem: numeroOp={}, dataOp={}",
                    (req != null ? req.getNumeroOp() : null), (req != null ? req.getDataOp() : null));
        } catch (Exception ignore) {
        }
        // Validações iniciais
        if (req == null) {
            log.warn("OpImportRequestDTO é null, ignorando import");
            return;
        }
        String numeroOp = req.getNumeroOp();
        if (numeroOp == null || numeroOp.isBlank()) {
            log.warn("NumeroOp está vazio ou nulo no request, ignorando import");
            return;
        }

        // Carrega ou cria OpImport
        OpImport op = repo.findByNumeroOp(numeroOp)
                .orElseGet(OpImport::new);

        op.setNumeroOp(numeroOp);
        op.setSharePath(req.getSharePath());

        // Data da OP (tolerante a vários formatos)
        if (req.getDataOp() != null && !req.getDataOp().isBlank()) {
            try {
                op.setDataOp(parseToZoned(req.getDataOp(), ZoneId.of("America/Sao_Paulo")));
            } catch (Exception e) {
                log.error("Falha ao parsear dataOp '{}' para OP {}: {}", req.getDataOp(), numeroOp, e.getMessage());
            }
        }

        // Materiais
        if (req.getMateriais() != null) {
            try {
                op.setMateriais(mapper.valueToTree(req.getMateriais()));
            } catch (Exception e) {
                log.error("Falha ao serializar materiais para OP {}: {}", numeroOp, e.getMessage());
            }
        }

        // Campos extras padronizados
        if (req.getDestacador() != null && !req.getDestacador().isBlank()) {
            op.setDestacador(req.getDestacador().trim().toUpperCase());
        }
        if (req.getModalidadeEntrega() != null && !req.getModalidadeEntrega().isBlank()) {
            op.setModalidadeEntrega(req.getModalidadeEntrega().trim().toUpperCase());
        } else if (op.getModalidadeEntrega() == null || op.getModalidadeEntrega().isBlank()) {
            // Default de negócio quando não informado pela OP
            op.setModalidadeEntrega("A ENTREGAR");
        }
        if (req.getDataRequeridaEntrega() != null && !req.getDataRequeridaEntrega().isBlank()) {
            try {
                ZonedDateTime dt = parseToZoned(req.getDataRequeridaEntrega(), ZoneId.of("America/Sao_Paulo"));
                op.setDataRequeridaEntrega(dt);
            } catch (Exception e) {
                log.error("Falha ao parsear dataRequeridaEntrega '{}': {}", req.getDataRequeridaEntrega(),
                        e.getMessage());
            }
        }
        if (req.getUsuarioImportacao() != null && !req.getUsuarioImportacao().isBlank()) {
            op.setUsuarioImportacao(req.getUsuarioImportacao().trim());
        }

        Optional<Order> maybeOrder = orderRepo.findTopByNrOrderByIdDesc(numeroOp);

        boolean manualLockEmborrachada = op.isManualLockEmborrachada();
        boolean manualLockPertinax = op.isManualLockPertinax();
        boolean manualLockPoliester = op.isManualLockPoliester();
        boolean manualLockPapelCalibrado = op.isManualLockPapelCalibrado();
        boolean manualLockVaiVinco = op.isManualLockVaiVinco();

        boolean previousOpEmborrachada = op.isEmborrachada();
        Boolean previousPertinax = op.getPertinax();
        Boolean previousPoliester = op.getPoliester();
        Boolean previousPapelCalibrado = op.getPapelCalibrado();
        Boolean previousVaiVinco = op.getVaiVinco();

        Boolean requestedEmborrachada = req.getEmborrachada();
        Boolean requestedPertinax = req.getPertinax();
        Boolean requestedPoliester = req.getPoliester();
        Boolean requestedPapelCalibrado = req.getPapelCalibrado();
        Boolean requestedVaiVinco = req.getVaiVinco();

        boolean newOpEmborrachada = requestedEmborrachada != null ? Boolean.TRUE.equals(requestedEmborrachada)
                : previousOpEmborrachada;
        op.setEmborrachada(newOpEmborrachada);
        if (!newOpEmborrachada && manualLockEmborrachada) {
            op.setManualLockEmborrachada(false);
            manualLockEmborrachada = false;
        }

        Boolean newPertinax = requestedPertinax != null ? requestedPertinax : previousPertinax;
        op.setPertinax(newPertinax);
        if (!Boolean.TRUE.equals(newPertinax) && manualLockPertinax) {
            op.setManualLockPertinax(false);
            manualLockPertinax = false;
        }

        Boolean newPoliester = requestedPoliester != null ? requestedPoliester : previousPoliester;
        op.setPoliester(newPoliester);
        if (!Boolean.TRUE.equals(newPoliester) && manualLockPoliester) {
            op.setManualLockPoliester(false);
            manualLockPoliester = false;
        }

        Boolean newPapelCalibrado = requestedPapelCalibrado != null ? requestedPapelCalibrado : previousPapelCalibrado;
        op.setPapelCalibrado(newPapelCalibrado);
        if (!Boolean.TRUE.equals(newPapelCalibrado) && manualLockPapelCalibrado) {
            op.setManualLockPapelCalibrado(false);
            manualLockPapelCalibrado = false;
        }

        Boolean newVaiVinco = requestedVaiVinco != null ? requestedVaiVinco : previousVaiVinco;
        op.setVaiVinco(newVaiVinco);
        if (!Boolean.TRUE.equals(newVaiVinco) && manualLockVaiVinco) {
            op.setManualLockVaiVinco(false);
            manualLockVaiVinco = false;
        }

        log.info(
                "[IMPORT] flags calculadas OP {} -> emborrachada={}, pertinax={}, poliester={}, papelCalibrado={}, vaiVinco={}, locks(e={},pt={},po={},pc={},vinco={})",
                numeroOp,
                newOpEmborrachada,
                newPertinax,
                newPoliester,
                newPapelCalibrado,
                newVaiVinco,
                manualLockEmborrachada,
                manualLockPertinax,
                manualLockPoliester,
                manualLockPapelCalibrado,
                manualLockVaiVinco);

        boolean shouldApplyEmborrachada = newOpEmborrachada && !manualLockEmborrachada;

        // Enriquecimento automatico de cliente/endereco
        try {
            clienteAuto.upsertFromOp(op, req);
        } catch (Exception e) {
            log.error("Erro no enriquecimento de cliente para OP {}: {}", numeroOp, e.getMessage());
        }

        // Salvar OpImport
        OpImport savedOp = repo.save(op);
        try {
            log.info("[IMPORT] OP salva: id={}, nr={}, emborrachada={}, dataOp={}",
                    savedOp.getId(), savedOp.getNumeroOp(), savedOp.isEmborrachada(), savedOp.getDataOp());
        } catch (Exception ignore) {
        }

        // Buscar e atualizar Order correspondente
        boolean orderUpdated = false;

        if (maybeOrder.isPresent()) {
            Order order = maybeOrder.get();

            // Atualizar campos diretos
            if (!isBlank(op.getDestacador()) && isBlank(order.getDestacador())) {
                order.setDestacador(op.getDestacador());
                orderUpdated = true;
            }
            if (!isBlank(op.getModalidadeEntrega()) && isBlank(order.getModalidadeEntrega())) {
                order.setModalidadeEntrega(op.getModalidadeEntrega());
                orderUpdated = true;
            }
            if (op.getDataRequeridaEntrega() != null && order.getDataRequeridaEntrega() == null) {
                order.setDataRequeridaEntrega(op.getDataRequeridaEntrega());
                orderUpdated = true;
            }
            if (!isBlank(op.getUsuarioImportacao()) && isBlank(order.getUsuarioImportacao())) {
                order.setUsuarioImportacao(op.getUsuarioImportacao());
                orderUpdated = true;
            }
            
            // Propagate enriched client info
            if (op.getClienteRef() != null) {
                if (order.getClienteRef() == null) {
                    order.setClienteRef(op.getClienteRef());
                    orderUpdated = true;
                }
                // Se o cliente tem transportadora padrão definida e o pedido ainda não tem, preenche
                if (op.getClienteRef().getTransportadora() != null && order.getTransportadora() == null) {
                    order.setTransportadora(op.getClienteRef().getTransportadora());
                    orderUpdated = true;
                }
            }

            if (op.getEndereco() != null && order.getEndereco() == null) {
                order.setEndereco(op.getEndereco());
                orderUpdated = true;
            }

            if (shouldApplyEmborrachada && !order.isEmborrachada()) {
                order.setEmborrachada(true);
                orderUpdated = true;
                log.info("[IMPORT] OP {} marcou pedido {} como emborrachada", numeroOp, order.getId());
            }

            if (Boolean.TRUE.equals(newVaiVinco) && !manualLockVaiVinco && !order.isVaiVinco()) {
                order.setVaiVinco(true);
                orderUpdated = true;
                log.info("[IMPORT] OP {} marcou pedido {} como vaiVinco", numeroOp, order.getId());
            }

            // Default de modalidade quando ainda ausente
            if (order.getModalidadeEntrega() == null || order.getModalidadeEntrega().isBlank()) {
                order.setModalidadeEntrega("A ENTREGAR");
                orderUpdated = true;
            }

            // Materiais especiais
            if (Boolean.TRUE.equals(newPertinax) && !manualLockPertinax && !order.isPertinax()) {
                order.setPertinax(true);
                orderUpdated = true;
                log.info("[IMPORT] OP {} marcou pedido {} como pertinax", numeroOp, order.getId());
            }
            if (Boolean.TRUE.equals(newPoliester) && !manualLockPoliester && !order.isPoliester()) {
                order.setPoliester(true);
                orderUpdated = true;
                log.info("[IMPORT] OP {} marcou pedido {} como poliester", numeroOp, order.getId());
            }
            if (Boolean.TRUE.equals(newPapelCalibrado) && !manualLockPapelCalibrado && !order.isPapelCalibrado()) {
                order.setPapelCalibrado(true);
                orderUpdated = true;
                log.info("[IMPORT] OP {} marcou pedido {} como papelCalibrado", numeroOp, order.getId());
            }

            // Removido: não anexar mais tags na observação; atualiza apenas campos
            if (orderUpdated) {
                orderRepo.save(order);
                notifyOrder(order);
            }
        }

        // Linkar OP com pedido
        final boolean[] linkedNowHolder = { false };
        maybeOrder.ifPresent(order -> {
            if (savedOp.getFacaId() == null || !savedOp.getFacaId().equals(order.getId())) {
                savedOp.setFacaId(order.getId());
                repo.save(savedOp);
                linkedNowHolder[0] = true;
            }
        });

        // Fallback de data/hora requerida com base na prioridade, se não foi definida
        if (savedOp.getDataRequeridaEntrega() == null) {
            orderRepo.findTopByNrOrderByIdDesc(numeroOp).ifPresent(ord -> {
                String pr = ord.getPrioridade();
                if (pr != null) {
                    java.time.ZoneId tz = java.time.ZoneId.of("America/Sao_Paulo");
                    java.time.ZonedDateTime base = java.time.ZonedDateTime.now(tz).withHour(18).withMinute(0)
                            .withSecond(0).withNano(0);
                    java.time.ZonedDateTime dt = null;
                    switch (pr) {
                        case "VERMELHO":
                            dt = base;
                            break; // hoje 18:00
                        case "AMARELO":
                            dt = base.plusDays(1);
                            break; // amanhã 18:00
                        case "AZUL":
                            dt = base.plusDays(2);
                            break; // depois de amanhã 18:00
                        default:
                            break; // VERDE: sem prazo
                    }
                    if (dt != null) {
                        ord.setDataRequeridaEntrega(dt);
                        String obs = java.util.Optional.ofNullable(ord.getObservacao()).orElse("");
                        String obsLower2 = obs.toLowerCase(java.util.Locale.ROOT);
                        if (!obsLower2.contains("entrega="))
                            appendTag(new StringBuilder(obs), "entrega=" + dt);
                        orderRepo.save(ord);
                        notifyOrder(ord);
                    }
                }
            });
        }

        // WS
        notifyOpImported(savedOp, req, linkedNowHolder[0]);

        // Retentativa de propagação emborrachada
        if (shouldApplyEmborrachada) {
            schedulePropagationRetries(numeroOp, true);
        }
    }

    // Converte string para ZonedDateTime aceitando formatos sem timezone
    private ZonedDateTime parseToZoned(String s, ZoneId defaultZone) {
        String str = s.trim();
        // yyyy-MM-dd
        if (str.matches("\\d{4}-\\d{2}-\\d{2}")) {
            LocalDate d = LocalDate.parse(str, DateTimeFormatter.ISO_LOCAL_DATE);
            return d.atStartOfDay(defaultZone);
        }
        // yyyy-MM-ddTHH:mm ou yyyy-MM-ddTHH:mm:ss (sem timezone)
        if (str.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2})?")) {
            // garante segundos
            String norm = (str.length() == 16) ? str + ":00" : str;
            LocalDateTime ldt = LocalDateTime.parse(norm, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            return ldt.atZone(defaultZone);
        }
        // ISO com offset e/ou região (ex.:
        // 2025-09-16T00:00:00-03:00[America/Sao_Paulo])
        return ZonedDateTime.parse(str);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean equalsInstant(ZonedDateTime a, ZonedDateTime b) {
        if (a == b)
            return true;
        if (a == null || b == null)
            return false;
        return a.toInstant().equals(b.toInstant());
    }

    // -- auxiliares --

    private void appendTag(StringBuilder sb, String tag) {
        if (sb.length() > 0) {
            sb.append("; ");
        }
        sb.append(tag);
    }

    private boolean containsTag(String obs, String chaveLower) {
        if (obs == null)
            return false;
        String lower = obs.toLowerCase(Locale.ROOT);
        return lower.contains(chaveLower.toLowerCase(Locale.ROOT));
    }

    // ---------------------------
    // Faca montada
    // ---------------------------
    @Transactional
    public void onFacaMontada(Long facaId) {
        Optional<OpImport> maybeOp = repo.findTopByFacaIdOrderByCreatedAtDesc(facaId);
        Order order = orderRepo.findById(facaId).orElseThrow();

        boolean emborrachada = maybeOp.map(OpImport::isEmborrachada)
                .orElse(order.isEmborrachada());
        if (emborrachada) {
            return;
        }

        maybeOp.map(OpImport::getVaiVinco)
                .filter(Objects::nonNull)
                .ifPresent(v -> order.setVaiVinco(Boolean.TRUE.equals(v)));

        if (OrderStatusRules.applyAutoProntoEntrega(order)) {
            orderRepo.save(order);
            notifyOrder(order);
        }
    }

    // ---------------------------
    // RabbitMQ
    // ---------------------------
    // Listener definido em AmqpConfig (OpImportedListener) já chama
    // service.importar(req)
    // Mantemos apenas o método assíncrono público caso seja útil em outros fluxos
    @Async("opExecutor")
    @Transactional
    public void importarAsync(OpImportRequestDTO req) {
        importar(req);
    }

    // ---------------------------
    // Link do Pedido recém-criado com a OP (quando o Pedido nasce depois)
    // ---------------------------
    @Async("opExecutor")
    @Transactional
    public void tryLinkAsync(String nr, Long facaId) {
        if (nr == null || nr.isBlank() || facaId == null)
            return;

        repo.findByNumeroOp(nr).ifPresent(op -> {
            boolean dirty = false;
            if (op.getFacaId() == null || !op.getFacaId().equals(facaId)) {
                op.setFacaId(facaId);
                dirty = true;
            }
            if (dirty)
                repo.save(op);

            orderRepo.findById(facaId).ifPresent(order -> {
                boolean orderUpdated = false;
                boolean opUpdated = false;

                boolean alreadyLinked = op.getFacaId() != null && op.getFacaId().equals(facaId);

                // Sincroniza Cliente/Transportadora/Endereco se ausente no pedido
                if (op.getClienteRef() != null) {
                    if (order.getClienteRef() == null) {
                        order.setClienteRef(op.getClienteRef());
                        orderUpdated = true;
                    }
                    if (op.getClienteRef().getTransportadora() != null && order.getTransportadora() == null) {
                        order.setTransportadora(op.getClienteRef().getTransportadora());
                        orderUpdated = true;
                    }
                }
                if (op.getEndereco() != null && order.getEndereco() == null) {
                    order.setEndereco(op.getEndereco());
                    orderUpdated = true;
                }

                boolean manualLockEmborrachada = op.isManualLockEmborrachada();

                if (!alreadyLinked && op.isEmborrachada() && !order.isEmborrachada()) {
                    order.setEmborrachada(true);
                    orderUpdated = true;
                    log.info("[LINK] Pedido {} recebeu emborrachada=true da OP {} (link tardio)", order.getId(),
                            op.getNumeroOp());
                } else {
                    if (order.isEmborrachada()) {
                        if (!op.isEmborrachada()) {
                            op.setEmborrachada(true);
                            opUpdated = true;
                            log.info("[LINK] OP {} sincronizada com pedido {} emborrachada=true", op.getNumeroOp(),
                                    order.getId());
                        }
                        if (manualLockEmborrachada) {
                            op.setManualLockEmborrachada(false);
                            opUpdated = true;
                            log.info("[LINK] OP {} removeu lock manual emborrachada ao detectar pedido {}=true",
                                    op.getNumeroOp(), order.getId());
                        }
                    } else if (op.isEmborrachada() && !manualLockEmborrachada) {
                        order.setEmborrachada(true);
                        orderUpdated = true;
                        log.info("[LINK] Pedido {} ajustado para emborrachada=true a partir da OP {}", order.getId(),
                                op.getNumeroOp());
                    }
                }

                if (!isBlank(order.getDestacador())) {
                    if (isBlank(op.getDestacador()) || !Objects.equals(order.getDestacador(), op.getDestacador())) {
                        op.setDestacador(order.getDestacador());
                        opUpdated = true;
                    }
                } else if (!isBlank(op.getDestacador())) {
                    order.setDestacador(op.getDestacador());
                    orderUpdated = true;
                }

                if (!isBlank(order.getModalidadeEntrega())) {
                    if (isBlank(op.getModalidadeEntrega())
                            || !Objects.equals(order.getModalidadeEntrega(), op.getModalidadeEntrega())) {
                        op.setModalidadeEntrega(order.getModalidadeEntrega());
                        opUpdated = true;
                    }
                } else if (!isBlank(op.getModalidadeEntrega())) {
                    order.setModalidadeEntrega(op.getModalidadeEntrega());
                    orderUpdated = true;
                }

                if (order.getDataRequeridaEntrega() != null) {
                    if (!equalsInstant(order.getDataRequeridaEntrega(), op.getDataRequeridaEntrega())) {
                        op.setDataRequeridaEntrega(order.getDataRequeridaEntrega());
                        opUpdated = true;
                    }
                } else if (op.getDataRequeridaEntrega() != null) {
                    order.setDataRequeridaEntrega(op.getDataRequeridaEntrega());
                    orderUpdated = true;
                }

                if (!isBlank(order.getUsuarioImportacao())) {
                    if (isBlank(op.getUsuarioImportacao())
                            || !Objects.equals(order.getUsuarioImportacao(), op.getUsuarioImportacao())) {
                        op.setUsuarioImportacao(order.getUsuarioImportacao());
                        opUpdated = true;
                    }
                } else if (!isBlank(op.getUsuarioImportacao())) {
                    order.setUsuarioImportacao(op.getUsuarioImportacao());
                    orderUpdated = true;
                }

                Boolean opPertinax = op.getPertinax();
                boolean lockPertinax = op.isManualLockPertinax();
                if (order.isPertinax()) {
                    if (!Boolean.TRUE.equals(opPertinax)) {
                        op.setPertinax(true);
                        opUpdated = true;
                        log.info("[LINK] OP {} recebeu pertinax=true do pedido {}", op.getNumeroOp(), order.getId());
                    }
                    if (lockPertinax) {
                        op.setManualLockPertinax(false);
                        opUpdated = true;
                        log.info("[LINK] OP {} removeu lock pertinax após pedido {}=true", op.getNumeroOp(),
                                order.getId());
                    }
                } else {
                    if (Boolean.TRUE.equals(opPertinax) && !lockPertinax) {
                        order.setPertinax(true);
                        orderUpdated = true;
                        log.info("[LINK] Pedido {} recebeu pertinax=true da OP {}", order.getId(), op.getNumeroOp());
                    } else if (lockPertinax && !Boolean.TRUE.equals(opPertinax)) {
                        op.setManualLockPertinax(false);
                        opUpdated = true;
                        log.info("[LINK] OP {} limpou lock pertinax pois pedido {} segue false", op.getNumeroOp(),
                                order.getId());
                    }
                }

                Boolean opPoliester = op.getPoliester();
                boolean lockPoliester = op.isManualLockPoliester();
                if (order.isPoliester()) {
                    if (!Boolean.TRUE.equals(opPoliester)) {
                        op.setPoliester(true);
                        opUpdated = true;
                        log.info("[LINK] OP {} recebeu poliester=true do pedido {}", op.getNumeroOp(), order.getId());
                    }
                    if (lockPoliester) {
                        op.setManualLockPoliester(false);
                        opUpdated = true;
                        log.info("[LINK] OP {} removeu lock poliester após pedido {}=true", op.getNumeroOp(),
                                order.getId());
                    }
                } else {
                    if (Boolean.TRUE.equals(opPoliester) && !lockPoliester) {
                        order.setPoliester(true);
                        orderUpdated = true;
                        log.info("[LINK] Pedido {} recebeu poliester=true da OP {}", order.getId(), op.getNumeroOp());
                    } else if (lockPoliester && !Boolean.TRUE.equals(opPoliester)) {
                        op.setManualLockPoliester(false);
                        opUpdated = true;
                        log.info("[LINK] OP {} limpou lock poliester pois pedido {} segue false", op.getNumeroOp(),
                                order.getId());
                    }
                }

                Boolean opPapel = op.getPapelCalibrado();
                boolean lockPapel = op.isManualLockPapelCalibrado();
                if (order.isPapelCalibrado()) {
                    if (!Boolean.TRUE.equals(opPapel)) {
                        op.setPapelCalibrado(true);
                        opUpdated = true;
                        log.info("[LINK] OP {} recebeu papelCalibrado=true do pedido {}", op.getNumeroOp(),
                                order.getId());
                    }
                    if (lockPapel) {
                        op.setManualLockPapelCalibrado(false);
                        opUpdated = true;
                        log.info("[LINK] OP {} removeu lock papelCalibrado após pedido {}=true", op.getNumeroOp(),
                                order.getId());
                    }
                } else {
                    if (Boolean.TRUE.equals(opPapel) && !lockPapel) {
                        order.setPapelCalibrado(true);
                        orderUpdated = true;
                        log.info("[LINK] Pedido {} recebeu papelCalibrado=true da OP {}", order.getId(),
                                op.getNumeroOp());
                    } else if (lockPapel && !Boolean.TRUE.equals(opPapel)) {
                        op.setManualLockPapelCalibrado(false);
                        opUpdated = true;
                        log.info("[LINK] OP {} limpou lock papelCalibrado pois pedido {} segue false", op.getNumeroOp(),
                                order.getId());
                    }
                }

                Boolean opVaiVinco = op.getVaiVinco();
                boolean lockVaiVinco = op.isManualLockVaiVinco();
                if (order.isVaiVinco()) {
                    if (!Boolean.TRUE.equals(opVaiVinco)) {
                        op.setVaiVinco(true);
                        opUpdated = true;
                        log.info("[LINK] OP {} recebeu vaiVinco=true do pedido {}", op.getNumeroOp(), order.getId());
                    }
                    if (lockVaiVinco) {
                        op.setManualLockVaiVinco(false);
                        opUpdated = true;
                        log.info("[LINK] OP {} removeu lock vaiVinco após pedido {}=true", op.getNumeroOp(),
                                order.getId());
                    }
                } else {
                    if (Boolean.TRUE.equals(opVaiVinco) && !lockVaiVinco) {
                        order.setVaiVinco(true);
                        orderUpdated = true;
                        log.info("[LINK] Pedido {} recebeu vaiVinco=true da OP {}", order.getId(), op.getNumeroOp());
                    } else if (lockVaiVinco && !Boolean.TRUE.equals(opVaiVinco)) {
                        op.setManualLockVaiVinco(false);
                        opUpdated = true;
                        log.info("[LINK] OP {} limpou lock vaiVinco pois pedido {} segue false", op.getNumeroOp(),
                                order.getId());
                    }
                }

                if (isBlank(order.getModalidadeEntrega())) {
                    order.setModalidadeEntrega("A ENTREGAR");
                    orderUpdated = true;
                }

                if (opUpdated) {
                    repo.save(op);
                }
                if (orderUpdated) {
                    orderRepo.save(order);
                    notifyOrder(order);
                }
            });
        });
    }

    @Transactional
    public void applyManualLocksForOrder(
            Order order,
            boolean previousEmborrachada,
            boolean previousPertinax,
            boolean previousPoliester,
            boolean previousPapelCalibrado,
            boolean previousVaiVinco) {

        if (order == null || order.getNr() == null || order.getNr().isBlank())
            return;

        repo.findByNumeroOp(order.getNr()).ifPresent(op -> {
            boolean dirty = false;

            if (previousEmborrachada != order.isEmborrachada()) {
                if (!order.isEmborrachada() && op.isEmborrachada()) {
                    if (!op.isManualLockEmborrachada()) {
                        op.setManualLockEmborrachada(true);
                        dirty = true;
                        log.info("[ORDER] Pedido {} marcou lock emborrachada=false na OP {}", order.getId(),
                                op.getNumeroOp());
                    }
                } else if (order.isEmborrachada() && op.isManualLockEmborrachada()) {
                    op.setManualLockEmborrachada(false);
                    dirty = true;
                    log.info("[ORDER] Pedido {} removeu lock emborrachada na OP {}", order.getId(), op.getNumeroOp());
                }
            }

            if (previousPertinax != order.isPertinax()) {
                if (!order.isPertinax() && Boolean.TRUE.equals(op.getPertinax())) {
                    if (!op.isManualLockPertinax()) {
                        op.setManualLockPertinax(true);
                        dirty = true;
                        log.info("[ORDER] Pedido {} marcou lock pertinax=false na OP {}", order.getId(),
                                op.getNumeroOp());
                    }
                } else if (order.isPertinax() && op.isManualLockPertinax()) {
                    op.setManualLockPertinax(false);
                    dirty = true;
                    log.info("[ORDER] Pedido {} removeu lock pertinax na OP {}", order.getId(), op.getNumeroOp());
                }
            }

            if (previousPoliester != order.isPoliester()) {
                if (!order.isPoliester() && Boolean.TRUE.equals(op.getPoliester())) {
                    if (!op.isManualLockPoliester()) {
                        op.setManualLockPoliester(true);
                        dirty = true;
                        log.info("[ORDER] Pedido {} marcou lock poliester=false na OP {}", order.getId(),
                                op.getNumeroOp());
                    }
                } else if (order.isPoliester() && op.isManualLockPoliester()) {
                    op.setManualLockPoliester(false);
                    dirty = true;
                    log.info("[ORDER] Pedido {} removeu lock poliester na OP {}", order.getId(), op.getNumeroOp());
                }
            }

            if (previousPapelCalibrado != order.isPapelCalibrado()) {
                if (!order.isPapelCalibrado() && Boolean.TRUE.equals(op.getPapelCalibrado())) {
                    if (!op.isManualLockPapelCalibrado()) {
                        op.setManualLockPapelCalibrado(true);
                        dirty = true;
                        log.info("[ORDER] Pedido {} marcou lock papelCalibrado=false na OP {}", order.getId(),
                                op.getNumeroOp());
                    }
                } else if (order.isPapelCalibrado() && op.isManualLockPapelCalibrado()) {
                    op.setManualLockPapelCalibrado(false);
                    dirty = true;
                    log.info("[ORDER] Pedido {} removeu lock papelCalibrado na OP {}", order.getId(), op.getNumeroOp());
                }
            }

            if (previousVaiVinco != order.isVaiVinco()) {
                if (!order.isVaiVinco() && Boolean.TRUE.equals(op.getVaiVinco())) {
                    if (!op.isManualLockVaiVinco()) {
                        op.setManualLockVaiVinco(true);
                        dirty = true;
                        log.info("[ORDER] Pedido {} marcou lock vaiVinco=false na OP {}", order.getId(),
                                op.getNumeroOp());
                    }
                } else if (order.isVaiVinco() && op.isManualLockVaiVinco()) {
                    op.setManualLockVaiVinco(false);
                    dirty = true;
                    log.info("[ORDER] Pedido {} removeu lock vaiVinco na OP {}", order.getId(), op.getNumeroOp());
                }
            }

            if (dirty) {
                repo.save(op);
            }
        });
    }

    // ---------------------------
    // Reconciliação periódica: liga OPs a faca
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
                    if (dirty)
                        repo.save(o);

                    boolean orderUpdated = false;
                    boolean opUpdated = false;
                    boolean linked = o.getFacaId() != null && o.getFacaId().equals(f.getId());

                    // Sincroniza Cliente/Transportadora/Endereco
                    if (o.getClienteRef() != null) {
                        if (f.getClienteRef() == null) {
                            f.setClienteRef(o.getClienteRef());
                            orderUpdated = true;
                        }
                        if (o.getClienteRef().getTransportadora() != null && f.getTransportadora() == null) {
                            f.setTransportadora(o.getClienteRef().getTransportadora());
                            orderUpdated = true;
                        }
                    }
                    if (o.getEndereco() != null && f.getEndereco() == null) {
                        f.setEndereco(o.getEndereco());
                        orderUpdated = true;
                    }

                    boolean lockEmborrachada = o.isManualLockEmborrachada();
                    boolean lockPertinax = o.isManualLockPertinax();
                    boolean lockPoliester = o.isManualLockPoliester();
                    boolean lockPapel = o.isManualLockPapelCalibrado();
                    boolean lockVaiVinco = o.isManualLockVaiVinco();

                    if (!linked && o.isEmborrachada() && !f.isEmborrachada()) {
                        f.setEmborrachada(true);
                        orderUpdated = true;
                        log.info("[RECONCILE] Pedido {} sincronizado para emborrachada=true via OP {}", f.getId(),
                                o.getNumeroOp());
                    } else {
                        if (f.isEmborrachada()) {
                            if (!o.isEmborrachada()) {
                                o.setEmborrachada(true);
                                opUpdated = true;
                                log.info("[RECONCILE] OP {} recebeu emborrachada=true do pedido {}", o.getNumeroOp(),
                                        f.getId());
                            }
                            if (lockEmborrachada) {
                                o.setManualLockEmborrachada(false);
                                opUpdated = true;
                                log.info("[RECONCILE] OP {} removeu lock emborrachada após pedido {}=true",
                                        o.getNumeroOp(), f.getId());
                            }
                        } else if (o.isEmborrachada() && !lockEmborrachada) {
                            f.setEmborrachada(true);
                            orderUpdated = true;
                            log.info("[RECONCILE] Pedido {} recebeu emborrachada=true da OP {}", f.getId(),
                                    o.getNumeroOp());
                        }
                    }

                    if (!isBlank(f.getDestacador())) {
                        if (isBlank(o.getDestacador()) || !Objects.equals(f.getDestacador(), o.getDestacador())) {
                            o.setDestacador(f.getDestacador());
                            opUpdated = true;
                        }
                    } else if (!isBlank(o.getDestacador())) {
                        f.setDestacador(o.getDestacador());
                        orderUpdated = true;
                    }

                    if (!isBlank(f.getModalidadeEntrega())) {
                        if (isBlank(o.getModalidadeEntrega())
                                || !Objects.equals(f.getModalidadeEntrega(), o.getModalidadeEntrega())) {
                            o.setModalidadeEntrega(f.getModalidadeEntrega());
                            opUpdated = true;
                        }
                    } else if (!isBlank(o.getModalidadeEntrega())) {
                        f.setModalidadeEntrega(o.getModalidadeEntrega());
                        orderUpdated = true;
                    }

                    if (f.getDataRequeridaEntrega() != null) {
                        if (!equalsInstant(f.getDataRequeridaEntrega(), o.getDataRequeridaEntrega())) {
                            o.setDataRequeridaEntrega(f.getDataRequeridaEntrega());
                            opUpdated = true;
                        }
                    } else if (o.getDataRequeridaEntrega() != null) {
                        f.setDataRequeridaEntrega(o.getDataRequeridaEntrega());
                        orderUpdated = true;
                    }

                    if (!isBlank(f.getUsuarioImportacao())) {
                        if (isBlank(o.getUsuarioImportacao())
                                || !Objects.equals(f.getUsuarioImportacao(), o.getUsuarioImportacao())) {
                            o.setUsuarioImportacao(f.getUsuarioImportacao());
                            opUpdated = true;
                        }
                    } else if (!isBlank(o.getUsuarioImportacao())) {
                        f.setUsuarioImportacao(o.getUsuarioImportacao());
                        orderUpdated = true;
                    }

                    if (f.isPertinax()) {
                        if (!Boolean.TRUE.equals(o.getPertinax())) {
                            o.setPertinax(true);
                            opUpdated = true;
                            log.info("[RECONCILE] OP {} recebeu pertinax=true do pedido {}", o.getNumeroOp(),
                                    f.getId());
                        }
                        if (lockPertinax) {
                            o.setManualLockPertinax(false);
                            opUpdated = true;
                            log.info("[RECONCILE] OP {} removeu lock pertinax após pedido {}=true", o.getNumeroOp(),
                                    f.getId());
                        }
                    } else {
                        if (Boolean.TRUE.equals(o.getPertinax()) && !lockPertinax) {
                            f.setPertinax(true);
                            orderUpdated = true;
                            log.info("[RECONCILE] Pedido {} recebeu pertinax=true da OP {}", f.getId(),
                                    o.getNumeroOp());
                        } else if (lockPertinax && !Boolean.TRUE.equals(o.getPertinax())) {
                            o.setManualLockPertinax(false);
                            opUpdated = true;
                            log.info("[RECONCILE] OP {} limpou lock pertinax pois pedido {} permanece false",
                                    o.getNumeroOp(), f.getId());
                        }
                    }

                    if (f.isPoliester()) {
                        if (!Boolean.TRUE.equals(o.getPoliester())) {
                            o.setPoliester(true);
                            opUpdated = true;
                            log.info("[RECONCILE] OP {} recebeu poliester=true do pedido {}", o.getNumeroOp(),
                                    f.getId());
                        }
                        if (lockPoliester) {
                            o.setManualLockPoliester(false);
                            opUpdated = true;
                            log.info("[RECONCILE] OP {} removeu lock poliester após pedido {}=true", o.getNumeroOp(),
                                    f.getId());
                        }
                    } else {
                        if (Boolean.TRUE.equals(o.getPoliester()) && !lockPoliester) {
                            f.setPoliester(true);
                            orderUpdated = true;
                            log.info("[RECONCILE] Pedido {} recebeu poliester=true da OP {}", f.getId(),
                                    o.getNumeroOp());
                        } else if (lockPoliester && !Boolean.TRUE.equals(o.getPoliester())) {
                            o.setManualLockPoliester(false);
                            opUpdated = true;
                            log.info("[RECONCILE] OP {} limpou lock poliester pois pedido {} permanece false",
                                    o.getNumeroOp(), f.getId());
                        }
                    }

                    if (f.isPapelCalibrado()) {
                        if (!Boolean.TRUE.equals(o.getPapelCalibrado())) {
                            o.setPapelCalibrado(true);
                            opUpdated = true;
                            log.info("[RECONCILE] OP {} recebeu papelCalibrado=true do pedido {}", o.getNumeroOp(),
                                    f.getId());
                        }
                        if (lockPapel) {
                            o.setManualLockPapelCalibrado(false);
                            opUpdated = true;
                            log.info("[RECONCILE] OP {} removeu lock papelCalibrado após pedido {}=true",
                                    o.getNumeroOp(), f.getId());
                        }
                    } else {
                        if (Boolean.TRUE.equals(o.getPapelCalibrado()) && !lockPapel) {
                            f.setPapelCalibrado(true);
                            orderUpdated = true;
                            log.info("[RECONCILE] Pedido {} recebeu papelCalibrado=true da OP {}", f.getId(),
                                    o.getNumeroOp());
                        } else if (lockPapel && !Boolean.TRUE.equals(o.getPapelCalibrado())) {
                            o.setManualLockPapelCalibrado(false);
                            opUpdated = true;
                            log.info("[RECONCILE] OP {} limpou lock papelCalibrado pois pedido {} permanece false",
                                    o.getNumeroOp(), f.getId());
                        }
                    }

                    Boolean opVaiVinco = o.getVaiVinco();
                    if (f.isVaiVinco()) {
                        if (!Boolean.TRUE.equals(opVaiVinco)) {
                            o.setVaiVinco(true);
                            opUpdated = true;
                            log.info("[RECONCILE] OP {} recebeu vaiVinco=true do pedido {}", o.getNumeroOp(),
                                    f.getId());
                        }
                        if (lockVaiVinco) {
                            o.setManualLockVaiVinco(false);
                            opUpdated = true;
                            log.info("[RECONCILE] OP {} removeu lock vaiVinco após pedido {}=true", o.getNumeroOp(),
                                    f.getId());
                        }
                    } else {
                        if (Boolean.TRUE.equals(opVaiVinco) && !lockVaiVinco) {
                            f.setVaiVinco(true);
                            orderUpdated = true;
                            log.info("[RECONCILE] Pedido {} recebeu vaiVinco=true da OP {}", f.getId(),
                                    o.getNumeroOp());
                        } else if (lockVaiVinco && !Boolean.TRUE.equals(opVaiVinco)) {
                            o.setManualLockVaiVinco(false);
                            opUpdated = true;
                            log.info("[RECONCILE] OP {} limpou lock vaiVinco pois pedido {} permanece false",
                                    o.getNumeroOp(), f.getId());
                        }
                    }

                    if (isBlank(f.getModalidadeEntrega())) {
                        f.setModalidadeEntrega("A ENTREGAR");
                        orderUpdated = true;
                    }

                    if (opUpdated) {
                        repo.save(o);
                    }
                    if (orderUpdated) {
                        orderRepo.save(f);
                        notifyOrder(f); // idempotente
                    }
                }));
        System.out.println("[ReconcileOps] Reconciliação periódica de OPs a faca concluída.");
    }
}
