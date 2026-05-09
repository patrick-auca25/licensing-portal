package rw.bnr.licensing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.bnr.licensing.enums.ApplicationStatus;
import rw.bnr.licensing.model.Application;
import rw.bnr.licensing.model.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    // Applicant sees only their own applications
    List<Application> findByApplicantOrderByCreatedAtDesc(User applicant);

    // Reviewer/Approver sees by status
    List<Application> findByStatusOrderByCreatedAtAsc(ApplicationStatus status);

    // Admin sees all
    List<Application> findAllByOrderByCreatedAtDesc();

    // Reviewer sees what they are assigned to
    List<Application> findByReviewerOrderByCreatedAtDesc(User reviewer);

    // Reference number lookup
    Optional<Application> findByReferenceNumber(String referenceNumber);

    // Check reference uniqueness
    boolean existsByReferenceNumber(String referenceNumber);

    // Applications available for a reviewer to claim (SUBMITTED, not yet assigned)
    @Query("SELECT a FROM Application a WHERE a.status = 'SUBMITTED' " +
           "ORDER BY a.submittedAt ASC")
    List<Application> findSubmittedApplications();

    // Applications ready for final decision (REVIEWED)
    List<Application> findByStatusIn(List<ApplicationStatus> statuses);
}
