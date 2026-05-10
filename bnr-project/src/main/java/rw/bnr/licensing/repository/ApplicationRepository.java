package rw.bnr.licensing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
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

       List<Application> findByStatusInOrderByCreatedAtDesc(List<ApplicationStatus> statuses);

    Optional<Application> findByReferenceNumber(String referenceNumber);

    boolean existsByReferenceNumber(String referenceNumber);

       default List<Application> findForReviewer() {
              return findByStatusInOrderByCreatedAtDesc(List.of(
                            ApplicationStatus.SUBMITTED,
                            ApplicationStatus.UNDER_REVIEW,
                            ApplicationStatus.ADDITIONAL_INFO_REQUESTED,
                            ApplicationStatus.REVIEWED
              ));
       }

       default List<Application> findForApprover() {
              return findByStatusInOrderByCreatedAtDesc(List.of(
                            ApplicationStatus.REVIEWED,
                            ApplicationStatus.APPROVED,
                            ApplicationStatus.REJECTED
              ));
       }
}