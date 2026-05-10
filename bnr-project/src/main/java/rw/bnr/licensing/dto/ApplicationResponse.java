package rw.bnr.licensing.dto;

import lombok.*;
import java.util.UUID;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class ApplicationResponse {
    private UUID id;
    private String referenceNumber;
    private String institutionName;
    private String institutionType;
    private String businessPlan;
    private Long registeredCapital;
    private String status;
    private String statusCode;
    private String statusDescription;
    private Long version;  // exposed for optimistic locking on client side

    // People involved
    private String applicantName;
    private String applicantEmail;
    private String reviewerName;
    private String approverName;

    // Workflow fields
    private String rejectionReason;
    private String reviewerNotes;
    private String additionalInfoRequest;

    // Timestamps
    private String submittedAt;
    private String decidedAt;
    private String createdAt;
}
