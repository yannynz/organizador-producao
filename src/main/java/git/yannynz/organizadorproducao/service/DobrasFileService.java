package git.yannynz.organizadorproducao.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import git.yannynz.organizadorproducao.model.Order;
import git.yannynz.organizadorproducao.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serviço dedicado a processar eventos de "Dobras" vindos do RabbitMQ.
 * Espera mensagens JSON com, no mínimo, a propriedade "file_name".
 * Ex.: {"file_name":"NR 123456.m.DXF", "path":"...", "timestamp":...}
 *
 * Regras:
 *  - Aceita apenas: NR<espaco opcional><numero>.(m.DXF|DXF.FCD), case-insensitive
 *  - Extrai o NR e atualiza o pedido para status = 6 ("Tirada"), se necessário
 *  - Publica a atualização via WebSocket em /topic/orders
 */
@Service
public class DobrasFileService {

    public static final String QUEUE_NAME = "dobra_notifications";
    public static final int STATUS_TIRADA = 6;

    private static final Logger log = LoggerFactory.getLogger(DobrasFileService.class);

    // ^NR\s*(\d+)\.(M\.DXF|DXF\.FCD)$ (case-insensitive, unicode-aware)
    private static final Pattern DOBRAS_PATTERN = Pattern.compile(
            "^NR\\s*(\\d+)\\.(?:M\\.DXF|DXF\\.FCD)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private final ObjectMapper objectMapper;
    private final OrderRepository orderRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public DobrasFileService(ObjectMapper objectMapper,
                             OrderRepository orderRepository,
                             SimpMessagingTemplate messagingTemplate) {
        this.objectMapper = objectMapper;
        this.orderRepository = orderRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @RabbitListener(queues = QUEUE_NAME)
    public void handleDobrasQueue(String message) {
        try {
            if (message == null || message.isBlank()) {
                log.warn("[DOBRAS] Mensagem vazia ou nula recebida na fila {}", QUEUE_NAME);
                return;
            }

            JsonNode json = objectMapper.readTree(message);
            String fileName = json.path("file_name").asText(null);

            if (fileName == null || fileName.isBlank()) {
                log.warn("[DOBRAS] Campo 'file_name' ausente/blank na mensagem: {}", message);
                return;
            }

            Optional<String> nrOpt = extractOrderNumber(fileName);
            if (nrOpt.isEmpty()) {
                // Não é do padrão de fim de dobra -> ignorar silenciosamente
                log.debug("[DOBRAS] Arquivo fora do padrão de dobras, ignorado: {}", fileName);
                return;
            }

            String orderNumber = nrOpt.get();
            updateOrderStatusToTirada(orderNumber);

        } catch (Exception e) {
            log.error("[DOBRAS] Erro ao processar mensagem na fila {}: {}", QUEUE_NAME, e.getMessage(), e);
        }
    }

    /**
     * Extrai o NR do nome do arquivo, se casar com o padrão esperado.
     */
    private Optional<String> extractOrderNumber(String fileName) {
        Matcher m = DOBRAS_PATTERN.matcher(fileName.trim());
        if (m.matches()) {
            return Optional.of(m.group(1)); // grupo 1 = número do pedido
        }
        return Optional.empty();
    }

    /**
     * Atualiza o status do pedido para "Tirada" (6), se necessário, e notifica via WebSocket.
     * A operação é idempotente (só salva se houver mudança).
     */
    @Transactional
    protected void updateOrderStatusToTirada(String orderNumber) {
        orderRepository.findByNr(orderNumber).ifPresentOrElse(order -> {
            int current = order.getStatus();
            if (current != STATUS_TIRADA) {
                order.setStatus(STATUS_TIRADA);
                orderRepository.save(order);
                messagingTemplate.convertAndSend("/topic/orders", order);
                log.info("[DOBRAS] Status do pedido {} atualizado de {} para {}", orderNumber, current, STATUS_TIRADA);
            } else {
                log.debug("[DOBRAS] Pedido {} já está no status {}", orderNumber, STATUS_TIRADA);
            }
        }, () -> log.warn("[DOBRAS] Pedido não encontrado (NR={})", orderNumber));
    }
}

