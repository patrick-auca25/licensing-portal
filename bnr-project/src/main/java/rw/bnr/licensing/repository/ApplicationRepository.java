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

    List<Application> findByApplicantOrderByCreatedAtDesc(User applicant);

    List<Application> findAllByOrderByCreatedAtDesc();

    List<Application> findByReviewerOrderByCreatedAtDesc(User reviewer);

    Optional<Application> findByReferenceNumber(String referenceNumber);

    boolean existsByReferenceNumber(String referenceNumber);

    // Separate queries per status group — avoids PostgreSQL ENUM IN() cast issue
    @Query("SELECT a FROM Application a WHERE a.status IN " +
           "('SUBMITTED', 'UNDER_REVIEW', 'ADDITIONAL_INFO_REQUESTED', 'REVIEWED') " +
           "ORDER BY a.createdAt DESC")
    List<Application> findForReviewer();

    @Query("SELECT a FROM Application a WHERE a.status IN " +
           "('REVIEWED', 'APPROVED', 'REJECTED') " +
           "ORDER BY a.createdAt DESC")
    List<Application> findForApprover();
}