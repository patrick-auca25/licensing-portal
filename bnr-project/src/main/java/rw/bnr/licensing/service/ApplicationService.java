package rw.bnr.licensing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.bnr.licensing.audit.AuditService;
import rw.bnr.licensing.dto.ApplicationRequest;
import rw.bnr.licensing.dto.ApplicationResponse;
import rw.bnr.licensing.enums.ApplicationStatus;
import rw.bnr.licensing.enums.UserRole;
import rw.bnr.licensing.exception.ResourceNotFoundException;
import rw.bnr.licensing.model.Application;
import rw.bnr.licensing.model.User;
import rw.bnr.licensing.repository.ApplicationRepository;
import rw.bnr.licensing.repository.UserRepository;

import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * ApplicationService — handles CRUD operations for applications.
 *
 * Workflow transitions (submit, review, approve etc.) are handled
 * by StateMachineService — this service handles:
 * - Creating new DRAFT applications
 * - Reading applications (role-filtered)
 * - Updating draft application details
 * - Generating unique reference numbers
 * - Mapping entities to response DTOs
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    // ── Create new DRAFT application ─────────────────────────────────────
    @Transactional
    public ApplicationResponse createApplication(ApplicationRequest request, User applicant) {
        Application application = Application.builder()
                .referenceNumber(generateReferenceNumber())
                .applicant(applicant)
                .institutionName(request.getInstitutionName())
                .institutionType(request.getInstitutionType())
                .businessPlan(request.getBusinessPlan())
                .registeredCapital(request.getRegisteredCapital())
                .status(ApplicationStatus.DRAFT)
                .build();

        Application saved = applicationRepository.save(application);

        auditService.logTransition(applicant, saved, null,
                ApplicationStatus.DRAFT,
                "{\"action\":\"Application created as DRAFT\"}");

        log.info("Application created: {} by {}", saved.getReferenceNumber(), applicant.getEmail());
        return toResponse(saved);
    }

    // ── Update DRAFT application details ─────────────────────────────────
    @Transactional
    public ApplicationResponse updateApplication(UUID id, ApplicationRequest request, User actor) {
        Application app = applicationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + id));

        // Only DRAFT applications can be edited
        if (app.getStatus() != ApplicationStatus.DRAFT) {
            throw new IllegalStateException(
                    "Only DRAFT applications can be edited. Current status: " + app.getStatus());
        }

        // Only the applicant who owns it can edit
        if (!app.getApplicant().getId().equals(actor.getId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You can only edit your own applications.");
        }

        app.setInstitutionName(request.getInstitutionName());
        app.setInstitutionType(request.getInstitutionType());
        app.setBusinessPlan(request.getBusinessPlan());
        app.setRegisteredCapital(request.getRegisteredCapital());

        return toResponse(applicationRepository.save(app));
    }

    // ── Get single application ────────────────────────────────────────────
    @Transactional(readOnly = true)
    public ApplicationResponse getApplication(UUID id, User actor) {
        Application app = applicationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + id));

        assertCanView(app, actor);
        return toResponse(app);
    }

    // ── Get all applications — filtered by role ───────────────────────────
    @Transactional(readOnly = true)
    public List<ApplicationResponse> getApplicationsForUser(User actor) {
        List<Application> applications;

        switch (actor.getRole()) {
            case APPLICANT ->
                // Applicants see only their own
                applications = applicationRepository.findByApplicantOrderByCreatedAtDesc(actor);

            case REVIEWER ->
                applications = applicationRepository.findForReviewer();

            case APPROVER ->
                applications = applicationRepository.findForApprover();

            case ADMIN ->
                // Admin sees everything
                applications = applicationRepository.findAllByOrderByCreatedAtDesc();

            default -> applications = List.of();
        }

        return applications.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Visibility check ─────────────────────────────────────────────────
    private void assertCanView(Application app, User actor) {
        if (actor.getRole() == UserRole.ADMIN) return;
        if (actor.getRole() == UserRole.APPLICANT &&
                !app.getApplicant().getId().equals(actor.getId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You do not have permission to view this application.");
        }
    }

    // ── Reference number generator ───────────────────────────────────────
    // Format: BNR-YYYY-XXXX (e.g. BNR-2026-0042)
    private String generateReferenceNumber() {
        int year = LocalDateTime.now().getYear();
        long count = applicationRepository.count() + 1;
        String candidate = String.format("BNR-%d-%04d", year, count);

        // Ensure uniqueness
        while (applicationRepository.existsByReferenceNumber(candidate)) {
            count++;
            candidate = String.format("BNR-%d-%04d", year, count);
        }
        return candidate;
    }

    // ── Entity → DTO mapping ─────────────────────────────────────────────
    public ApplicationResponse toResponse(Application app) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        return ApplicationResponse.builder()
                .id(app.getId())
                .referenceNumber(app.getReferenceNumber())
                .institutionName(app.getInstitutionName())
                .institutionType(app.getInstitutionType())
                .businessPlan(app.getBusinessPlan())
                .registeredCapital(app.getRegisteredCapital())
                .status(app.getStatus().name())
                .statusCode(app.getStatus().getCode())
                .statusDescription(app.getStatus().getDescription())
                .version(app.getVersion())
                .applicantName(app.getApplicant().getFullName())
                .applicantEmail(app.getApplicant().getEmail())
                .reviewerName(app.getReviewer() != null ? app.getReviewer().getFullName() : null)
                .approverName(app.getApprover() != null ? app.getApprover().getFullName() : null)
                .rejectionReason(app.getRejectionReason())
                .reviewerNotes(app.getReviewerNotes())
                .additionalInfoRequest(app.getAdditionalInfoRequest())
                .submittedAt(app.getSubmittedAt() != null ? app.getSubmittedAt().format(fmt) : null)
                .decidedAt(app.getDecidedAt() != null ? app.getDecidedAt().format(fmt) : null)
                .createdAt(app.getCreatedAt() != null ? app.getCreatedAt().format(fmt) : null)
                .build();
    }
}