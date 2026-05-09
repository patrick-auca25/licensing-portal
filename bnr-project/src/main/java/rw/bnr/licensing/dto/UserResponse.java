package rw.bnr.licensing.dto;

import lombok.*;
import java.util.UUID;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class UserResponse {
    private UUID id;
    private String email;
    private String fullName;
    private String role;
    private boolean isActive;
}
