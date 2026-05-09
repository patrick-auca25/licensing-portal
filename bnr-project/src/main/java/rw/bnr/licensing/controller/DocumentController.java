package rw.bnr.licensing.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import rw.bnr.licensing.dto.DocumentResponse;
import rw.bnr.licensing.model.User;
import rw.bnr.licensing.security.SecurityUtils;
import rw.bnr.licensing.service.DocumentService;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/applications/{applicationId}/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final SecurityUtils securityUtils;

    // ── Upload document ───────────────────────────────────────────────────
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('APPLICANT')")
    public ResponseEntity<DocumentResponse> uploadDocument(
            @PathVariable UUID applicationId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "category", defaultValue = "GENERAL") String category
    ) throws IOException {
        User actor = securityUtils.getCurrentUser();
        DocumentResponse response = documentService.uploadDocument(
                applicationId, category, file, actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── Get all documents (all versions) ─────────────────────────────────
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<DocumentResponse>> getAllDocuments(
            @PathVariable UUID applicationId) {
        return ResponseEntity.ok(documentService.getDocuments(applicationId));
    }

    // ── Get current documents only ────────────────────────────────────────
    @GetMapping("/current")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<DocumentResponse>> getCurrentDocuments(
            @PathVariable UUID applicationId) {
        return ResponseEntity.ok(documentService.getCurrentDocuments(applicationId));
    }
}
