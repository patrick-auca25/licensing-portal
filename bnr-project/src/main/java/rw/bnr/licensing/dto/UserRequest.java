package rw.bnr.licensing.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class UserRequest {

    @Email(message = "Valid email required")
    @NotBlank(message = "Email is required")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Role is required")
    @Pattern(regexp = "APPLICANT|REVIEWER|APPROVER|ADMIN",
             message = "Role must be one of: APPLICANT, REVIEWER, APPROVER, ADMIN")
    private String role;
}
