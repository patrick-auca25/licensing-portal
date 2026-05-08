package rw.bnr.licensing.dto;

import lombok.*;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class AuthResponse {
    private String token;
    private String email;
    private String fullName;
    private String role;
    private long expiresIn;  // milliseconds
}
