package rw.bnr.licensing.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import rw.bnr.licensing.dto.*;
import rw.bnr.licensing.model.Application;
import rw.bnr.licensing.model.User;
import rw.bnr.licensing.security.SecurityUtils;
import rw.bnr.licensing.service.ApplicationService;
import rw.bnr.licensing.service.StateMachineService;

import java.util.List;
import java.util.UUID;

/**
 * ApplicationController — REST API for the full application lifecycle.
 *
 * URL structure:
 *   POST   /api/applications              — create new DRAFT (APPLICANT)
 *   GET    /api/applications              — list applications (role-filtered)
 *   GET    /api/applications/{id}         — get single application
 *   PUT    /api/applications/{id}         — update DRAFT details (APPLICANT)
 *
 * Workflow actions (state transitions):
 *   POST   /api/applications/{id}/submit            — DRAFT → SUBMITTED
 *   POST   /api/applications/{id}/start-review      — SUBMITTED → UNDER_REVIEW
 *   POST   /api/applications/{id}/request-info      — UNDER_REVIEW → ADDITIONAL_INFO_REQUESTED
 *   POST   /api/applications/{id}/resubmit          — ADDITIONAL_INFO_REQUESTED → SUBMITTED
 *   POST   /api/applications/{id}/complete-review   — UNDER_REVIEW → REVIEWED
 *   POST   /api/applications/{id}/approve           — REVIEWED → APPROVED
 *   POST   /api/applications/{id}/reject            — REVIEWED → REJECTED
 *
 * Security:
 * - @PreAuthorize enforces role at METHOD level — not just URL level.
 * - A user who bypasses the frontend and calls the API directly
 *   is STILL denied here. This is the non-negotiable backend enforcement.
 * - Unauthorised → 403 (handled by GlobalExceptionHandler)
 */
@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
@Slf4j
public class ApplicationController {

    private final ApplicationService applicationService;
    private final StateMachineService stateMachineService;
    private final SecurityUtils securityUtils;

    // ── CREATE ────────────────────────────────────────────────────────────
    @PostMapping
    @PreAuthorize("hasRole('APPLICANT')")
    public ResponseEntity<ApplicationResponse> createApplication(
            @Valid @RequestBody ApplicationRequest request) {
        User actor = securityUtils.getCurrentUser();
        ApplicationResponse response = applicationService.createApplication(request, actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── LIST (role-filtered) ──────────────────────────────────────────────
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ApplicationResponse>> listApplications() {
        User actor = securityUtils.getCurrentUser();
        return ResponseEntity.ok(applicationService.getApplicationsForUser(actor));
    }

    // ── GET SINGLE ────────────────────────────────────────────────────────
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApplicationResponse> getApplication(@PathVariable UUID id) {
        User actor = securityUtils.getCurrentUser();
        return ResponseEntity.ok(applicationService.getApplication(id, actor));
    }

    // ── UPDATE DRAFT ──────────────────────────────────────────────────────
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('APPLICANT')")
    public ResponseEntity<ApplicationResponse> updateApplication(
            @PathVariable UUID id,
            @Valid @RequestBody ApplicationRequest request) {
        User actor = securityUtils.getCurrentUser();
        return ResponseEntity.ok(applicationService.updateApplication(id, request, actor));
    }

    // ════════════════════════════════════════════════════════════════════
    // WORKFLOW TRANSITION ENDPOINTS
    // Each endpoint delegates to StateMachineService which enforces:
    // - Valid transition matrix
    // - Role requirements
    // - Four-eyes rule
    // - Terminal state checks
    // - Optimistic locking
    // ════════════════════════════════════════════════════════════════════

    /**
     * APPLICANT submits their application.
     * DRAFT → SUBMITTED
     */
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('APPLICANT')")
    public ResponseEntity<ApplicationResponse> submitApplication(@PathVariable UUID id) {
        User actor = securityUtils.getCurrentUser();
        Application result = stateMachineService.submitApplication(id, actor);
        return ResponseEntity.ok(applicationService.toResponse(result));
    }

    /**
     * REVIEWER claims an application to review.
     * SUBMITTED → UNDER_REVIEW
     */
    @PostMapping("/{id}/start-review")
    @PreAuthorize("hasRole('REVIEWER')")
    public ResponseEntity<ApplicationResponse> startReview(@PathVariable UUID id) {
        User actor = securityUtils.getCurrentUser();
        Application result = stateMachineService.startReview(id, actor);
        return ResponseEntity.ok(applicationService.toResponse(result));
    }

    /**
     * REVIEWER requests additional information from applicant.
     * UNDER_REVIEW → ADDITIONAL_INFO_REQUESTED
     */
    @PostMapping("/{id}/request-info")
    @PreAuthorize("hasRole('REVIEWER')")
    public ResponseEntity<ApplicationResponse> requestAdditionalInfo(
            @PathVariable UUID id,
            @RequestBody WorkflowActionRequest request) {
        User actor = securityUtils.getCurrentUser();
        Application result = stateMachineService.requestAdditionalInfo(
                id, actor, request.getAdditionalInfoRequest());
        return ResponseEntity.ok(applicationService.toResponse(result));
    }

    /**
     * APPLICANT resubmits after additional info was requested.
     * ADDITIONAL_INFO_REQUESTED → SUBMITTED
     */
    @PostMapping("/{id}/resubmit")
    @PreAuthorize("hasRole('APPLICANT')")
    public ResponseEntity<ApplicationResponse> resubmitApplication(@PathVariable UUID id) {
        User actor = securityUtils.getCurrentUser();
        Application result = stateMachineService.resubmitApplication(id, actor);
        return ResponseEntity.ok(applicationService.toResponse(result));
    }

    /**
     * REVIEWER completes their review assessment.
     * UNDER_REVIEW → REVIEWED
     */
    @PostMapping("/{id}/complete-review")
    @PreAuthorize("hasRole('REVIEWER')")
    public ResponseEntity<ApplicationResponse> completeReview(
            @PathVariable UUID id,
            @RequestBody WorkflowActionRequest request) {
        User actor = securityUtils.getCurrentUser();
        Application result = stateMachineService.completeReview(id, actor, request.getNotes());
        return ResponseEntity.ok(applicationService.toResponse(result));
    }

    /**
     * APPROVER grants final approval.
     * REVIEWED → APPROVED
     * Four-eyes rule enforced in StateMachineService.
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('APPROVER')")
    public ResponseEntity<ApplicationResponse> approveApplication(@PathVariable UUID id) {
        User actor = securityUtils.getCurrentUser();
        Application result = stateMachineService.approveApplication(id, actor);
        return ResponseEntity.ok(applicationService.toResponse(result));
    }

    /**
     * APPROVER rejects application with mandatory reason.
     * REVIEWED → REJECTED
     * Four-eyes rule enforced in StateMachineService.
     */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('APPROVER')")
    public ResponseEntity<ApplicationResponse> rejectApplication(
            @PathVariable UUID id,
            @RequestBody WorkflowActionRequest request) {
        User actor = securityUtils.getCurrentUser();
        Application result = stateMachineService.rejectApplication(
                id, actor, request.getReason());
        return ResponseEntity.ok(applicationService.toResponse(result));
    }
}
