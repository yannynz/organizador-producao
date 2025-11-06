package git.yannynz.organizadorproducao.model.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;

public record DXFAnalysisView(
        String analysisId,
        String orderNr,
        Long orderId,
        Double score,
        String scoreLabel,
        Double scoreStars,
        Double totalCutLengthMm,
        Integer curveCount,
        Integer intersectionCount,
        Double minRadiusMm,
        boolean cacheHit,
        OffsetDateTime analyzedAt,
        String fileName,
        String fileHash,
        String imagePath,
        String imageUrl,
        String imageBucket,
        String imageKey,
        String imageUri,
        String imageChecksum,
        Long imageSizeBytes,
        String imageContentType,
        String imageUploadStatus,
        String imageUploadMessage,
        OffsetDateTime imageUploadedAt,
        String imageEtag,
        Integer imageWidth,
        Integer imageHeight,
        JsonNode metrics,
        JsonNode explanations
) {
}
