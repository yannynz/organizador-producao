package git.yannynz.organizadorproducao.service;

import com.fasterxml.jackson.databind.JsonNode;
import git.yannynz.organizadorproducao.config.DXFAnalysisProperties;
import git.yannynz.organizadorproducao.model.DXFAnalysis;
import git.yannynz.organizadorproducao.model.Order;
import git.yannynz.organizadorproducao.model.dto.DXFAnalysisView;
import git.yannynz.organizadorproducao.repository.DXFAnalysisRepository;
import git.yannynz.organizadorproducao.repository.OrderRepository;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DXFAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(DXFAnalysisService.class);

    private final DXFAnalysisRepository analysisRepository;
    private final OrderRepository orderRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final DXFAnalysisProperties properties;
    private final Pattern orderPattern;
    private final MeterRegistry meterRegistry;
    private final Counter analysisTotalCounter;
    private final Counter analysisFailedCounter;
    private final DistributionSummary imageSizeSummary;
    private final Timer analysisTimer;

    public DXFAnalysisService(DXFAnalysisRepository analysisRepository,
                              OrderRepository orderRepository,
                              SimpMessagingTemplate messagingTemplate,
                              MeterRegistry meterRegistry,
                              DXFAnalysisProperties properties) {
        this.analysisRepository = analysisRepository;
        this.orderRepository = orderRepository;
        this.messagingTemplate = messagingTemplate;
        this.properties = properties;
        this.orderPattern = Pattern.compile(properties.getOrderNumberPattern());
        this.meterRegistry = meterRegistry;
        this.analysisTotalCounter = meterRegistry.counter("organizador_dxf_analysis_total", "status", "success");
        this.analysisFailedCounter = meterRegistry.counter("organizador_dxf_analysis_failed_total");
        this.imageSizeSummary = DistributionSummary.builder("organizador_dxf_image_size_bytes")
                .description("Tamanho das imagens DXF persistidas")
                .baseUnit("bytes")
                .register(meterRegistry);
        this.analysisTimer = Timer.builder("organizador_dxf_analysis_duration_seconds")
                .description("Tempo para processar resultados de análise DXF")
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    @Transactional
    public DXFAnalysis persistFromPayload(JsonNode payload) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            if (payload == null || payload.isNull() || payload.isMissingNode()) {
                throw new IllegalArgumentException("DXF analysis payload must not be null");
            }

            String analysisId = text(payload, "analysisId");
            if (analysisId == null || analysisId.isBlank()) {
                throw new IllegalArgumentException("DXF analysis payload is missing 'analysisId'");
            }

            DXFAnalysis entity = analysisRepository.findByAnalysisId(analysisId)
                    .orElseGet(DXFAnalysis::new);
            entity.setAnalysisId(analysisId);

            JsonNode fileNode = firstNonMissing(
                    payload.path("file"),
                    payload.path("dxf"),
                    payload.path("input")
            );
            entity.setFileName(textOrFallback(fileNode, "name", text(payload, "fileName")));
            entity.setFileHash(textOrFallback(fileNode, "hash", text(payload, "fileHash")));

            String orderNrCandidate = resolveOrderNumber(payload, entity.getFileName());
            entity.setOrderNr(orderNrCandidate);

            if (orderNrCandidate != null
                    && (entity.getOrder() == null || !orderNrCandidate.equalsIgnoreCase(entity.getOrder().getNr()))) {
                final String lookupOrderNr = orderNrCandidate;
                orderRepository.findTopByNrOrderByIdDesc(lookupOrderNr)
                        .or(() -> orderRepository.findByNr(lookupOrderNr))
                        .ifPresentOrElse(entity::setOrder, () -> entity.setOrder(null));
            }

            JsonNode scoringNode = payload.path("score");
            Double scoreValue = null;
            String scoreLabel = null;
            if (isConcrete(scoringNode)) {
                if (scoringNode.isNumber()) {
                    scoreValue = scoringNode.doubleValue();
                } else {
                    scoreValue = doubleOrNull(scoringNode, "value", "score");
                    scoreLabel = textOrFallback(scoringNode, "label", null);
                }
            }
            if (scoreValue == null) {
                scoreValue = doubleOrNull(payload, "score");
            }
            if (scoreLabel == null) {
                scoreLabel = textOrFallback(payload, "scoreLabel", null);
            }
            entity.setScore(scoreValue);
            entity.setScoreLabel(scoreLabel);
            entity.setScoreStars(scoreValue != null ? normalizeScoreStars(scoreValue) : null);

            JsonNode metricsNode = payload.path("metrics");
            if (isConcrete(metricsNode)) {
                entity.setMetrics(metricsNode);
                entity.setTotalCutLengthMm(doubleOrNull(metricsNode,
                        "totalCutLengthMm", "total_cut_length_mm", "totalLengthMm", "totalCutLength"));
                entity.setCurveCount(intOrNull(metricsNode, "curveCount", "curves", "numCurves"));
                entity.setIntersectionCount(intOrNull(metricsNode, "intersectionCount", "intersections", "numIntersections"));
                entity.setMinRadiusMm(doubleOrNull(metricsNode, "minRadiusMm", "min_radius_mm", "minArcRadius"));
            } else {
                entity.setMetrics(null);
                entity.setTotalCutLengthMm(null);
                entity.setCurveCount(null);
                entity.setIntersectionCount(null);
                entity.setMinRadiusMm(null);
            }

            JsonNode explanationsNode = firstNonMissing(
                    payload.path("explanations"),
                    scoringNode.path("explanations"));
            entity.setExplanations(normalizeExplanations(explanationsNode));

            JsonNode imageNode = payload.path("image");
            if (!isConcrete(imageNode)) {
                imageNode = payload.path("render");
            }
            entity.setImagePath(textOrFallback(imageNode, "path", null));
            entity.setImageWidth(intOrNull(imageNode, "width"));
            entity.setImageHeight(intOrNull(imageNode, "height"));
            entity.setImageBucket(textOrFallback(imageNode, "storageBucket", entity.getImageBucket()));
            entity.setImageKey(textOrFallback(imageNode, "storageKey", entity.getImageKey()));
            entity.setImageUri(textOrFallback(imageNode, "storageUri", entity.getImageUri()));
            entity.setImageChecksum(textOrFallback(imageNode, "checksum", entity.getImageChecksum()));
            final Long imageSize = longOrNull(imageNode, "sizeBytes");
            entity.setImageSizeBytes(imageSize != null ? imageSize : entity.getImageSizeBytes());
            entity.setImageContentType(textOrFallback(imageNode, "contentType", entity.getImageContentType()));
            entity.setImageUploadStatus(textOrFallback(imageNode, "uploadStatus", entity.getImageUploadStatus()));
            entity.setImageUploadMessage(textOrFallback(imageNode, "uploadMessage", entity.getImageUploadMessage()));
            JsonNode uploadedAtNode = imageNode.path("uploadedAtUtc");
            if (!isConcrete(uploadedAtNode)) {
                uploadedAtNode = imageNode.path("uploadedAt");
            }
            final OffsetDateTime uploadedAt = parseDate(uploadedAtNode).orElse(null);
            entity.setImageUploadedAt(uploadedAt != null ? uploadedAt : entity.getImageUploadedAt());
            entity.setImageEtag(textOrFallback(imageNode, "etag", entity.getImageEtag()));

            if ("error".equalsIgnoreCase(entity.getImageUploadStatus())
                    || "failed".equalsIgnoreCase(entity.getImageUploadStatus())) {
                log.warn("DXF image upload flagged as {} for analysis {}: {}",
                        entity.getImageUploadStatus(),
                        entity.getAnalysisId(),
                        entity.getImageUploadMessage());
            }

            entity.setCacheHit(payload.path("cacheHit").asBoolean(false));

            OffsetDateTime analyzedAt = resolveAnalysisTimestamp(payload);
            entity.setAnalyzedAt(analyzedAt != null ? analyzedAt : OffsetDateTime.now());

            if (payload.isContainerNode()) {
                entity.setRawPayload(payload.deepCopy());
            } else {
                entity.setRawPayload(payload);
            }

            DXFAnalysis saved = analysisRepository.save(entity);
            analysisTotalCounter.increment();
            if (entity.getImageSizeBytes() != null && entity.getImageSizeBytes() > 0) {
                imageSizeSummary.record(entity.getImageSizeBytes());
            }
            recordUploadStatusMetric(entity.getImageUploadStatus());
            broadcast(saved);
            return saved;
        } catch (RuntimeException ex) {
            analysisFailedCounter.increment();
            throw ex;
        } finally {
            sample.stop(analysisTimer);
        }
    }

    public Optional<DXFAnalysis> findLatestByOrderNr(String orderNr) {
        if (orderNr == null || orderNr.isBlank()) {
            return Optional.empty();
        }
        List<String> candidates = buildOrderCandidates(orderNr);
        DXFAnalysis latest = null;
        for (String candidate : candidates) {
            Optional<DXFAnalysis> found = analysisRepository.findTopByOrderNrOrderByAnalyzedAtDesc(candidate);
            if (found.isEmpty()) {
                continue;
            }
            DXFAnalysis current = found.get();
            if (latest == null || isAfter(current.getAnalyzedAt(), latest.getAnalyzedAt())) {
                latest = current;
            }
        }

        if (latest != null && hasImage(latest)) {
            return Optional.of(latest);
        }

        DXFAnalysis latestWithImage = null;
        for (String candidate : candidates) {
            List<DXFAnalysis> history = analysisRepository.findByOrderNrOrderByAnalyzedAtDesc(candidate, PageRequest.of(0, 10));
            for (DXFAnalysis item : history) {
                if (!hasImage(item)) {
                    continue;
                }
                if (latestWithImage == null || isAfter(item.getAnalyzedAt(), latestWithImage.getAnalyzedAt())) {
                    latestWithImage = item;
                }
                break;
            }
        }

        if (latestWithImage != null) {
            return Optional.of(latestWithImage);
        }

        return Optional.ofNullable(latest);
    }

    public Optional<DXFAnalysis> findByAnalysisId(String analysisId) {
        if (analysisId == null || analysisId.isBlank()) {
            return Optional.empty();
        }
        return analysisRepository.findByAnalysisId(analysisId);
    }

    public List<DXFAnalysisView> listRecentByOrder(String orderNr, int limit) {
        if (orderNr == null || orderNr.isBlank()) {
            return List.of();
        }
        int safeLimit = Math.max(1, Math.min(limit, 25));
        String normalized = normalizeOrderNumber(orderNr);
        List<DXFAnalysis> entities = analysisRepository.findByOrderNrOrderByAnalyzedAtDesc(
                normalized != null ? normalized : orderNr.trim(), PageRequest.of(0, safeLimit));
        if (entities.isEmpty() && normalized != null) {
            entities = analysisRepository.findByOrderNrOrderByAnalyzedAtDesc(orderNr.trim(), PageRequest.of(0, safeLimit));
        }
        if (entities.isEmpty() && normalized != null) {
            entities = analysisRepository.findByOrderNrOrderByAnalyzedAtDesc("NR" + normalized, PageRequest.of(0, safeLimit));
        }
        if (entities.isEmpty() && normalized != null) {
            entities = analysisRepository.findByOrderNrOrderByAnalyzedAtDesc("CL" + normalized, PageRequest.of(0, safeLimit));
        }
        return entities.stream().map(this::toView).toList();
    }

    public DXFAnalysisView toView(DXFAnalysis analysis) {
        if (analysis == null) {
            return null;
        }
        String imageUrl = resolveImageUrl(analysis);
        Long orderId = analysis.getOrder() != null ? analysis.getOrder().getId() : null;
        return new DXFAnalysisView(
                analysis.getAnalysisId(),
                analysis.getOrderNr(),
                orderId,
                analysis.getScore(),
                analysis.getScoreLabel(),
                analysis.getScoreStars(),
                analysis.getTotalCutLengthMm(),
                analysis.getCurveCount(),
                analysis.getIntersectionCount(),
                analysis.getMinRadiusMm(),
                analysis.isCacheHit(),
                analysis.getAnalyzedAt(),
                analysis.getFileName(),
                analysis.getFileHash(),
                analysis.getImagePath(),
                imageUrl,
                analysis.getImageBucket(),
                analysis.getImageKey(),
                analysis.getImageUri(),
                analysis.getImageChecksum(),
                analysis.getImageSizeBytes(),
                analysis.getImageContentType(),
                analysis.getImageUploadStatus(),
                analysis.getImageUploadMessage(),
                analysis.getImageUploadedAt(),
                analysis.getImageEtag(),
                analysis.getImageWidth(),
                analysis.getImageHeight(),
                analysis.getMetrics(),
                analysis.getExplanations()
        );
    }

    public ResponseEntity<?> loadAnalysisImage(String analysisId) {
        DXFAnalysis analysis = analysisRepository.findByAnalysisId(analysisId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Análise DXF não encontrada"));

        String directUrl = resolveImageUrl(analysis);
        if (isHttpUrl(directUrl)) {
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(directUrl)).build();
        }

        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Imagem DXF não disponível no storage configurado");
    }

    private void broadcast(DXFAnalysis analysis) {
        String topic = properties.getWebsocketTopic();
        if (topic == null || topic.isBlank()) {
            return;
        }
        try {
            messagingTemplate.convertAndSend(topic, toView(analysis));
        } catch (Exception e) {
            log.warn("Failed to broadcast DXF analysis {} to WebSocket topic {}: {}", analysis.getAnalysisId(), topic, e.getMessage());
        }
    }

    private OffsetDateTime resolveAnalysisTimestamp(JsonNode payload) {
        return parseDate(payload.path("timestampUtc"))
                .or(() -> parseDate(payload.path("analysisTimestamp")))
                .or(() -> parseDate(payload.path("timestamp")))
                .or(() -> parseDate(payload.path("analyzedAt")))
                .or(() -> parseDate(payload.path("analysis").path("timestamp")))
                .orElse(null);
    }

    private Optional<OffsetDateTime> parseDate(JsonNode node) {
        if (!isConcrete(node)) {
            return Optional.empty();
        }
        if (node.isNumber()) {
            long epochMillis = node.asLong();
            return Optional.of(OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMillis), java.time.ZoneOffset.UTC));
        }
        if (node.isTextual()) {
            try {
                return Optional.of(OffsetDateTime.parse(node.asText()));
            } catch (DateTimeParseException ex) {
                try {
                    return Optional.of(OffsetDateTime.parse(node.asText().toUpperCase(Locale.ROOT)));
                } catch (DateTimeParseException ignored) {
                    log.debug("Unable to parse date '{}': {}", node.asText(), ex.getMessage());
                }
            }
        }
        return Optional.empty();
    }

    private String resolveOrderNumber(JsonNode payload, String fileName) {
        String candidate = text(payload, "opId");
        if (candidate == null) {
            candidate = text(payload.path("flags"), "orderNumber");
        }
        if (candidate == null) {
            candidate = text(payload, "orderNumber");
        }
        if (candidate == null) {
            candidate = text(payload.path("order"), "number");
        }
        if (candidate == null) {
            candidate = text(payload, "orderNr");
        }
        if (candidate == null && fileName != null) {
            candidate = extractOrderNumber(fileName);
        }
        return normalizeOrderNumber(candidate);
    }

    private JsonNode normalizeExplanations(JsonNode node) {
        if (!isConcrete(node)) {
            return null;
        }
        if (node.isArray()) {
            return node;
        }
        if (node.isTextual()) {
            ArrayNode array = JsonNodeFactory.instance.arrayNode();
            array.add(node.asText());
            return array;
        }
        if (node.isObject() && node.isEmpty()) {
            return JsonNodeFactory.instance.arrayNode();
        }
        return node;
    }

    private boolean isConcrete(JsonNode node) {
        return node != null && !node.isMissingNode() && !node.isNull();
    }

    private String text(JsonNode node, String field) {
        if (!isConcrete(node)) {
            return null;
        }
        JsonNode target = node.get(field);
        if (!isConcrete(target)) {
            return null;
        }
        return target.asText();
    }

    private String textOrFallback(JsonNode node, String field, String fallback) {
        String value = text(node, field);
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private Double doubleOrNull(JsonNode node, String... fields) {
        if (!isConcrete(node)) {
            return null;
        }
        for (String field : fields) {
            JsonNode target = node.get(field);
            if (isConcrete(target) && target.isNumber()) {
                return target.doubleValue();
            }
        }
        for (String field : fields) {
            JsonNode target = node.get(field);
            if (isConcrete(target) && target.isTextual()) {
                try {
                    return Double.parseDouble(target.asText());
                } catch (NumberFormatException ignored) {
                    // tenta próximo campo
                }
            }
        }
        return null;
    }

    private Integer intOrNull(JsonNode node, String... fields) {
        if (!isConcrete(node)) {
            return null;
        }
        for (String field : fields) {
            JsonNode target = node.get(field);
            if (isConcrete(target) && target.isInt()) {
                return target.intValue();
            }
            if (isConcrete(target) && target.canConvertToInt()) {
                return target.intValue();
            }
            if (isConcrete(target) && target.isTextual()) {
                try {
                    return Integer.parseInt(target.asText());
                } catch (NumberFormatException ignored) {
                    // tenta próximo campo
                }
            }
        }
        return null;
    }

    private Long longOrNull(JsonNode node, String... fields) {
        if (!isConcrete(node)) {
            return null;
        }
        for (String field : fields) {
            JsonNode target = node.get(field);
            if (isConcrete(target) && target.isLong()) {
                return target.longValue();
            }
            if (isConcrete(target) && target.canConvertToLong()) {
                return target.longValue();
            }
            if (isConcrete(target) && target.isTextual()) {
                try {
                    return Long.parseLong(target.asText());
                } catch (NumberFormatException ignored) {
                    // tenta próximo campo
                }
            }
        }
        return null;
    }

    private JsonNode firstNonMissing(JsonNode... nodes) {
        if (nodes == null) {
            return null;
        }
        for (JsonNode node : nodes) {
            if (isConcrete(node)) {
                return node;
            }
        }
        return null;
    }

    private String extractOrderNumber(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        Matcher matcher = orderPattern.matcher(source);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String normalizeOrderNumber(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        String extracted = extractOrderNumber(trimmed);
        return extracted != null ? extracted : trimmed;
    }

    private void recordUploadStatusMetric(String status) {
        if (!hasText(status)) {
            return;
        }
        meterRegistry.counter("organizador_dxf_analysis_upload_total", "uploadStatus", status.toLowerCase(Locale.ROOT))
                .increment();
    }

    private boolean isHttpUrl(String value) {
        if (!hasText(value)) {
            return false;
        }
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        return trimmed.startsWith("http://") || trimmed.startsWith("https://");
    }

    private Double normalizeScoreStars(Double score) {
        double clamped = Math.max(0.0, Math.min(5.0, score));
        return Math.round(clamped * 2.0) / 2.0;
    }

    private String resolveImageUrl(DXFAnalysis analysis) {
        if (analysis == null) {
            return null;
        }
        String base = properties.getImageBaseUrl();
        String storageKey = analysis.getImageKey();
        if (hasText(base) && hasText(storageKey)) {
            String normalizedKey = normalizeStorageKey(storageKey);
            return joinBaseAndKey(base, normalizedKey);
        }
        String storageUri = analysis.getImageUri();
        if (hasText(base)) {
            String derivedKey = deriveStorageKey(storageUri, analysis.getImageBucket(), base);
            if (hasText(derivedKey)) {
                return joinBaseAndKey(base, derivedKey);
            }
        }
        if (hasText(storageUri)) {
            return storageUri;
        }
        String imagePath = analysis.getImagePath();
        if (!hasText(imagePath)) {
            return null;
        }
        if (imagePath.contains(":\\") || imagePath.matches("^[A-Za-z]:/.*")) {
            return null;
        }
        if (imagePath.startsWith("http://") || imagePath.startsWith("https://")) {
            return imagePath;
        }
        return imagePath;
    }

    private String joinBaseAndKey(String base, String key) {
        String normalizedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        String normalizedKey = key.startsWith("/") ? key.substring(1) : key;
        return normalizedBase + "/" + normalizedKey;
    }

    /**
     * Normaliza a chave do storage para garantir o formato correto da URL.
     * Removemos a normalização agressiva (lower-case + underscore) pois o MinIO é case-sensitive
     * e devemos respeitar o nome exato do arquivo gerado pelo FileWatcherApp.
     */
    private String normalizeStorageKey(String key) {
        if (!hasText(key)) {
            return key;
        }
        // Apenas padroniza separadores de diretório
        return key.trim().replace('\\', '/');
    }

    private String deriveStorageKey(String storageUri, String bucket, String baseUrl) {
        String path = extractPathFromUri(storageUri);
        if (!hasText(path)) {
            return null;
        }
        String bucketKey = extractKeyFromBucketPath(path, bucket);
        if (hasText(bucketKey)) {
            return normalizeStorageKey(bucketKey);
        }
        String baseKey = extractKeyFromBasePath(path, baseUrl);
        if (hasText(baseKey)) {
            return normalizeStorageKey(baseKey);
        }
        String firstSegmentKey = extractKeyFromFirstSegment(path);
        if (hasText(firstSegmentKey)) {
            return normalizeStorageKey(firstSegmentKey);
        }
        return null;
    }

    private String extractPathFromUri(String uriValue) {
        if (!hasText(uriValue)) {
            return null;
        }
        String trimmed = uriValue.trim();
        if (trimmed.startsWith("//")) {
            trimmed = "http:" + trimmed;
        }
        if (!(trimmed.startsWith("http://") || trimmed.startsWith("https://"))) {
            return null;
        }
        try {
            URI uri = URI.create(trimmed);
            return uri.getPath();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String extractKeyFromBucketPath(String path, String bucket) {
        if (!hasText(path) || !hasText(bucket)) {
            return null;
        }
        String normalizedPath = stripLeadingSlash(path);
        String normalizedBucket = bucket.trim();
        String marker = normalizedBucket + "/";
        if (normalizedPath.startsWith(marker)) {
            String key = normalizedPath.substring(marker.length());
            return key.isBlank() ? null : key;
        }
        int index = normalizedPath.indexOf("/" + marker);
        if (index >= 0) {
            String key = normalizedPath.substring(index + marker.length() + 1);
            return key.isBlank() ? null : key;
        }
        return null;
    }

    private String extractKeyFromBasePath(String path, String baseUrl) {
        if (!hasText(path) || !hasText(baseUrl)) {
            return null;
        }
        String basePath = normalizeBasePath(baseUrl);
        if (!hasText(basePath)) {
            return null;
        }
        String normalizedPath = stripLeadingSlash(path);
        String normalizedBasePath = stripLeadingSlash(basePath);
        String marker = normalizedBasePath.endsWith("/") ? normalizedBasePath : normalizedBasePath + "/";
        if (normalizedPath.startsWith(marker)) {
            String key = normalizedPath.substring(marker.length());
            return key.isBlank() ? null : key;
        }
        return null;
    }

    private String normalizeBasePath(String baseUrl) {
        if (!hasText(baseUrl)) {
            return null;
        }
        String trimmed = baseUrl.trim();
        if (trimmed.startsWith("//")) {
            trimmed = "http:" + trimmed;
        }
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            try {
                URI uri = URI.create(trimmed);
                return uri.getPath();
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
        if (trimmed.startsWith("/")) {
            return trimmed;
        }
        return null;
    }

    private String extractKeyFromFirstSegment(String path) {
        if (!hasText(path)) {
            return null;
        }
        String normalizedPath = stripLeadingSlash(path);
        int slashIndex = normalizedPath.indexOf('/');
        if (slashIndex < 0 || slashIndex == normalizedPath.length() - 1) {
            return null;
        }
        String key = normalizedPath.substring(slashIndex + 1);
        return key.isBlank() ? null : key;
    }

    private String stripLeadingSlash(String value) {
        if (!hasText(value)) {
            return value;
        }
        return value.startsWith("/") ? value.substring(1) : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private List<String> buildOrderCandidates(String orderNr) {
        List<String> candidates = new ArrayList<>();
        String normalized = normalizeOrderNumber(orderNr);
        if (hasText(normalized)) {
            candidates.add(normalized);
            candidates.add("NR" + normalized);
            candidates.add("CL" + normalized);
        }
        String trimmed = orderNr.trim();
        if (hasText(trimmed) && !candidates.contains(trimmed)) {
            candidates.add(trimmed);
        }
        return candidates;
    }

    private boolean hasImage(DXFAnalysis analysis) {
        if (analysis == null) {
            return false;
        }
        if (!hasText(analysis.getImageKey())
                && !hasText(analysis.getImageUri())
                && !hasText(analysis.getImagePath())) {
            return false;
        }

        String status = analysis.getImageUploadStatus();
        if (hasText(status)) {
            String normalized = status.trim().toLowerCase(Locale.ROOT);
            return normalized.equals("uploaded") || normalized.equals("exists");
        }

        Long sizeBytes = analysis.getImageSizeBytes();
        if (sizeBytes != null && sizeBytes > 0) {
            return true;
        }

        return hasText(analysis.getImageUri()) || hasText(analysis.getImagePath());
    }

    private boolean isAfter(OffsetDateTime candidate, OffsetDateTime reference) {
        if (candidate == null) {
            return false;
        }
        if (reference == null) {
            return true;
        }
        return candidate.isAfter(reference);
    }
}
