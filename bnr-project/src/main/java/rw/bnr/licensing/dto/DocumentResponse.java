package rw.bnr.licensing.dto;

import lombok.*;
import java.util.UUID;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class DocumentResponse {
    private UUID id;
    private String fileName;
    private Long fileSize;
    private String fileType;
    private String documentCategory;
    private Integer versionNumber;
    private boolean isCurrent;
    private String uploaderName;
    private String uploadedAt;
}
