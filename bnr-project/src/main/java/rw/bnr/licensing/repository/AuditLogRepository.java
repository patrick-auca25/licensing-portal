package rw.bnr.licensing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.bnr.licensing.model.AuditLog;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    List<AuditLog> findByApplicationIdOrderByCreatedAtAsc(UUID applicationId);
    List<AuditLog> findByActorIdOrderByCreatedAtDesc(UUID actorId);
}
