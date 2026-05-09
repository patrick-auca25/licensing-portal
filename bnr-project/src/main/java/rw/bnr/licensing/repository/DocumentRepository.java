package rw.bnr.licensing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.bnr.licensing.model.Document;

import java.util.List;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    // All documents for an application (including old versions)
    List<Document> findByApplicationIdOrderByUploadedAtDesc(UUID applicationId);

    // Only current versions
    List<Document> findByApplicationIdAndCurrentTrueOrderByUploadedAtDesc(UUID applicationId);

    // Latest version number for a document category on an application
    @Query("SELECT COALESCE(MAX(d.versionNumber), 0) FROM Document d " +
           "WHERE d.application.id = :appId AND d.documentCategory = :category")
    int findMaxVersionNumber(@Param("appId") UUID appId,
                             @Param("category") String category);

    // Mark all previous versions as not current before saving new one
    @Modifying
    @Query("UPDATE Document d SET d.current = false " +
           "WHERE d.application.id = :appId AND d.documentCategory = :category")
    void markAllAsNotCurrent(@Param("appId") UUID appId,
                              @Param("category") String category);
}
