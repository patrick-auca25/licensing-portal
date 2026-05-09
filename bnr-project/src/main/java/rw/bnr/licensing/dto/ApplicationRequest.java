package rw.bnr.licensing.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class ApplicationRequest {

    @NotBlank(message = "Institution name is required")
    @Size(max = 255, message = "Institution name must not exceed 255 characters")
    private String institutionName;

    @NotBlank(message = "Institution type is required")
    private String institutionType;

    @Size(max = 5000, message = "Business plan must not exceed 5000 characters")
    private String businessPlan;

    @Min(value = 0, message = "Registered capital must be a positive value")
    private Long registeredCapital;
}
