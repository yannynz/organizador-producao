package git.yannynz.organizadorproducao.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import git.yannynz.organizadorproducao.config.DXFAnalysisProperties;
import git.yannynz.organizadorproducao.model.DXFAnalysis;
import git.yannynz.organizadorproducao.model.Order;
import git.yannynz.organizadorproducao.model.dto.DXFAnalysisView;
import git.yannynz.organizadorproducao.repository.DXFAnalysisRepository;
import git.yannynz.organizadorproducao.repository.OrderRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DXFAnalysisServiceTest {

    @Mock
    private DXFAnalysisRepository analysisRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private DXFAnalysisProperties properties;
    private SimpleMeterRegistry meterRegistry;

    private DXFAnalysisService service;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        properties = new DXFAnalysisProperties();
        properties.setWebsocketTopic("/topic/dxf-analysis");
        if (meterRegistry != null) {
            meterRegistry.close();
        }
        meterRegistry = new SimpleMeterRegistry();
        service = new DXFAnalysisService(analysisRepository, orderRepository, messagingTemplate, meterRegistry, properties);
    }

    @Test
    void persistFromPayload_shouldMapAllMainFields() throws Exception {
        String json = """
                {
                  "analysisId": "analysis-123",
                  "timestampUtc": "2025-10-13T10:15:30Z",
                  "opId": "NR123456",
                  "fileName": "NR123456 Cliente_VERDE.DXF",
                  "fileHash": "abc123",
                  "metrics": {
                    "totalCutLength": 2500.75,
                    "numCurves": 10,
                    "numIntersections": 3,
                    "minArcRadius": 0.5
                  },
                  "score": 4.0,
                  "explanations": ["cut_length"],
                  "image": {
                    "path": "render/analysis-123.png",
                    "width": 1024,
                    "height": 768
                  },
                  "flags": {
                    "orderNumber": "123456"
                  },
                  "cacheHit": false
                }
                """;

        JsonNode payload = objectMapper.readTree(json);

        Order order = new Order();
        order.setId(42L);
        order.setNr("123456");

        when(orderRepository.findTopByNrOrderByIdDesc("123456"))
                .thenReturn(Optional.of(order));
        when(analysisRepository.findByAnalysisId("analysis-123"))
                .thenReturn(Optional.empty());
        when(analysisRepository.save(any(DXFAnalysis.class)))
                .thenAnswer(invocation -> {
                    DXFAnalysis entity = invocation.getArgument(0);
                    entity.setId(99L);
                    return entity;
                });

        DXFAnalysis result = service.persistFromPayload(payload);

        assertThat(result.getAnalysisId()).isEqualTo("analysis-123");
        assertThat(result.getOrderNr()).isEqualTo("123456");
        assertThat(result.getOrder()).isSameAs(order);
        assertThat(result.getScore()).isEqualTo(4.0);
        assertThat(result.getScoreLabel()).isNull();
        assertThat(result.getScoreStars()).isEqualTo(4.0);
        assertThat(result.getTotalCutLengthMm()).isEqualTo(2500.75);
        assertThat(result.getCurveCount()).isEqualTo(10);
        assertThat(result.getIntersectionCount()).isEqualTo(3);
        assertThat(result.getMinRadiusMm()).isEqualTo(0.5);
        assertThat(result.getFileHash()).isEqualTo("abc123");
        assertThat(result.getImagePath()).isEqualTo("render/analysis-123.png");
        assertThat(result.getAnalyzedAt()).isEqualTo(OffsetDateTime.parse("2025-10-13T10:15:30Z"));
        assertThat(result.isCacheHit()).isFalse();
        assertThat(result.getMetrics()).isNotNull();
        assertThat(result.getExplanations()).isNotNull();

        ArgumentCaptor<DXFAnalysisView> viewCaptor = ArgumentCaptor.forClass(DXFAnalysisView.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/dxf-analysis"), viewCaptor.capture());
        DXFAnalysisView view = viewCaptor.getValue();
        assertThat(view.analysisId()).isEqualTo("analysis-123");
        assertThat(view.orderNr()).isEqualTo("123456");
        assertThat(view.score()).isEqualTo(4.0);
        assertThat(view.scoreStars()).isEqualTo(4.0);

        var successCounter = meterRegistry.find("organizador_dxf_analysis_total").tag("status", "success").counter();
        assertThat(successCounter).isNotNull();
        assertThat(successCounter.count()).isEqualTo(1.0);
        var failedCounter = meterRegistry.find("organizador_dxf_analysis_failed_total").counter();
        assertThat(failedCounter).isNotNull();
        assertThat(failedCounter.count()).isEqualTo(0.0);
    }

    @Test
    void persistFromPayload_shouldExtractOrderFromFileName() throws Exception {
        String json = """
                {
                  "analysisId": "analysis-999",
                  "timestamp": "2025-10-14T12:00:00Z",
                  "fileName": "NR 654321 MACHO_VERMELHO.DXF",
                  "metrics": {
                    "total_cut_length_mm": "1800.0"
                  },
                  "score": {
                    "score": "2.5"
                  }
                }
                """;

        JsonNode payload = objectMapper.readTree(json);

        when(analysisRepository.findByAnalysisId("analysis-999"))
                .thenReturn(Optional.empty());
        when(analysisRepository.save(any(DXFAnalysis.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DXFAnalysis result = service.persistFromPayload(payload);

        assertThat(result.getOrderNr()).isEqualTo("654321");
        assertThat(result.getOrder()).isNull();
        assertThat(result.getTotalCutLengthMm()).isEqualTo(1800.0);
        assertThat(result.getScore()).isEqualTo(2.5);
        assertThat(result.getScoreStars()).isEqualTo(2.5);
        verify(messagingTemplate).convertAndSend(eq("/topic/dxf-analysis"), any(DXFAnalysisView.class));
    }

    @Test
    void persistFromPayload_shouldPersistStorageMetadataAndMetrics() throws Exception {
        String json = """
                {
                  "analysisId": "analysis-storage",
                  "timestampUtc": "2025-10-15T12:00:00Z",
                  "file": {
                    "name": "NR321654_CLIENTE.DXF",
                    "hash": "sha256:deadbeef"
                  },
                  "score": 4.7,
                  "metrics": {
                    "totalCutLengthMm": 1500.0
                  },
                  "image": {
                    "path": "render/analysis-storage.png",
                    "width": 800,
                    "height": 600,
                    "storageBucket": "facas-renders",
                    "storageKey": "renders/sha/analysis-storage.png",
                    "storageUri": "http://cdn.example.com/renders/sha/analysis-storage.png",
                    "checksum": "sha256:0011",
                    "sizeBytes": 4096,
                    "contentType": "image/png",
                    "uploadStatus": "uploaded",
                    "uploadMessage": "ok",
                    "uploadedAtUtc": "2025-10-15T12:00:05Z",
                    "etag": "etag123"
                  }
                }
                """;

        JsonNode payload = objectMapper.readTree(json);

        when(analysisRepository.findByAnalysisId("analysis-storage"))
                .thenReturn(Optional.empty());
        when(analysisRepository.save(any(DXFAnalysis.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepository.findTopByNrOrderByIdDesc("321654"))
                .thenReturn(Optional.empty());
        when(orderRepository.findByNr("321654"))
                .thenReturn(Optional.empty());

        DXFAnalysis result = service.persistFromPayload(payload);

        assertThat(result.getImageBucket()).isEqualTo("facas-renders");
        assertThat(result.getImageKey()).isEqualTo("renders/sha/analysis-storage.png");
        assertThat(result.getImageUri()).isEqualTo("http://cdn.example.com/renders/sha/analysis-storage.png");
        assertThat(result.getImageChecksum()).isEqualTo("sha256:0011");
        assertThat(result.getImageSizeBytes()).isEqualTo(4096L);
        assertThat(result.getImageContentType()).isEqualTo("image/png");
        assertThat(result.getImageUploadStatus()).isEqualTo("uploaded");
        assertThat(result.getImageUploadMessage()).isEqualTo("ok");
        assertThat(result.getImageUploadedAt()).isEqualTo(OffsetDateTime.parse("2025-10-15T12:00:05Z"));
        assertThat(result.getImageEtag()).isEqualTo("etag123");
        assertThat(result.getScoreStars()).isEqualTo(4.5);

        DXFAnalysisView view = service.toView(result);
        assertThat(view.imageUrl()).isEqualTo("http://cdn.example.com/renders/sha/analysis-storage.png");
        assertThat(view.imageBucket()).isEqualTo("facas-renders");

        var uploadCounter = meterRegistry.find("organizador_dxf_analysis_upload_total")
                .tag("uploadStatus", "uploaded")
                .counter();
        assertThat(uploadCounter).isNotNull();
        assertThat(uploadCounter.count()).isEqualTo(1.0);

        var sizeSummary = meterRegistry.find("organizador_dxf_image_size_bytes").summary();
        assertThat(sizeSummary).isNotNull();
        assertThat(sizeSummary.count()).isEqualTo(1);
        assertThat(sizeSummary.totalAmount()).isEqualTo(4096.0);
    }
}
