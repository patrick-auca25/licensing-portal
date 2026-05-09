package rw.bnr.licensing.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import rw.bnr.licensing.audit.AuditService;
import rw.bnr.licensing.enums.ApplicationStatus;
import rw.bnr.licensing.enums.UserRole;
import rw.bnr.licensing.exception.IllegalStateTransitionException;
import rw.bnr.licensing.exception.ResourceNotFoundException;
import rw.bnr.licensing.model.Application;
import rw.bnr.licensing.model.User;
import rw.bnr.licensing.repository.ApplicationRepository;
import rw.bnr.licensing.repository.UserRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * StateMachineServiceTest
 *
 * Tests cover:
 * 1.  Valid transitions — happy path for every state change
 * 2.  Invalid transitions — rejected at service level
 * 3.  Terminal state enforcement — APPROVED and REJECTED block all transitions
 * 4.  Four-eyes rule — reviewer cannot approve their own reviewed application
 * 5.  Role enforcement — wrong role rejected
 * 6.  Concurrent access — ObjectOptimisticLockingFailureException on version conflict
 * 7.  Edge cases — missing rejection reason, wrong applicant resubmitting
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StateMachineService Tests")
class StateMachineServiceTest {

    @Mock private ApplicationRepository applicationRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;

    @InjectMocks
    private StateMachineService stateMachineService;

    // ── Test users ───────────────────────────────────────────────────────
    private User applicant;
    private User reviewer;
    private User approver;
    private User differentReviewer;

    // ── Test application ─────────────────────────────────────────────────
    private Application application;
    private UUID appId;

    @BeforeEach
    void setUp() {
        appId = UUID.randomUUID();

        applicant = User.builder()
                .id(UUID.randomUUID()).email("applicant@test.com")
                .role(UserRole.APPLICANT).active(true).build();

        reviewer = User.builder()
                .id(UUID.randomUUID()).email("reviewer@bnr.rw")
                .role(UserRole.REVIEWER).active(true).build();

        approver = User.builder()
                .id(UUID.randomUUID()).email("approver@bnr.rw")
                .role(UserRole.APPROVER).active(true).build();

        differentReviewer = User.builder()
                .id(UUID.randomUUID()).email("reviewer2@bnr.rw")
                .role(UserRole.REVIEWER).active(true).build();

        application = Application.builder()
                .id(appId)
                .referenceNumber("BNR-2026-TEST")
                .applicant(applicant)
                .institutionName("Test Bank Ltd")
                .institutionType("Commercial Bank")
                .status(ApplicationStatus.DRAFT)
                .version(0L)
                .build();

        // Default: save returns whatever is passed in
        lenient().when(applicationRepository.save(any(Application.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ═══════════════════════════════════════════════════════════════════
    // 1. VALID TRANSITIONS — HAPPY PATH
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("DRAFT → SUBMITTED: applicant can submit their own application")
    void submitApplication_validTransition_success() {
        application.setStatus(ApplicationStatus.DRAFT);
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(application));

        Application result = stateMachineService.submitApplication(appId, applicant);

        assertThat(result.getStatus()).isEqualTo(ApplicationStatus.SUBMITTED);
        assertThat(result.getSubmittedAt()).isNotNull();
        verify(applicationRepository).save(application);
        verify(auditService).logTransition(eq(applicant), eq(result),
                eq(ApplicationStatus.DRAFT), eq(ApplicationStatus.SUBMITTED), any());
    }

    @Test
    @DisplayName("SUBMITTED → UNDER_REVIEW: reviewer can claim an application")
    void startReview_validTransition_setsReviewerId() {
        application.setStatus(ApplicationStatus.SUBMITTED);
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(application));

        Application result = stateMachineService.startReview(appId, reviewer);

        assertThat(result.getStatus()).isEqualTo(ApplicationStatus.UNDER_REVIEW);
        assertThat(result.getReviewer()).isEqualTo(reviewer);
        verify(applicationRepository).save(application);
    }

    @Test
    @DisplayName("UNDER_REVIEW → ADDITIONAL_INFO_REQUESTED: assigned reviewer can request info")
    void requestAdditionalInfo_validTransition_success() {
        application.setStatus(ApplicationStatus.UNDER_REVIEW);
        application.setReviewer(reviewer);
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(application));

        Application result = stateMachineService.requestAdditionalInfo(
                appId, reviewer, "Please provide audited financials for 2023 and 2024.");

        assertThat(result.getStatus()).isEqualTo(ApplicationStatus.ADDITIONAL_INFO_REQUESTED);
        assertThat(result.getAdditionalInfoRequest()).isNotBlank();
    }

    @Test
    @DisplayName("ADDITIONAL_INFO_REQUESTED → SUBMITTED: applicant can resubmit")
    void resubmitApplication_validTransition_success() {
        application.setStatus(ApplicationStatus.ADDITIONAL_INFO_REQUESTED);
        application.setReviewer(reviewer);
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(application));

        Application result = stateMachineService.resubmitApplication(appId, applicant);

        assertThat(result.getStatus()).isEqualTo(ApplicationStatus.SUBMITTED);
        assertThat(result.getSubmittedAt()).isNotNull();
    }

    @Test
    @DisplayName("UNDER_REVIEW → REVIEWED: assigned reviewer can complete review")
    void completeReview_validTransition_success() {
        application.setStatus(ApplicationStatus.UNDER_REVIEW);
        application.setReviewer(reviewer);
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(application));

        Application result = stateMachineService.completeReview(
                appId, reviewer, "All documents verified. Recommend approval.");

        assertThat(result.getStatus()).isEqualTo(ApplicationStatus.REVIEWED);
        assertThat(result.getReviewerNotes()).contains("Recommend approval");
    }

    @Test
    @DisplayName("REVIEWED → APPROVED: approver can approve a reviewed application")
    void approveApplication_validTransition_success() {
        application.setStatus(ApplicationStatus.REVIEWED);
        application.setReviewer(reviewer); // different from approver
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(application));

        Application result = stateMachineService.approveApplication(appId, approver);

        assertThat(result.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
        assertThat(result.getApprover()).isEqualTo(approver);
        assertThat(result.getDecidedAt()).isNotNull();
    }

    @Test
    @DisplayName("REVIEWED → REJECTED: approver can reject with reason")
    void rejectApplication_validTransition_success() {
        application.setStatus(ApplicationStatus.REVIEWED);
        application.setReviewer(reviewer);
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(application));

        Application result = stateMachineService.rejectApplication(
                appId, approver, "Insufficient capital reserves for licence category.");

        assertThat(result.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(result.getRejectionReason()).isNotBlank();
        assertThat(result.getDecidedAt()).isNotNull();
    }

    // ═══════════════════════════════════════════════════════════════════
    // 2. INVALID TRANSITIONS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("DRAFT → UNDER_REVIEW: invalid — must go through SUBMITTED first")
    void transition_draftToUnderReview_throwsIllegalStateTransition() {
        application.setStatus(ApplicationStatus.DRAFT);
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> stateMachineService.startReview(appId, reviewer))
                .isInstanceOf(IllegalStateTransitionException.class)
                .hasMessageContaining("DRAFT")
                .hasMessageContaining("UNDER_REVIEW");
    }

    @Test
    @DisplayName("SUBMITTED → APPROVED: invalid — must go through review workflow")
    void transition_submittedToApproved_throwsIllegalStateTransition() {
        application.setStatus(ApplicationStatus.SUBMITTED);
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> stateMachineService.approveApplication(appId, approver))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    @DisplayName("REVIEWED → SUBMITTED: invalid — cannot go backwards")
    void transition_reviewedToSubmitted_throwsIllegalStateTransition() {
        application.setStatus(ApplicationStatus.REVIEWED);
        application.setReviewer(reviewer);
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> stateMachineService.resubmitApplication(appId, applicant))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    // ═══════════════════════════════════════════════════════════════════
    // 3. TERMINAL STATE ENFORCEMENT
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("APPROVED is terminal — no further transitions permitted")
    void transition_fromApproved_throwsIllegalStateTransition() {
        application.setStatus(ApplicationStatus.APPROVED);
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> stateMachineService.rejectApplication(
                    appId, approver, "Try again"))
                .isInstanceOf(IllegalStateTransitionException.class)
                .hasMessageContaining("terminal");
    }

    @Test
    @DisplayName("REJECTED is terminal — no further transitions permitted")
    void transition_fromRejected_throwsIllegalStateTransition() {
        application.setStatus(ApplicationStatus.REJECTED);
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> stateMachineService.approveApplication(appId, approver))
                .isInstanceOf(IllegalStateTransitionException.class)
                .hasMessageContaining("terminal");
    }

    @Test
    @DisplayName("APPROVED terminal — submitting again is blocked")
    void transition_fromApproved_submitAgain_throwsIllegalStateTransition() {
        application.setStatus(ApplicationStatus.APPROVED);
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> stateMachineService.submitApplication(appId, applicant))
                .isInstanceOf(IllegalStateTransitionException.class)
                .hasMessageContaining("terminal");
    }

    // ═══════════════════════════════════════════════════════════════════
    // 4. FOUR-EYES RULE
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Four-eyes: reviewer cannot approve their own reviewed application")
    void approveApplication_reviewerIsApprover_throwsAccessDenied() {
        application.setStatus(ApplicationStatus.REVIEWED);
        application.setReviewer(reviewer); // reviewer and approver are the same person

        // reviewer is trying to act as approver — same user ID
        User reviewerActingAsApprover = User.builder()
                .id(reviewer.getId()) // SAME ID
                .email(reviewer.getEmail())
                .role(UserRole.APPROVER) // has approver role but same person
                .active(true)
                .build();

        when(applicationRepository.findById(appId)).thenReturn(Optional.of(application));

        assertThatThrownBy(() ->
                stateMachineService.approveApplication(appId, reviewerActingAsApprover))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Four-eyes rule violation");
    }

    @Test
    @DisplayName("Four-eyes: reviewer cannot reject their own reviewed application")
    void rejectApplication_reviewerIsApprover_throwsAccessDenied() {
        application.setStatus(ApplicationStatus.REVIEWED);
        application.setReviewer(reviewer);

        User reviewerActingAsApprover = User.builder()
                .id(reviewer.getId())
                .email(reviewer.getEmail())
                .role(UserRole.APPROVER)
                .active(true)
                .build();

        when(applicationRepository.findById(appId)).thenReturn(Optional.of(application));

        assertThatThrownBy(() ->
                stateMachineService.rejectApplication(appId, reviewerActingAsApprover,
                        "Some reason"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Four-eyes rule violation");
    }

    @Test
    @DisplayName("Four-eyes: different approver can approve after different reviewer")
    void approveApplication_differentApprover_success() {
        application.setStatus(ApplicationStatus.REVIEWED);
        application.setReviewer(reviewer); // reviewer != approver
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(application));

        // approver has a completely different ID from reviewer
        Application result = stateMachineService.approveApplication(appId, approver);

        assertThat(result.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
    }

    // ═══════════════════════════════════════════════════════════════════
    // 5. ROLE ENFORCEMENT
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Role check: APPLICANT cannot start a review")
    void startReview_byApplicant_throwsAccessDenied() {
        application.setStatus(ApplicationStatus.SUBMITTED);
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> stateMachineService.startReview(appId, applicant))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("APPLICANT");
    }

    @Test
    @DisplayName("Role check: REVIEWER cannot approve an application")
    void approveApplication_byReviewer_throwsAccessDenied() {
        application.setStatus(ApplicationStatus.REVIEWED);
        application.setReviewer(differentReviewer);
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> stateMachineService.approveApplication(appId, reviewer))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Role check: APPROVER cannot submit a new application")
    void submitApplication_byApprover_throwsAccessDenied() {
        application.setStatus(ApplicationStatus.DRAFT);
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> stateMachineService.submitApplication(appId, approver))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ═══════════════════════════════════════════════════════════════════
    // 6. CONCURRENT ACCESS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Concurrent access: second user gets OptimisticLockException when version conflicts")
    void startReview_concurrentAccess_throwsOptimisticLockException() {
        application.setStatus(ApplicationStatus.SUBMITTED);
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(application));

        // Simulate: first reviewer's save succeeds, but when second reviewer
        // tries to save, the version has already changed — JPA throws this
        when(applicationRepository.save(any(Application.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Application.class, appId));

        assertThatThrownBy(() -> stateMachineService.startReview(appId, reviewer))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        // GlobalExceptionHandler catches this and returns HTTP 409
        // Verified separately in controller tests
    }

    // ═══════════════════════════════════════════════════════════════════
    // 7. EDGE CASES
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Edge case: rejection without reason throws IllegalArgumentException")
    void rejectApplication_noReason_throwsIllegalArgument() {
        application.setStatus(ApplicationStatus.REVIEWED);
        application.setReviewer(reviewer);

        assertThatThrownBy(() ->
                stateMachineService.rejectApplication(appId, approver, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rejection reason is mandatory");
    }

    @Test
    @DisplayName("Edge case: rejection with null reason throws IllegalArgumentException")
    void rejectApplication_nullReason_throwsIllegalArgument() {
        application.setStatus(ApplicationStatus.REVIEWED);
        application.setReviewer(reviewer);

        assertThatThrownBy(() ->
                stateMachineService.rejectApplication(appId, approver, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Edge case: different applicant cannot resubmit someone else's application")
    void resubmitApplication_wrongApplicant_throwsAccessDenied() {
        User otherApplicant = User.builder()
                .id(UUID.randomUUID())
                .email("other@test.com")
                .role(UserRole.APPLICANT)
                .active(true)
                .build();

        application.setStatus(ApplicationStatus.ADDITIONAL_INFO_REQUESTED);
        application.setReviewer(reviewer);
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> stateMachineService.resubmitApplication(appId, otherApplicant))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Edge case: application not found throws ResourceNotFoundException")
    void submitApplication_applicationNotFound_throwsResourceNotFound() {
        when(applicationRepository.findById(appId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stateMachineService.submitApplication(appId, applicant))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(appId.toString());
    }

    @Test
    @DisplayName("Edge case: non-assigned reviewer cannot request additional info")
    void requestAdditionalInfo_wrongReviewer_throwsAccessDenied() {
        application.setStatus(ApplicationStatus.UNDER_REVIEW);
        application.setReviewer(reviewer); // reviewer is assigned
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(application));

        // differentReviewer tries to act on someone else's assigned application
        assertThatThrownBy(() ->
                stateMachineService.requestAdditionalInfo(
                        appId, differentReviewer, "Some request"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("assigned reviewer");
    }
}
