package rw.bnr.licensing.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import rw.bnr.licensing.enums.ApplicationStatus;
import rw.bnr.licensing.model.*;
import rw.bnr.licensing.repository.AuditLogRepository;

/**
 * AuditService — the ONLY class permitted to write audit log entries.
 *
 * Design rules enforced here:
 * 1. Only save() is ever called — never update() or delete().
 * 2. Propagation.REQUIRES_NEW — audit log is written in its OWN transaction.
 *    Even if the main transaction rolls back, the audit entry is preserved.
 *    This is critical: we want to know about failed attempts too.
 * 3. Every action type has a dedicated method — makes call sites readable
 *    and prevents typos in action strings.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    // ── State transition — REQUIRED: joins caller transaction (app FK must exist)
    @Transactional(propagation = Propagation.REQUIRED)
    public void logTransition(User actor, Application application,
                               ApplicationStatus from, ApplicationStatus to,
                               String metadata) {
        save(AuditLog.builder()
                .actor(actor)
                .application(application)
                .action("STATE_TRANSITION")
                .stateBefore(from != null ? from.name() : null)
                .stateAfter(to.name())
                .metadata(metadata)
                .build());
    }

    // ── Document upload — REQUIRED: joins caller transaction (app FK must exist)
    @Transactional(propagation = Propagation.REQUIRED)
    public void logDocumentUpload(User actor, Application application,
                                   String fileName, int versionNumber) {
        save(AuditLog.builder()
                .actor(actor)
                .application(application)
                .action("DOCUMENT_UPLOADED")
                .metadata(String.format(
                    "{\"fileName\":\"%s\",\"version\":%d}", fileName, versionNumber))
                .build());
    }

    // ── Login ────────────────────────────────────────────────────────────
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logLogin(User actor) {
        save(AuditLog.builder()
                .actor(actor)
                .action("USER_LOGIN")
                .build());
    }

    // ── User created ─────────────────────────────────────────────────────
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logUserCreated(User admin, User newUser) {
        save(AuditLog.builder()
                .actor(admin)
                .action("USER_CREATED")
                .metadata(String.format(
                    "{\"newUser\":\"%s\",\"role\":\"%s\"}",
                    newUser.getEmail(), newUser.getRole()))
                .build());
    }

    // ── Internal save — the single point of audit writes ─────────────────
    private void save(AuditLog entry) {
        auditLogRepository.save(entry);
        log.debug("Audit: actor={} action={} app={}",
                entry.getActor().getEmail(),
                entry.getAction(),
                entry.getApplication() != null
                    ? entry.getApplication().getReferenceNumber() : "N/A");
    }
}