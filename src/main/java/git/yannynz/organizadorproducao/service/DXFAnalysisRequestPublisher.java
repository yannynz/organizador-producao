package git.yannynz.organizadorproducao.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import git.yannynz.organizadorproducao.config.DXFAnalysisProperties;
import git.yannynz.organizadorproducao.model.dto.DXFAnalysisRequestDTO;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DXFAnalysisRequestPublisher {

    private static final Logger log = LoggerFactory.getLogger(DXFAnalysisRequestPublisher.class);
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final String APP_NAME = "organizador-producao";

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final DXFAnalysisProperties properties;
    private final Pattern orderPattern;

    public DXFAnalysisRequestPublisher(RabbitTemplate rabbitTemplate,
                                       ObjectMapper objectMapper,
                                       DXFAnalysisProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.orderPattern = Pattern.compile(properties.getOrderNumberPattern());
    }

    public String publish(DXFAnalysisRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("DXF analysis request body cannot be null");
        }

        if (!StringUtils.hasText(request.filePath())) {
            throw new IllegalArgumentException("DXF analysis request requires 'filePath'");
        }

        String analysisId = UUID.randomUUID().toString();
        String normalizedFileName = resolveFileName(request);
        String orderNumber = resolveOrderNumber(request.orderNumber(), normalizedFileName);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("analysisId", analysisId);
        payload.put("filePath", request.filePath());
        payload.put("fileName", normalizedFileName);
        if (StringUtils.hasText(request.fileHash())) {
            payload.put("fileHash", request.fileHash());
        }
        if (StringUtils.hasText(orderNumber)) {
            payload.put("opId", orderNumber);
        }
        payload.put("requestedAt", ISO_FORMATTER.format(OffsetDateTime.now()));
        payload.put("requestedBy", APP_NAME);
        payload.put("forceReprocess", Boolean.TRUE.equals(request.forceReprocess()));
        payload.put("shadowMode", Boolean.TRUE.equals(request.shadowMode()));
        payload.put("source", Map.of(
                "app", APP_NAME,
                "version", getVersion()
        ));

        Map<String, Object> flags = new LinkedHashMap<>();
        flags.put("requestId", analysisId);
        if (StringUtils.hasText(orderNumber)) {
            flags.put("orderNumber", orderNumber);
        }
        if (!flags.isEmpty()) {
            payload.put("flags", flags);
        }

        try {
            // Envia o mapa diretamente para o Jackson2JsonMessageConverter
            rabbitTemplate.convertAndSend("", properties.getRequestQueue(), payload);
            log.info("DXF analysis request {} enviado para {}", analysisId, properties.getRequestQueue());
            return analysisId;
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao publicar pedido de análise DXF", e);
        }
    }

    private String resolveFileName(DXFAnalysisRequestDTO request) {
        if (StringUtils.hasText(request.fileName())) {
            return request.fileName();
        }
        String filePath = request.filePath();
        int lastSeparator = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        return lastSeparator >= 0 ? filePath.substring(lastSeparator + 1) : filePath;
    }

    private String resolveOrderNumber(String provided, String fileName) {
        if (StringUtils.hasText(provided)) {
            return provided.trim();
        }
        if (!StringUtils.hasText(fileName)) {
            return null;
        }
        Matcher matcher = orderPattern.matcher(fileName);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String getVersion() {
        String version = DXFAnalysisRequestPublisher.class.getPackage().getImplementationVersion();
        return version != null ? version : "dev";
    }
}
