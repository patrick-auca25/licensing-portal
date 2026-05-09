package rw.bnr.licensing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import rw.bnr.licensing.audit.AuditService;
import rw.bnr.licensing.dto.DocumentResponse;
import rw.bnr.licensing.enums.ApplicationStatus;
import rw.bnr.licensing.exception.ResourceNotFoundException;
import rw.bnr.licensing.model.Application;
import rw.bnr.licensing.model.Document;
import rw.bnr.licensing.model.User;
import rw.bnr.licensing.repository.ApplicationRepository;
import rw.bnr.licensing.repository.DocumentRepository;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * DocumentService — handles file upload with:
 * 1. Server-side 5MB size enforcement
 * 2. Allowed file type validation
 * 3. Document versioning (previous versions preserved)
 * 4. Secure file storage (outside web root)
 * 5. Audit logging on every upload
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final ApplicationRepository applicationRepository;
    private final AuditService auditService;

    @Value("${app.upload.base-dir}")
    private String uploadBaseDir;

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB in bytes

    private static final List<String> ALLOWED_TYPES = List.of(
            "application/pdf",
            "image/jpeg",
            "image/jpg",
            "image/png"
    );

    // ── Upload document ───────────────────────────────────────────────────
    @Transactional
    public DocumentResponse uploadDocument(UUID applicationId, String category,
                                            MultipartFile file, User uploader) throws IOException {
        // ── 1. Load application ──────────────────────────────────────────
        Application app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Application not found: " + applicationId));

        // ── 2. Validate application is in uploadable state ───────────────
        if (app.getStatus() == ApplicationStatus.APPROVED ||
                app.getStatus() == ApplicationStatus.REJECTED) {
            throw new IllegalStateException(
                    "Documents cannot be uploaded to a finalised application.");
        }

        // ── 3. Server-side file size check (non-negotiable requirement) ──
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "File size " + (file.getSize() / 1024 / 1024) + "MB " +
                    "exceeds the maximum allowed size of 5MB.");
        }

        // ── 4. File type validation ──────────────────────────────────────
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    "File type not allowed. Permitted types: PDF, JPEG, PNG.");
        }

        // ── 5. Get next version number ───────────────────────────────────
        int currentMaxVersion = documentRepository.findMaxVersionNumber(applicationId, category);
        int newVersion = currentMaxVersion + 1;

        // ── 6. Mark all previous versions of this category as not current ─
        if (currentMaxVersion > 0) {
            documentRepository.markAllAsNotCurrent(applicationId, category);
        }

        // ── 7. Store file on disk ────────────────────────────────────────
        String filePath = storeFile(app, file, category, newVersion);

        // ── 8. Save document metadata to DB ─────────────────────────────
        Document document = Document.builder()
                .application(app)
                .fileName(file.getOriginalFilename())
                .filePath(filePath)
                .fileSize(file.getSize())
                .fileType(contentType)
                .uploader(uploader)
                .documentCategory(category)
                .versionNumber(newVersion)
                .current(true)
                .build();

        Document saved = documentRepository.save(document);

        // ── 9. Audit the upload ──────────────────────────────────────────
        auditService.logDocumentUpload(uploader, app,
                file.getOriginalFilename(), newVersion);

        log.info("Document uploaded: {} v{} on application {} by {}",
                file.getOriginalFilename(), newVersion,
                app.getReferenceNumber(), uploader.getEmail());

        return toResponse(saved);
    }

    // ── Get all documents for an application (all versions) ──────────────
    @Transactional(readOnly = true)
    public List<DocumentResponse> getDocuments(UUID applicationId) {
        return documentRepository
                .findByApplicationIdOrderByUploadedAtDesc(applicationId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Get only current documents ────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<DocumentResponse> getCurrentDocuments(UUID applicationId) {
        return documentRepository
                .findByApplicationIdAndCurrentTrueOrderByUploadedAtDesc(applicationId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Store file securely on filesystem ────────────────────────────────
    private String storeFile(Application app, MultipartFile file,
                              String category, int version) throws IOException {
        // Build path: uploads/{appId}/{category}/v{N}_{originalName}
        Path uploadPath = Paths.get(uploadBaseDir,
                app.getId().toString(), category);

        Files.createDirectories(uploadPath);

        String safeFilename = String.format("v%d_%s", version,
                sanitizeFilename(file.getOriginalFilename()));

        Path filePath = uploadPath.resolve(safeFilename);
        Files.copy(file.getInputStream(), filePath,
                StandardCopyOption.REPLACE_EXISTING);

        return filePath.toString();
    }

    // ── Sanitize filename — prevent path traversal attacks ───────────────
    private String sanitizeFilename(String filename) {
        if (filename == null) return "document";
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    // ── Entity → DTO ─────────────────────────────────────────────────────
    private DocumentResponse toResponse(Document doc) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return DocumentResponse.builder()
                .id(doc.getId())
                .fileName(doc.getFileName())
                .fileSize(doc.getFileSize())
                .fileType(doc.getFileType())
                .documentCategory(doc.getDocumentCategory())
                .versionNumber(doc.getVersionNumber())
                .isCurrent(doc.isCurrent())
                .uploaderName(doc.getUploader().getFullName())
                .uploadedAt(doc.getUploadedAt() != null ?
                        doc.getUploadedAt().format(fmt) : null)
                .build();
    }
}
