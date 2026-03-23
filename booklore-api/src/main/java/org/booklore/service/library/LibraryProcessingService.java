package org.booklore.service.library;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.settings.LibraryFile;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.websocket.LogNotification;
import org.booklore.model.websocket.Topic;
import org.booklore.repository.BookAdditionalFileRepository;
import org.booklore.repository.LibraryRepository;
import org.booklore.service.NotificationService;
import org.booklore.service.file.FileFingerprint;
import org.booklore.task.options.RescanLibraryContext;
import org.booklore.util.FileUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@AllArgsConstructor
@Service
@Slf4j
public class LibraryProcessingService {

    private final LibraryRepository libraryRepository;
    private final NotificationService notificationService;
    private final BookAdditionalFileRepository bookAdditionalFileRepository;
    private final FileAsBookProcessor fileAsBookProcessor;
    private final BookRestorationService bookRestorationService;
    private final BookDeletionService bookDeletionService;
    private final LibraryFileHelper libraryFileHelper;
    private final BookGroupingService bookGroupingService;
    @PersistenceContext
    private final EntityManager entityManager;

    @Transactional
    public void processLibrary(long libraryId) {
        LibraryEntity libraryEntity = libraryRepository.findById(libraryId).orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(libraryId));
        notificationService.sendMessage(Topic.LOG, LogNotification.info("Started processing library: " + libraryEntity.getName()));
        try {
            List<LibraryFile> libraryFiles = libraryFileHelper.getLibraryFiles(libraryEntity);
            List<LibraryFile> newFiles = detectNewBookPaths(libraryFiles, libraryEntity);

            // Use BookGroupingService for consistent grouping based on organization mode
            Map<String, List<LibraryFile>> groups = bookGroupingService.groupForInitialScan(newFiles, libraryEntity);
            fileAsBookProcessor.processLibraryFilesGrouped(groups, libraryEntity);

            notificationService.sendMessage(Topic.LOG, LogNotification.info("Finished processing library: " + libraryEntity.getName()));
        } catch (IOException e) {
            log.error("Failed to process library {}: {}", libraryEntity.getName(), e.getMessage(), e);
            notificationService.sendMessage(Topic.LOG, LogNotification.error("Failed to process library: " + libraryEntity.getName() + " - " + e.getMessage()));
            throw new UncheckedIOException("Library processing failed", e);
        }
    }

    @Transactional
    public void rescanLibrary(RescanLibraryContext context) throws IOException {
        LibraryEntity libraryEntity = libraryRepository.findByIdWithBooks(context.getLibraryId()).orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(context.getLibraryId()));
        notificationService.sendMessage(Topic.LOG, LogNotification.info("Started refreshing library: " + libraryEntity.getName()));

        validateLibraryPathsAccessible(libraryEntity);

        List<LibraryFile> allLibraryFiles = libraryFileHelper.getAllLibraryFiles(libraryEntity);
        List<LibraryFile> filteredFiles = libraryFileHelper.filterByAllowedFormats(
                allLibraryFiles, libraryEntity.getAllowedFormats());

        int existingBookCount = libraryEntity.getBookEntities().size();
        if (existingBookCount > 0 && allLibraryFiles.isEmpty()) {
            String paths = libraryEntity.getLibraryPaths().stream()
                    .map(LibraryPathEntity::getPath)
                    .collect(Collectors.joining(", "));
            log.error("Library '{}' has {} existing books but scan found 0 files. Paths may be offline: {}",
                    libraryEntity.getName(), existingBookCount, paths);
            throw ApiError.LIBRARY_PATH_NOT_ACCESSIBLE.createException(paths);
        }

        List<Long> additionalFileIds = detectDeletedAdditionalFiles(allLibraryFiles, libraryEntity);
        if (!additionalFileIds.isEmpty()) {
            log.info("Detected {} removed additional files in library: {}", additionalFileIds.size(), libraryEntity.getName());
            bookDeletionService.deleteRemovedAdditionalFiles(additionalFileIds);
        }
        List<Long> bookIds = detectDeletedBookIds(allLibraryFiles, libraryEntity);
        if (!bookIds.isEmpty()) {
            log.info("Detected {} removed books in library: {}", bookIds.size(), libraryEntity.getName());
            bookDeletionService.processDeletedLibraryFiles(bookIds, allLibraryFiles);
        }
        bookRestorationService.restoreDeletedBooks(allLibraryFiles);
        bookDeletionService.purgeDisallowedFormats(libraryEntity);
        entityManager.clear();
        // Re-fetch library entity with books to get fresh state after entity manager was cleared
        libraryEntity = libraryRepository.findByIdWithBooks(context.getLibraryId())
                .orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(context.getLibraryId()));

        List<LibraryFile> newFiles = detectNewBookPaths(filteredFiles, libraryEntity);

        // Use BookGroupingService to determine what to attach vs create new
        BookGroupingService.GroupingResult groupingResult = bookGroupingService.groupForRescan(newFiles, libraryEntity);

        // Auto-attach files to existing books
        for (Map.Entry<BookEntity, List<LibraryFile>> entry : groupingResult.filesToAttach().entrySet()) {
            for (LibraryFile file : entry.getValue()) {
                autoAttachFile(entry.getKey(), file);
            }
        }

        // Process new book groups
        fileAsBookProcessor.processLibraryFilesGrouped(groupingResult.newBookGroups(), libraryEntity);

        notificationService.sendMessage(Topic.LOG, LogNotification.info("Finished refreshing library: " + libraryEntity.getName()));
    }

    public void processLibraryFiles(List<LibraryFile> libraryFiles, LibraryEntity libraryEntity) {
        fileAsBookProcessor.processLibraryFiles(libraryFiles, libraryEntity);
    }

    private void validateLibraryPathsAccessible(LibraryEntity libraryEntity) {
        for (var pathEntity : libraryEntity.getLibraryPaths()) {
            Path path = Path.of(pathEntity.getPath());
            if (!Files.exists(path) || !Files.isDirectory(path) || !Files.isReadable(path)) {
                log.error("Library path not accessible: {}", path);
                throw ApiError.LIBRARY_PATH_NOT_ACCESSIBLE.createException(path.toString());
            }
        }
    }

    protected static List<Long> detectDeletedBookIds(List<LibraryFile> libraryFiles, LibraryEntity libraryEntity) {
        Set<Path> currentFullPaths = libraryFiles.stream()
                .map(LibraryFile::getFullPath)
                .collect(Collectors.toSet());

        return libraryEntity.getBookEntities().stream()
                .filter(book -> (book.getDeleted() == null || !book.getDeleted()))
                .filter(book -> {
                    // Don't mark fileless books as deleted - they're intentionally without files
                    if (!book.hasFiles()) {
                        return false;
                    }
                    return !currentFullPaths.contains(book.getFullFilePath());
                })
                .map(BookEntity::getId)
                .collect(Collectors.toList());
    }

    protected List<LibraryFile> detectNewBookPaths(List<LibraryFile> libraryFiles, LibraryEntity libraryEntity) {
        Set<String> existingKeys = libraryEntity.getBookEntities().stream()
                .filter(book -> book.getBookFiles() != null && !book.getBookFiles().isEmpty())
                .map(this::generateUniqueKey)
                .collect(Collectors.toSet());

        Set<String> additionalFileKeys = bookAdditionalFileRepository.findByLibraryId(libraryEntity.getId()).stream()
                .map(this::generateUniqueKey)
                .collect(Collectors.toSet());

        existingKeys.addAll(additionalFileKeys);

        return libraryFiles.stream()
                .filter(file -> !existingKeys.contains(generateUniqueKey(file)))
                .collect(Collectors.toList());
    }

    private void autoAttachFile(BookEntity book, LibraryFile file) {
        // Check if file already exists to prevent duplicates during concurrent rescans
        var existing = bookAdditionalFileRepository.findByLibraryPath_IdAndFileSubPathAndFileName(
                file.getLibraryPathEntity().getId(), file.getFileSubPath(), file.getFileName());
        if (existing.isPresent()) {
            log.debug("Additional file already exists, skipping: {}", file.getFileName());
            return;
        }

        // Set libraryPath if not set (fileless books like physical books don't have one)
        if (book.getLibraryPath() == null) {
            book.setLibraryPath(file.getLibraryPathEntity());
        } else if (!book.getLibraryPath().getId().equals(file.getLibraryPathEntity().getId())) {
            // Book already has a different libraryPath - cannot attach files from different paths
            log.warn("Cannot attach file '{}' to book id={}: file is in libraryPath {} but book is in libraryPath {}",
                    file.getFileName(), book.getId(), file.getLibraryPathEntity().getId(), book.getLibraryPath().getId());
            return;
        }

        String hash = file.isFolderBased()
                ? FileFingerprint.generateFolderHash(file.getFullPath())
                : FileFingerprint.generateHash(file.getFullPath());
        Long fileSizeKb = file.isFolderBased()
                ? FileUtils.getFolderSizeInKb(file.getFullPath())
                : FileUtils.getFileSizeInKb(file.getFullPath());
        BookFileEntity additionalFile = BookFileEntity.builder()
                .book(book)
                .fileName(file.getFileName())
                .fileSubPath(file.getFileSubPath())
                .isBookFormat(true)
                .bookType(file.getBookFileType())
                .folderBased(file.isFolderBased())
                .fileSizeKb(fileSizeKb)
                .initialHash(hash)
                .currentHash(hash)
                .addedOn(Instant.now())
                .build();

        try {
            bookAdditionalFileRepository.save(additionalFile);
            String primaryFileName = book.hasFiles() ? book.getPrimaryBookFile().getFileName() : "book#" + book.getId();
            log.info("Auto-attached new format {} to existing book: {}", file.getFileName(), primaryFileName);
            fileAsBookProcessor.generateCoverFromAdditionalFile(book, file);
        } catch (Exception e) {
            log.error("Error auto-attaching file {}: {}", file.getFileName(), e.getMessage());
        }
    }

    private String generateUniqueKey(BookEntity book) {
        BookFileEntity primaryFile = book.getPrimaryBookFile();
        if (primaryFile == null) {
            // Fileless book - use a unique key that won't match any file
            return "fileless:" + book.getId();
        }
        return generateKey(book.getLibraryPath().getId(), primaryFile.getFileSubPath(), primaryFile.getFileName());
    }

    private String generateUniqueKey(BookFileEntity file) {
        return generateKey(file.getBook().getLibraryPath().getId(), file.getFileSubPath(), file.getFileName());
    }

    private String generateUniqueKey(LibraryFile file) {
        return generateKey(file.getLibraryPathEntity().getId(), file.getFileSubPath(), file.getFileName());
    }

    private String generateKey(Long libraryPathId, String subPath, String fileName) {
        String safeSubPath = (subPath == null) ? "" : subPath;
        return libraryPathId + ":" + safeSubPath + ":" + fileName;
    }

    protected List<Long> detectDeletedAdditionalFiles(List<LibraryFile> libraryFiles, LibraryEntity libraryEntity) {
        Set<String> currentFileKeys = libraryFiles.stream()
                .map(this::generateUniqueKey)
                .collect(Collectors.toSet());

        List<BookFileEntity> allAdditionalFiles = bookAdditionalFileRepository.findByLibraryId(libraryEntity.getId());

        return allAdditionalFiles.stream()
                .filter(BookFileEntity::isBookFormat)
                .filter(additionalFile -> !currentFileKeys.contains(generateUniqueKey(additionalFile)))
                .map(BookFileEntity::getId)
                .collect(Collectors.toList());
    }
}
