package org.booklore.service.upload;

import org.booklore.config.AppProperties;
import org.booklore.exception.ApiError;
import org.booklore.mapper.AdditionalFileMapper;
import org.booklore.model.dto.BookFile;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.BookFileExtension;
import org.booklore.repository.BookAdditionalFileRepository;
import org.booklore.repository.BookRepository;
import org.booklore.repository.LibraryRepository;
import org.booklore.service.file.FileFingerprint;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.file.FileMovingHelper;
import org.booklore.service.monitoring.MonitoringRegistrationService;
import org.booklore.service.metadata.extractor.MetadataExtractorFactory;
import org.booklore.util.PathPatternResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Optional;
import org.booklore.model.enums.AuditAction;
import org.booklore.service.audit.AuditService;

@RequiredArgsConstructor
@Service
@Slf4j
public class FileUploadService {

    private static final String UPLOAD_TEMP_PREFIX = "upload-";
    private static final String BOOKDROP_TEMP_PREFIX = "bookdrop-";
    private static final long BYTES_TO_KB_DIVISOR = 1024L;
    private static final long MB_TO_BYTES_MULTIPLIER = 1024L * 1024L;

    private final LibraryRepository libraryRepository;
    private final BookRepository bookRepository;
    private final BookAdditionalFileRepository additionalFileRepository;
    private final AppSettingService appSettingService;
    private final AppProperties appProperties;
    private final MetadataExtractorFactory metadataExtractorFactory;
    private final AdditionalFileMapper additionalFileMapper;
    private final FileMovingHelper fileMovingHelper;
    private final MonitoringRegistrationService monitoringRegistrationService;
    private final AuditService auditService;

    public void uploadFile(MultipartFile file, long libraryId, long pathId) {
        validateFile(file);

        final LibraryEntity libraryEntity = findLibraryById(libraryId);
        final LibraryPathEntity libraryPathEntity = findLibraryPathById(libraryEntity, pathId);
        final String originalFileName = getValidatedFileName(file);
        final BookFileExtension fileExtension = getFileExtension(originalFileName);
        validateAllowedFormat(libraryEntity, fileExtension.getType());

        Path tempPath = null;
        try {
            tempPath = createTempFile(UPLOAD_TEMP_PREFIX, originalFileName);
            file.transferTo(tempPath);
            final BookMetadata metadata = extractMetadata(fileExtension, tempPath.toFile(), originalFileName);
            final String uploadPattern = fileMovingHelper.getFileNamingPattern(libraryEntity);

            final String relativePath = PathPatternResolver.resolvePattern(metadata, uploadPattern, originalFileName);
            final Path finalPath = Paths.get(libraryPathEntity.getPath(), relativePath);

            validateFinalPath(finalPath);
            moveFileToFinalLocation(tempPath, finalPath);

            log.info("File uploaded to final location: {}", finalPath);
            auditService.log(AuditAction.BOOK_UPLOADED, "Library", libraryId, "Uploaded file: " + originalFileName);

        } catch (IOException e) {
            log.error("Failed to upload file: {}", originalFileName, e);
            throw ApiError.FILE_READ_ERROR.createException(e.getMessage());
        } finally {
            cleanupTempFile(tempPath);
        }
    }

    @Transactional
    public BookFile uploadAdditionalFile(Long bookId, MultipartFile file, boolean isBook, BookFileType bookType, String description) {
        final BookEntity book = findBookById(bookId);
        final String originalFileName = getValidatedFileName(file);
        final Long libraryId = book.getLibrary() != null ? book.getLibrary().getId() : null;
        final String sanitizedFileName = PathPatternResolver.truncateFilenameWithExtension(originalFileName);
        final boolean wasPhysicalBook = Boolean.TRUE.equals(book.getIsPhysical());

        Path tempPath = null;
        boolean monitoringUnregistered = false;
        try {
            tempPath = createTempFile(UPLOAD_TEMP_PREFIX, sanitizedFileName);
            file.transferTo(tempPath);

            final String fileHash = FileFingerprint.generateHash(tempPath);
            if (isBook) {
                validateAlternativeFormatDuplicate(fileHash);
            }

            final Path finalPath;
            final String finalFileName;
            final String fileSubPath;
            final BookFileType effectiveBookType;

            // Handle physical books that are getting their first file
            if (wasPhysicalBook) {
                // Physical book - determine library path and subpath
                LibraryPathEntity libraryPath = determineLibraryPathForPhysicalBook(book);
                book.setLibraryPath(libraryPath);

                String pattern = fileMovingHelper.getFileNamingPattern(book.getLibrary());
                String resolvedRelativePath = PathPatternResolver.resolvePattern(book.getMetadata(), pattern, sanitizedFileName);
                finalFileName = Paths.get(resolvedRelativePath).getFileName().toString();
                fileSubPath = Paths.get(resolvedRelativePath).getParent() != null
                        ? Paths.get(resolvedRelativePath).getParent().toString()
                        : "";
                finalPath = Paths.get(libraryPath.getPath(), resolvedRelativePath);
                String extension = sanitizedFileName.substring(sanitizedFileName.lastIndexOf('.') + 1);
                effectiveBookType = BookFileType.fromExtension(extension)
                        .orElseThrow(() -> ApiError.INVALID_FILE_FORMAT.createException("Unsupported book file extension: " + extension));
            } else if (isBook) {
                String pattern = fileMovingHelper.getFileNamingPattern(book.getLibrary());
                String resolvedRelativePath = PathPatternResolver.resolvePattern(book.getMetadata(), pattern, sanitizedFileName);
                finalFileName = Paths.get(resolvedRelativePath).getFileName().toString();
                fileSubPath = book.getPrimaryBookFile().getFileSubPath();
                finalPath = buildAdditionalFilePath(book, finalFileName);
                String extension = sanitizedFileName.substring(sanitizedFileName.lastIndexOf('.') + 1);
                effectiveBookType = BookFileType.fromExtension(extension)
                        .orElseThrow(() -> ApiError.INVALID_FILE_FORMAT.createException("Unsupported book file extension: " + extension));
            } else {
                finalFileName = sanitizedFileName;
                fileSubPath = book.getPrimaryBookFile().getFileSubPath();
                finalPath = buildAdditionalFilePath(book, sanitizedFileName);
                effectiveBookType = bookType;
            }
            validateFinalPath(finalPath);

            if (libraryId != null) {
                log.debug("Unregistering library {} for monitoring", libraryId);
                monitoringRegistrationService.unregisterLibrary(libraryId);
                monitoringUnregistered = true;
            }
            moveFileToFinalLocation(tempPath, finalPath);

            log.info("Additional file uploaded to final location: {}", finalPath);

            final BookFileEntity entity = createAdditionalFileEntityWithSubPath(book, finalFileName, fileSubPath, isBook, effectiveBookType, file.getSize(), fileHash, description);
            final BookFileEntity savedEntity = additionalFileRepository.save(entity);

            // Promote physical book to digital if this is a book file
            if (wasPhysicalBook && isBook) {
                book.setIsPhysical(false);
                bookRepository.save(book);
                log.info("Physical book {} promoted to digital book after file upload", bookId);
            }

            return additionalFileMapper.toAdditionalFile(savedEntity);

        } catch (IOException e) {
            log.error("Failed to upload additional file for book {}: {}", bookId, sanitizedFileName, e);
            throw ApiError.FILE_READ_ERROR.createException(e.getMessage());
        } finally {
            if (monitoringUnregistered) {
                try {
                    if (book.getLibrary() != null && book.getLibrary().getLibraryPaths() != null) {
                        for (LibraryPathEntity libPath : book.getLibrary().getLibraryPaths()) {
                            Path libraryRoot = Path.of(libPath.getPath());
                            log.debug("Re-registering library {} for monitoring", libraryId);
                            monitoringRegistrationService.registerLibraryPaths(libraryId, libraryRoot);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to re-register library {} for monitoring after additional file upload: {}", libraryId, e.getMessage());
                }
            }
            cleanupTempFile(tempPath);
        }
    }

    private LibraryPathEntity determineLibraryPathForPhysicalBook(BookEntity book) {
        if (book.getLibrary() == null || book.getLibrary().getLibraryPaths() == null || book.getLibrary().getLibraryPaths().isEmpty()) {
            throw new IllegalStateException("Cannot upload file to physical book: library has no paths configured");
        }
        // Use the first library path for physical books
        return book.getLibrary().getLibraryPaths().iterator().next();
    }

    private BookFileEntity createAdditionalFileEntityWithSubPath(BookEntity book, String fileName, String fileSubPath, boolean isBook, BookFileType bookType, long fileSize, String fileHash, String description) {
        return BookFileEntity.builder()
                .book(book)
                .fileName(fileName)
                .fileSubPath(fileSubPath)
                .isBookFormat(isBook)
                .bookType(bookType)
                .fileSizeKb(fileSize / BYTES_TO_KB_DIVISOR)
                .initialHash(fileHash)
                .currentHash(fileHash)
                .description(description)
                .addedOn(Instant.now())
                .build();
    }

    public Book uploadFileBookDrop(MultipartFile file) throws IOException {
        validateFile(file);

        final Path dropFolder = Paths.get(appProperties.getBookdropFolder());
        Files.createDirectories(dropFolder);

        final String originalFilename = getValidatedFileName(file);
        final String sanitizedFilename = PathPatternResolver.truncateFilenameWithExtension(originalFilename);
        Path tempPath = null;

        try {
            tempPath = createTempFile(BOOKDROP_TEMP_PREFIX, sanitizedFilename);
            file.transferTo(tempPath);

            final Path finalPath = dropFolder.resolve(sanitizedFilename);
            validateFinalPath(finalPath);
            Files.move(tempPath, finalPath);

            log.info("File moved to book-drop folder: {}", finalPath);
            return null;

        } finally {
            cleanupTempFile(tempPath);
        }
    }

    private LibraryEntity findLibraryById(long libraryId) {
        return libraryRepository.findById(libraryId)
                .orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(libraryId));
    }

    private LibraryPathEntity findLibraryPathById(LibraryEntity libraryEntity, long pathId) {
        return libraryEntity.getLibraryPaths()
                .stream()
                .filter(p -> p.getId() == pathId)
                .findFirst()
                .orElseThrow(() -> ApiError.INVALID_LIBRARY_PATH.createException(libraryEntity.getId()));
    }

    private BookEntity findBookById(Long bookId) {
        return bookRepository.findById(bookId)
                .orElseThrow(() -> new IllegalArgumentException("Book not found with id: " + bookId));
    }

    private String getValidatedFileName(MultipartFile file) {
        final String originalFileName = file.getOriginalFilename();
        if (originalFileName == null) {
            throw new IllegalArgumentException("File must have a name");
        }
        return originalFileName;
    }

    private BookFileExtension getFileExtension(String fileName) {
        return BookFileExtension.fromFileName(fileName)
                .orElseThrow(() -> ApiError.INVALID_FILE_FORMAT.createException("Unsupported file extension"));
    }

    private Path createTempFile(String prefix, String fileName) throws IOException {
        String suffix = "";
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex >= 0) {
            suffix = fileName.substring(lastDotIndex);
        }
        return Files.createTempFile(prefix, suffix);
    }

    private void validateFinalPath(Path finalPath) {
        if (Files.exists(finalPath)) {
            throw ApiError.FILE_ALREADY_EXISTS.createException();
        }
    }

    private void moveFileToFinalLocation(Path sourcePath, Path targetPath) throws IOException {
        Files.createDirectories(targetPath.getParent());
        Files.move(sourcePath, targetPath);
    }

    private void validateAlternativeFormatDuplicate(String fileHash) {
        final Optional<BookFileEntity> existingAltFormat = additionalFileRepository.findByAltFormatCurrentHash(fileHash);
        if (existingAltFormat.isPresent()) {
            throw new IllegalArgumentException("Alternative format file already exists with same content");
        }
    }

    private Path buildAdditionalFilePath(BookEntity book, String fileName) {
        final BookFileEntity primaryFile = book.getPrimaryBookFile();
        return Paths.get(book.getLibraryPath().getPath(), primaryFile.getFileSubPath(), fileName);
    }

    private BookFileEntity createAdditionalFileEntity(BookEntity book, String fileName, boolean isBook, BookFileType bookType, long fileSize, String fileHash, String description) {
        final BookFileEntity primaryFile = book.getPrimaryBookFile();
        return BookFileEntity.builder()
                .book(book)
                .fileName(fileName)
                .fileSubPath(primaryFile.getFileSubPath())
                .isBookFormat(isBook)
                .bookType(bookType)
                .fileSizeKb(fileSize / BYTES_TO_KB_DIVISOR)
                .initialHash(fileHash)
                .currentHash(fileHash)
                .description(description)
                .addedOn(Instant.now())
                .build();
    }

    private void cleanupTempFile(Path tempPath) {
        if (tempPath != null) {
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException e) {
                log.warn("Failed to cleanup temp file: {}", tempPath, e);
            }
        }
    }

    private BookMetadata extractMetadata(BookFileExtension fileExt, File file, String originalFileName) {
        BookMetadata metadata = metadataExtractorFactory.extractMetadata(fileExt, file);

        // If the metadata title is the same as the temporary file's base name (which happens
        // when CBX files have no embedded metadata), use the original filename as the title instead
        String tempFileBaseName = java.nio.file.Paths.get(file.getName()).getFileName().toString();
        int lastDotIndex = tempFileBaseName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            tempFileBaseName = tempFileBaseName.substring(0, lastDotIndex);
        }

        String originalFileBaseName = originalFileName;
        lastDotIndex = originalFileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            originalFileBaseName = originalFileName.substring(0, lastDotIndex);
        }

        if (metadata.getTitle() != null && (metadata.getTitle().equals(tempFileBaseName) || metadata.getTitle().startsWith(UPLOAD_TEMP_PREFIX))) {
            metadata.setTitle(originalFileBaseName);
        }

        return metadata;
    }

    private void validateFile(MultipartFile file) {
        final String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || BookFileExtension.fromFileName(originalFilename).isEmpty()) {
            throw ApiError.INVALID_FILE_FORMAT.createException("Unsupported file extension");
        }

        final int maxSizeMb = appSettingService.getAppSettings().getMaxFileUploadSizeInMb();
        if (file.getSize() > maxSizeMb * MB_TO_BYTES_MULTIPLIER) {
            throw ApiError.FILE_TOO_LARGE.createException(maxSizeMb);
        }
    }

    private void validateAllowedFormat(LibraryEntity library, BookFileType fileType) {
        var allowedFormats = library.getAllowedFormats();
        if (allowedFormats != null && !allowedFormats.isEmpty() && !allowedFormats.contains(fileType)) {
            throw ApiError.FORMAT_NOT_ALLOWED.createException(fileType.name(), library.getName());
        }
    }
}
