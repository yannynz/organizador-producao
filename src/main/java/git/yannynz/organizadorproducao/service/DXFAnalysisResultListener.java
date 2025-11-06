package git.yannynz.organizadorproducao.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import git.yannynz.organizadorproducao.config.DXFAnalysisProperties;
import git.yannynz.organizadorproducao.monitoring.MessageProcessingMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class DXFAnalysisResultListener {

    private static final Logger log = LoggerFactory.getLogger(DXFAnalysisResultListener.class);

    private final DXFAnalysisService analysisService;
    private final MessageProcessingMetrics messageProcessingMetrics;
    private final ObjectMapper objectMapper;
    private final DXFAnalysisProperties properties;

    public DXFAnalysisResultListener(DXFAnalysisService analysisService,
                                     MessageProcessingMetrics messageProcessingMetrics,
                                     ObjectMapper objectMapper,
                                     DXFAnalysisProperties properties) {
        this.analysisService = analysisService;
        this.messageProcessingMetrics = messageProcessingMetrics;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @RabbitListener(
            queues = "${app.dxf.analysis.result-queue:facas.analysis.result}",
            containerFactory = "stringListenerFactory"
    )
    public void consumeResult(String payload) {
        if (payload == null || payload.isBlank()) {
            log.debug("Ignorando mensagem vazia na fila de resultados de DXF.");
            return;
        }

        String queueName = properties.getResultQueue();
        try {
            messageProcessingMetrics.recordProcessing(queueName, () -> {
                JsonNode json = objectMapper.readTree(payload);
                analysisService.persistFromPayload(json);
            });
        } catch (Exception e) {
            log.error("Erro ao processar resultado de análise DXF: {}", e.getMessage(), e);
        }
    }
}
