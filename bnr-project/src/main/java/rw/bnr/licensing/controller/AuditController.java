package rw.bnr.licensing.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import rw.bnr.licensing.model.AuditLog;
import rw.bnr.licensing.repository.AuditLogRepository;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditLogRepository auditLogRepository;

    // ── Full audit trail for one application ─────────────────────────────
    @GetMapping("/applications/{applicationId}")
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER','APPROVER')")
    public ResponseEntity<List<AuditLog>> getApplicationAuditLog(
            @PathVariable UUID applicationId) {
        return ResponseEntity.ok(
                auditLogRepository.findByApplicationIdOrderByCreatedAtAsc(applicationId));
    }

    // ── All audit entries (admin only) ────────────────────────────────────
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AuditLog>> getAllAuditLogs() {
        return ResponseEntity.ok(auditLogRepository.findAll());
    }
}
