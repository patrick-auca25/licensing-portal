package rw.bnr.licensing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.bnr.licensing.audit.AuditService;
import rw.bnr.licensing.enums.ApplicationStatus;
import rw.bnr.licensing.enums.UserRole;
import rw.bnr.licensing.exception.IllegalStateTransitionException;
import rw.bnr.licensing.exception.ResourceNotFoundException;
import rw.bnr.licensing.model.Application;
import rw.bnr.licensing.model.User;
import rw.bnr.licensing.repository.ApplicationRepository;
import rw.bnr.licensing.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * StateMachineService — enforces ALL application workflow rules.
 *
 * This is the most critical class in the system. Every state transition
 * MUST go through this service. Controllers never update application
 * status directly — always via this service.
 *
 * Rules enforced:
 * 1. Only valid transitions from the matrix are permitted.
 * 2. Each transition requires a specific role.
 * 3. APPROVED and REJECTED are terminal — no exit.
 * 4. Four-eyes rule: reviewer != approver on same application.
 * 5. Optimistic locking (@Version) handles concurrent access.
 *
 * Transition matrix:
 *   DRAFT                    → SUBMITTED                (APPLICANT)
 *   SUBMITTED                → UNDER_REVIEW             (REVIEWER)
 *   UNDER_REVIEW             → ADDITIONAL_INFO_REQUESTED (REVIEWER)
 *   UNDER_REVIEW             → REVIEWED                 (REVIEWER)
 *   ADDITIONAL_INFO_REQUESTED → SUBMITTED               (APPLICANT)
 *   REVIEWED                 → APPROVED                 (APPROVER)
 *   REVIEWED                 → REJECTED                 (APPROVER)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StateMachineService {

    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    // ── Valid transition matrix ──────────────────────────────────────────
    // Key   = current state
    // Value = set of states this can legally transition TO
    private static final Map<ApplicationStatus, Set<ApplicationStatus>> VALID_TRANSITIONS =
        Map.of(
            ApplicationStatus.DRAFT,
                Set.of(ApplicationStatus.SUBMITTED),

            ApplicationStatus.SUBMITTED,
                Set.of(ApplicationStatus.UNDER_REVIEW),

            ApplicationStatus.UNDER_REVIEW,
                Set.of(ApplicationStatus.ADDITIONAL_INFO_REQUESTED,
                       ApplicationStatus.REVIEWED),

            ApplicationStatus.ADDITIONAL_INFO_REQUESTED,
                Set.of(ApplicationStatus.SUBMITTED),

            ApplicationStatus.REVIEWED,
                Set.of(ApplicationStatus.APPROVED,
                       ApplicationStatus.REJECTED),

            // Terminal states — empty sets, no outgoing transitions
            ApplicationStatus.APPROVED,  Set.of(),
            ApplicationStatus.REJECTED,  Set.of()
        );

    // ── Role required for each TARGET state ─────────────────────────────
    private static final Map<ApplicationStatus, UserRole> REQUIRED_ROLE =
        Map.of(
            ApplicationStatus.SUBMITTED,                  UserRole.APPLICANT,
            ApplicationStatus.UNDER_REVIEW,               UserRole.REVIEWER,
            ApplicationStatus.ADDITIONAL_INFO_REQUESTED,  UserRole.REVIEWER,
            ApplicationStatus.REVIEWED,                   UserRole.REVIEWER,
            ApplicationStatus.APPROVED,                   UserRole.APPROVER,
            ApplicationStatus.REJECTED,                   UserRole.APPROVER
        );

    // ═══════════════════════════════════════════════════════════════════
    // PUBLIC TRANSITION METHODS
    // Each method is named after the business action, not the state name.
    // This makes call sites readable: stateMachineService.submitApplication(...)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * APPLICANT submits a DRAFT application.
     * DRAFT → SUBMITTED
     */
    @Transactional
    public Application submitApplication(UUID applicationId, User actor) {
        Application app = loadAndValidate(applicationId, actor,
                ApplicationStatus.SUBMITTED);

        ApplicationStatus previous = app.getStatus();
        app.setStatus(ApplicationStatus.SUBMITTED);
        app.setSubmittedAt(LocalDateTime.now());

        Application saved = applicationRepository.save(app);
        auditService.logTransition(actor, saved, previous,
                ApplicationStatus.SUBMITTED,
                "{\"action\":\"Application submitted by applicant\"}");

        log.info("Application {} submitted by {}", app.getReferenceNumber(), actor.getEmail());
        return saved;
    }

    /**
     * REVIEWER claims an application for review.
     * SUBMITTED → UNDER_REVIEW
     * Sets reviewer_id — used later for four-eyes check.
     */
    @Transactional
    public Application startReview(UUID applicationId, User actor) {
        Application app = loadAndValidate(applicationId, actor,
                ApplicationStatus.UNDER_REVIEW);

        ApplicationStatus previous = app.getStatus();
        app.setStatus(ApplicationStatus.UNDER_REVIEW);
        app.setReviewer(actor);  // ← four-eyes: lock in reviewer identity

        Application saved = applicationRepository.save(app);
        auditService.logTransition(actor, saved, previous,
                ApplicationStatus.UNDER_REVIEW,
                String.format("{\"reviewer\":\"%s\"}", actor.getEmail()));

        log.info("Application {} claimed for review by {}",
                app.getReferenceNumber(), actor.getEmail());
        return saved;
    }

    /**
     * REVIEWER requests additional information from the applicant.
     * UNDER_REVIEW → ADDITIONAL_INFO_REQUESTED
     */
    @Transactional
    public Application requestAdditionalInfo(UUID applicationId, User actor,
                                              String requestDetails) {
        Application app = loadAndValidate(applicationId, actor,
                ApplicationStatus.ADDITIONAL_INFO_REQUESTED);

        // Reviewer can only act on applications they claimed
        assertIsAssignedReviewer(app, actor);

        ApplicationStatus previous = app.getStatus();
        app.setStatus(ApplicationStatus.ADDITIONAL_INFO_REQUESTED);
        app.setAdditionalInfoRequest(requestDetails);

        Application saved = applicationRepository.save(app);
        auditService.logTransition(actor, saved, previous,
                ApplicationStatus.ADDITIONAL_INFO_REQUESTED,
                String.format("{\"request\":\"%s\"}", sanitize(requestDetails)));

        log.info("Additional info requested on {} by {}",
                app.getReferenceNumber(), actor.getEmail());
        return saved;
    }

    /**
     * APPLICANT resubmits after additional info was requested.
     * ADDITIONAL_INFO_REQUESTED → SUBMITTED
     */
    @Transactional
    public Application resubmitApplication(UUID applicationId, User actor) {
        Application app = loadAndValidate(applicationId, actor,
                ApplicationStatus.SUBMITTED);

        // Only the original applicant can resubmit
        assertIsApplicant(app, actor);

        ApplicationStatus previous = app.getStatus();
        app.setStatus(ApplicationStatus.SUBMITTED);
        app.setSubmittedAt(LocalDateTime.now());

        Application saved = applicationRepository.save(app);
        auditService.logTransition(actor, saved, previous,
                ApplicationStatus.SUBMITTED,
                "{\"action\":\"Resubmission after additional info request\"}");

        log.info("Application {} resubmitted by {}", app.getReferenceNumber(), actor.getEmail());
        return saved;
    }

    /**
     * REVIEWER completes their assessment.
     * UNDER_REVIEW → REVIEWED
     */
    @Transactional
    public Application completeReview(UUID applicationId, User actor,
                                       String reviewerNotes) {
        Application app = loadAndValidate(applicationId, actor,
                ApplicationStatus.REVIEWED);

        assertIsAssignedReviewer(app, actor);

        ApplicationStatus previous = app.getStatus();
        app.setStatus(ApplicationStatus.REVIEWED);
        app.setReviewerNotes(reviewerNotes);

        Application saved = applicationRepository.save(app);
        auditService.logTransition(actor, saved, previous,
                ApplicationStatus.REVIEWED,
                String.format("{\"notes\":\"%s\"}", sanitize(reviewerNotes)));

        log.info("Application {} review completed by {}",
                app.getReferenceNumber(), actor.getEmail());
        return saved;
    }

    /**
     * APPROVER grants final approval.
     * REVIEWED → APPROVED
     *
     * FOUR-EYES RULE ENFORCED HERE:
     * The approver cannot be the same person who reviewed the application.
     */
    @Transactional
    public Application approveApplication(UUID applicationId, User actor) {
        Application app = loadAndValidate(applicationId, actor,
                ApplicationStatus.APPROVED);

        // ── FOUR-EYES RULE ───────────────────────────────────────────────
        // This check is the non-negotiable requirement.
        // It runs in the backend service — bypassing the UI still hits this.
        assertFourEyesRule(app, actor);

        ApplicationStatus previous = app.getStatus();
        app.setStatus(ApplicationStatus.APPROVED);
        app.setApprover(actor);
        app.setDecidedAt(LocalDateTime.now());

        Application saved = applicationRepository.save(app);
        auditService.logTransition(actor, saved, previous,
                ApplicationStatus.APPROVED,
                String.format("{\"approver\":\"%s\"}", actor.getEmail()));

        log.info("Application {} APPROVED by {}", app.getReferenceNumber(), actor.getEmail());
        return saved;
    }

    /**
     * APPROVER rejects the application with mandatory reason.
     * REVIEWED → REJECTED
     *
     * FOUR-EYES RULE ENFORCED HERE as well.
     */
    @Transactional
    public Application rejectApplication(UUID applicationId, User actor,
                                          String rejectionReason) {
        if (rejectionReason == null || rejectionReason.isBlank()) {
            throw new IllegalArgumentException(
                    "A rejection reason is mandatory when rejecting an application.");
        }

        Application app = loadAndValidate(applicationId, actor,
                ApplicationStatus.REJECTED);

        // ── FOUR-EYES RULE ───────────────────────────────────────────────
        assertFourEyesRule(app, actor);

        ApplicationStatus previous = app.getStatus();
        app.setStatus(ApplicationStatus.REJECTED);
        app.setApprover(actor);
        app.setRejectionReason(rejectionReason);
        app.setDecidedAt(LocalDateTime.now());

        Application saved = applicationRepository.save(app);
        auditService.logTransition(actor, saved, previous,
                ApplicationStatus.REJECTED,
                String.format("{\"approver\":\"%s\",\"reason\":\"%s\"}",
                        actor.getEmail(), sanitize(rejectionReason)));

        log.info("Application {} REJECTED by {}", app.getReferenceNumber(), actor.getEmail());
        return saved;
    }

    // ═══════════════════════════════════════════════════════════════════
    // PRIVATE VALIDATION METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Core validation — runs before every transition.
     * 1. Load the application
     * 2. Check terminal state
     * 3. Check transition is in valid matrix
     * 4. Check actor has correct role
     */
    private Application loadAndValidate(UUID applicationId, User actor,
                                         ApplicationStatus targetStatus) {

        Application app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Application not found: " + applicationId));

        ApplicationStatus current = app.getStatus();

        // ── 1. Terminal state check ──────────────────────────────────────
        if (current == ApplicationStatus.APPROVED || current == ApplicationStatus.REJECTED) {
            throw new IllegalStateTransitionException(
                    "Application " + app.getReferenceNumber() +
                    " is in a terminal state (" + current + ") and cannot be modified.");
        }

        // ── 2. Valid transition matrix check ────────────────────────────
        Set<ApplicationStatus> allowed = VALID_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(targetStatus)) {
            throw new IllegalStateTransitionException(
                    "Invalid transition: " + current + " → " + targetStatus +
                    " is not a permitted state change.");
        }

        // ── 3. Role check ────────────────────────────────────────────────
        UserRole required = REQUIRED_ROLE.get(targetStatus);
        if (required != null && actor.getRole() != required) {
            throw new AccessDeniedException(
                    "Role " + actor.getRole() + " cannot perform transition to " +
                    targetStatus + ". Required role: " + required);
        }

        return app;
    }

    /**
     * Four-eyes rule enforcement.
     * The person who reviewed cannot be the final approver.
     * Checked for both APPROVED and REJECTED transitions.
     */
    private void assertFourEyesRule(Application app, User approver) {
        if (app.getReviewer() != null &&
                app.getReviewer().getId().equals(approver.getId())) {
            throw new AccessDeniedException(
                    "Four-eyes rule violation: The reviewer of application " +
                    app.getReferenceNumber() +
                    " cannot also be the approver. " +
                    "A different BNR officer must make the final decision.");
        }
    }

    /**
     * Ensures only the assigned reviewer can act on an application
     * once it is in UNDER_REVIEW state.
     */
    private void assertIsAssignedReviewer(Application app, User actor) {
        if (app.getReviewer() == null ||
                !app.getReviewer().getId().equals(actor.getId())) {
            throw new AccessDeniedException(
                    "Only the assigned reviewer can act on application " +
                    app.getReferenceNumber());
        }
    }

    /**
     * Ensures only the original applicant can resubmit their application.
     */
    private void assertIsApplicant(Application app, User actor) {
        if (!app.getApplicant().getId().equals(actor.getId())) {
            throw new AccessDeniedException(
                    "Only the original applicant can resubmit application " +
                    app.getReferenceNumber());
        }
    }

    /**
     * Sanitize strings before embedding in JSON metadata.
     * Prevents JSON injection in audit log metadata field.
     */
    private String sanitize(String input) {
        if (input == null) return "";
        return input.replace("\"", "'").replace("\n", " ").trim();
    }
}
