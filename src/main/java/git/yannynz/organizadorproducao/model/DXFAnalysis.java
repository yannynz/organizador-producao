package git.yannynz.organizadorproducao.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "dxf_analysis")
public class DXFAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "analysis_id", nullable = false, unique = true, length = 64)
    private String analysisId;

    @Column(name = "order_nr", length = 64)
    private String orderNr;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_hash", length = 128)
    private String fileHash;

    @Column(name = "image_path")
    private String imagePath;

    @Column(name = "image_width")
    private Integer imageWidth;

    @Column(name = "image_height")
    private Integer imageHeight;

    @Column(name = "image_bucket", length = 128)
    private String imageBucket;

    @Column(name = "image_key", length = 512)
    private String imageKey;

    @Column(name = "image_uri")
    private String imageUri;

    @Column(name = "image_checksum", length = 128)
    private String imageChecksum;

    @Column(name = "image_size_bytes")
    private Long imageSizeBytes;

    @Column(name = "image_content_type", length = 64)
    private String imageContentType;

    @Column(name = "image_upload_status", length = 32)
    private String imageUploadStatus;

    @Column(name = "image_upload_message")
    private String imageUploadMessage;

    @Column(name = "image_uploaded_at")
    private OffsetDateTime imageUploadedAt;

    @Column(name = "image_etag", length = 128)
    private String imageEtag;

    @Column(name = "score")
    private Double score;

    @Column(name = "score_label", length = 32)
    private String scoreLabel;

    @Column(name = "score_stars")
    private Double scoreStars;

    @Column(name = "total_cut_length_mm")
    private Double totalCutLengthMm;

    @Column(name = "curve_count")
    private Integer curveCount;

    @Column(name = "intersection_count")
    private Integer intersectionCount;

    @Column(name = "min_radius_mm")
    private Double minRadiusMm;

    @Column(name = "cache_hit", nullable = false)
    private boolean cacheHit;

    @Column(name = "analyzed_at", nullable = false)
    private OffsetDateTime analyzedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metrics_json")
    private JsonNode metrics;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "explanations_json")
    private JsonNode explanations;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload_json")
    private JsonNode rawPayload;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAnalysisId() {
        return analysisId;
    }

    public void setAnalysisId(String analysisId) {
        this.analysisId = analysisId;
    }

    public String getOrderNr() {
        return orderNr;
    }

    public void setOrderNr(String orderNr) {
        this.orderNr = orderNr;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public Integer getImageWidth() {
        return imageWidth;
    }

    public void setImageWidth(Integer imageWidth) {
        this.imageWidth = imageWidth;
    }

    public Integer getImageHeight() {
        return imageHeight;
    }

    public void setImageHeight(Integer imageHeight) {
        this.imageHeight = imageHeight;
    }

    public String getImageBucket() {
        return imageBucket;
    }

    public void setImageBucket(String imageBucket) {
        this.imageBucket = imageBucket;
    }

    public String getImageKey() {
        return imageKey;
    }

    public void setImageKey(String imageKey) {
        this.imageKey = imageKey;
    }

    public String getImageUri() {
        return imageUri;
    }

    public void setImageUri(String imageUri) {
        this.imageUri = imageUri;
    }

    public String getImageChecksum() {
        return imageChecksum;
    }

    public void setImageChecksum(String imageChecksum) {
        this.imageChecksum = imageChecksum;
    }

    public Long getImageSizeBytes() {
        return imageSizeBytes;
    }

    public void setImageSizeBytes(Long imageSizeBytes) {
        this.imageSizeBytes = imageSizeBytes;
    }

    public String getImageContentType() {
        return imageContentType;
    }

    public void setImageContentType(String imageContentType) {
        this.imageContentType = imageContentType;
    }

    public String getImageUploadStatus() {
        return imageUploadStatus;
    }

    public void setImageUploadStatus(String imageUploadStatus) {
        this.imageUploadStatus = imageUploadStatus;
    }

    public String getImageUploadMessage() {
        return imageUploadMessage;
    }

    public void setImageUploadMessage(String imageUploadMessage) {
        this.imageUploadMessage = imageUploadMessage;
    }

    public OffsetDateTime getImageUploadedAt() {
        return imageUploadedAt;
    }

    public void setImageUploadedAt(OffsetDateTime imageUploadedAt) {
        this.imageUploadedAt = imageUploadedAt;
    }

    public String getImageEtag() {
        return imageEtag;
    }

    public void setImageEtag(String imageEtag) {
        this.imageEtag = imageEtag;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public Double getScoreStars() {
        return scoreStars;
    }

    public void setScoreStars(Double scoreStars) {
        this.scoreStars = scoreStars;
    }

    public String getScoreLabel() {
        return scoreLabel;
    }

    public void setScoreLabel(String scoreLabel) {
        this.scoreLabel = scoreLabel;
    }

    public Double getTotalCutLengthMm() {
        return totalCutLengthMm;
    }

    public void setTotalCutLengthMm(Double totalCutLengthMm) {
            this.totalCutLengthMm = totalCutLengthMm;
    }

    public Integer getCurveCount() {
        return curveCount;
    }

    public void setCurveCount(Integer curveCount) {
        this.curveCount = curveCount;
    }

    public Integer getIntersectionCount() {
        return intersectionCount;
    }

    public void setIntersectionCount(Integer intersectionCount) {
        this.intersectionCount = intersectionCount;
    }

    public Double getMinRadiusMm() {
        return minRadiusMm;
    }

    public void setMinRadiusMm(Double minRadiusMm) {
        this.minRadiusMm = minRadiusMm;
    }

    public boolean isCacheHit() {
        return cacheHit;
    }

    public void setCacheHit(boolean cacheHit) {
        this.cacheHit = cacheHit;
    }

    public OffsetDateTime getAnalyzedAt() {
        return analyzedAt;
    }

    public void setAnalyzedAt(OffsetDateTime analyzedAt) {
        this.analyzedAt = analyzedAt;
    }

    public JsonNode getMetrics() {
        return metrics;
    }

    public void setMetrics(JsonNode metrics) {
        this.metrics = metrics;
    }

    public JsonNode getExplanations() {
        return explanations;
    }

    public void setExplanations(JsonNode explanations) {
        this.explanations = explanations;
    }

    public JsonNode getRawPayload() {
        return rawPayload;
    }

    public void setRawPayload(JsonNode rawPayload) {
        this.rawPayload = rawPayload;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DXFAnalysis that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
