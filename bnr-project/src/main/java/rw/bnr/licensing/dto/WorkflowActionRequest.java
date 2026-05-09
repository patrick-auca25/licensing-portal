package rw.bnr.licensing.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Used for workflow actions that require a text reason or notes.
 * - Rejection: rejectionReason is mandatory
 * - Request additional info: details of what is needed
 * - Complete review: reviewer's assessment notes
 */
@Data
public class WorkflowActionRequest {

    // Optional for some actions, mandatory for rejection
    // Validated at service level depending on action type
    private String reason;

    private String notes;

    private String additionalInfoRequest;
}
