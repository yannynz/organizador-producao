package git.yannynz.organizadorproducao.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import git.yannynz.organizadorproducao.config.DXFAnalysisProperties;
import git.yannynz.organizadorproducao.model.dto.DXFAnalysisRequestDTO;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DXFAnalysisRequestPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private DXFAnalysisRequestPublisher publisher;

    private DXFAnalysisProperties properties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        properties = new DXFAnalysisProperties();
        properties.setRequestQueue("facas.analysis.request");
        publisher = new DXFAnalysisRequestPublisher(rabbitTemplate, objectMapper, properties);
    }

    @Test
    void publish_shouldSendMessageWithDerivedFields() throws Exception {
        DXFAnalysisRequestDTO dto = new DXFAnalysisRequestDTO(
                "//srv/dxf/NR777777_CLIENTE.DXF",
                null,
                "hash-abc",
                null,
                Boolean.TRUE,
                null
        );

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

        String analysisId = publisher.publish(dto);

        assertThat(analysisId).isNotBlank();

        verify(rabbitTemplate).convertAndSend(eq(""), eq(properties.getRequestQueue()), payloadCaptor.capture());
        String payloadJson = payloadCaptor.getValue();

        Map<?, ?> payload = objectMapper.readValue(payloadJson, Map.class);
        assertThat(payload.get("analysisId")).isEqualTo(analysisId);
        assertThat(payload.get("fileName")).isEqualTo("NR777777_CLIENTE.DXF");
        assertThat(payload.get("opId")).isEqualTo("777777");
        assertThat(payload.get("fileHash")).isEqualTo("hash-abc");
        assertThat(payload.get("forceReprocess")).isEqualTo(true);
        assertThat(payload.get("shadowMode")).isEqualTo(false);

        Map<?, ?> flags = (Map<?, ?>) payload.get("flags");
        assertThat(flags).isNotNull();
        assertThat(flags.get("orderNumber")).isEqualTo("777777");
        assertThat(flags.get("requestId")).isEqualTo(analysisId);
    }

    @Test
    void publish_shouldRejectMissingFilePath() {
        DXFAnalysisRequestDTO dto = new DXFAnalysisRequestDTO(
                " ",
                null,
                null,
                null,
                null,
                null
        );

        assertThatThrownBy(() -> publisher.publish(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("filePath");
    }
}
