package git.yannynz.organizadorproducao.model.dto;

import jakarta.validation.constraints.NotBlank;

public record DXFAnalysisRequestDTO(
        @NotBlank String filePath,
        String fileName,
        String fileHash,
        String orderNumber,
        Boolean forceReprocess,
        Boolean shadowMode
) {
}
