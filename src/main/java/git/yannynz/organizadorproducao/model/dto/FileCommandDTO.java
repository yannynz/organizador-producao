package git.yannynz.organizadorproducao.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileCommandDTO {
    private String action; // RENAME_PRIORITY
    private String nr;
    private String newPriority;
    private String directory; // LASER or FACAS
}
