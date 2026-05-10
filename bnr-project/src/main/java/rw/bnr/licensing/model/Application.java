package rw.bnr.licensing.model;

import jakarta.persistence.*;
import lombok.*;
import rw.bnr.licensing.enums.ApplicationStatus;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "applications")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "reference_number", nullable = false, unique = true)
    private String referenceNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_id", nullable = false)
    private User applicant;

    @Column(name = "institution_name", nullable = false)
    private String institutionName;

    @Column(name = "institution_type", nullable = false)
    private String institutionType;

    @Column(name = "business_plan", columnDefinition = "TEXT")
    private String businessPlan;

    @Column(name = "registered_capital")
    private Long registeredCapital;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "application_status")
    @Builder.Default
    private ApplicationStatus status = ApplicationStatus.DRAFT;

    // ── Optimistic locking ──────────────────────────────────────────────────
    // This @Version field is the mechanism for handling concurrent access.
    // Every UPDATE includes WHERE id=? AND version=?
    // If two users act simultaneously, the second update finds version changed
    // and throws ObjectOptimisticLockingFailureException → caught → HTTP 409
    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    // ── Reviewer assignment (four-eyes rule) ────────────────────────────────
    // Set when REVIEWER claims application (UNDER_REVIEW transition).
    // Checked on APPROVED/REJECTED: approver must NOT equal reviewer.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id")
    private User reviewer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_id")
    private User approver;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "reviewer_notes", columnDefinition = "TEXT")
    private String reviewerNotes;

    @Column(name = "additional_info_request", columnDefinition = "TEXT")
    private String additionalInfoRequest;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}