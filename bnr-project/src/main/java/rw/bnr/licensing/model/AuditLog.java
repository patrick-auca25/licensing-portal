package rw.bnr.licensing.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * AuditLog — append-only record of every action taken in the system.
 *
 * Design decisions:
 * 1. No @Version — this entity is never updated, only inserted.
 * 2. No updatedAt column — its absence signals append-only intent.
 * 3. PostgreSQL DB role "audit_writer" has INSERT-only on this table.
 *    Even if application code attempts an UPDATE/DELETE it will fail at DB level.
 * 4. JSONB metadata column allows extra context without schema changes.
 *    Suitable for legal evidence: immutable, timestamped, actor-attributed.
 */
@Entity
@Table(name = "audit_log")
@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id")
    private Application application;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", nullable = false)
    private User actor;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "state_before")
    private String stateBefore;

    @Column(name = "state_after")
    private String stateAfter;

    // Extra context — reviewer notes, rejection reasons, file names etc.
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "ip_address")
    private String ipAddress;

    // No updatedAt — deliberately absent. Append-only.
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
