package org.booklore.service.file;

import jakarta.persistence.EntityManager;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.config.AppProperties;
import org.booklore.mapper.BookMapper;
import org.booklore.mapper.LibraryMapper;
import org.booklore.model.dto.FileMoveResult;
import org.booklore.model.dto.Library;
import org.booklore.model.dto.request.FileMoveRequest;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.websocket.Topic;
import org.booklore.repository.BookAdditionalFileRepository;
import org.booklore.repository.BookRepository;
import org.booklore.repository.LibraryRepository;
import org.booklore.service.NotificationService;
import org.booklore.service.monitoring.MonitoringRegistrationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@AllArgsConstructor
@Service
@Slf4j
public class FileMoveService {

    private static final long EVENT_DRAIN_TIMEOUT_MS = 300;

    private final AppProperties appProperties;
    private final BookRepository bookRepository;
    private final BookAdditionalFileRepository bookFileRepository;
    private final LibraryRepository libraryRepository;
    private final FileMoveHelper fileMoveHelper;
    private final MonitoringRegistrationService monitoringRegistrationService;
    private final LibraryMapper libraryMapper;
    private final BookMapper bookMapper;
    private final NotificationService notificationService;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;


    public void bulkMoveFiles(FileMoveRequest request) {
        List<FileMoveRequest.Move> moves = request.getMoves();

        validateLocalStorage();

        Set<Long> allAffectedLibraryIds = collectAllAffectedLibraryIds(moves);
        Set<Path> libraryPaths = monitoringRegistrationService.getPathsForLibraries(allAffectedLibraryIds);

        log.info("Unregistering {} libraries before bulk file move", allAffectedLibraryIds.size());
        monitoringRegistrationService.unregisterLibraries(allAffectedLibraryIds);
        monitoringRegistrationService.waitForEventsDrainedByPaths(libraryPaths, EVENT_DRAIN_TIMEOUT_MS);

        try {
            for (FileMoveRequest.Move move : moves) {
                processSingleMove(move);
            }
            // Ensure any file system events from the moves are drained/ignored while we are still unregistered
            sleep(EVENT_DRAIN_TIMEOUT_MS);
        } finally {
            for (Long libraryId : allAffectedLibraryIds) {
                libraryRepository.findById(libraryId)
                        .ifPresent(library -> monitoringRegistrationService.registerLibrary(libraryMapper.toLibrary(library)));
            }
        }
    }

    private Set<Long> collectAllAffectedLibraryIds(List<FileMoveRequest.Move> moves) {
        Set<Long> libraryIds = new HashSet<>();
        
        for (FileMoveRequest.Move move : moves) {
            libraryIds.add(move.getTargetLibraryId());
            bookRepository.findById(move.getBookId())
                    .ifPresent(book -> libraryIds.add(book.getLibrary().getId()));
        }
        
        return libraryIds;
    }

    private void processSingleMove(FileMoveRequest.Move move) {
        Long bookId = move.getBookId();
        Long targetLibraryId = move.getTargetLibraryId();
        Long targetLibraryPathId = move.getTargetLibraryPathId();

        record PlannedMove(Path source, Path temp, Path target) {}

        Map<Long, PlannedMove> plannedMovesByBookFileId = new HashMap<>();
        Set<Path> sourceParentsToCleanup = new HashSet<>();

        try {
            Optional<BookEntity> optionalBook = bookRepository.findByIdWithBookFiles(bookId);
            Optional<LibraryEntity> optionalLibrary = libraryRepository.findById(targetLibraryId);
            if (optionalBook.isEmpty()) {
                log.warn("Book not found for move operation: bookId={}", bookId);
                return;
            }
            if (optionalLibrary.isEmpty()) {
                log.warn("Target library not found for move operation: libraryId={}", targetLibraryId);
                return;
            }
            BookEntity bookEntity = optionalBook.get();
            LibraryEntity targetLibrary = optionalLibrary.get();

            Optional<LibraryPathEntity> optionalLibraryPathEntity = targetLibrary.getLibraryPaths().stream()
                    .filter(libraryPath -> Objects.equals(libraryPath.getId(), targetLibraryPathId))
                    .findFirst();
            if (optionalLibraryPathEntity.isEmpty()) {
                log.warn("Target library path not found for move operation: libraryId={}, pathId={}", targetLibraryId, targetLibraryPathId);
                return;
            }
            LibraryPathEntity libraryPathEntity = optionalLibraryPathEntity.get();

            if (bookEntity.getBookFiles() == null || bookEntity.getBookFiles().isEmpty()) {
                log.warn("Book has no files to move: bookId={}", bookId);
                return;
            }

            Path currentPrimaryFilePath = bookEntity.getFullFilePath();
            String pattern = fileMoveHelper.getFileNamingPattern(targetLibrary);
            Path newFilePath = fileMoveHelper.generateNewFilePath(bookEntity, libraryPathEntity, pattern);

            if (currentPrimaryFilePath.equals(newFilePath)) {
                return;
            }

            String newFileSubPath = fileMoveHelper.extractSubPath(newFilePath, libraryPathEntity);
            Path targetParentDir = newFilePath.getParent();

            if (targetParentDir == null) {
                log.warn("Target parent directory could not be determined for move operation: bookId={}", bookId);
                return;
            }

            // Validate all source paths exist before attempting moves
            for (var bookFile : bookEntity.getBookFiles()) {
                Path sourcePath = bookFile.getFullFilePath();
                if (!fileMoveHelper.validateSourceExists(sourcePath, bookFile.isFolderBased())) {
                    log.warn("Source {} not found: bookId={}, path={}",
                            bookFile.isFolderBased() ? "folder" : "file", bookId, sourcePath);
                    return;
                }
            }

            for (var bookFile : bookEntity.getBookFiles()) {
                Path sourcePath = bookFile.getFullFilePath();
                Path targetPath;
                if (bookFile.isBook()) {
                    targetPath = fileMoveHelper.generateNewFilePath(bookEntity, bookFile, libraryPathEntity, pattern);
                } else {
                    targetPath = targetParentDir.resolve(bookFile.getFileName());
                }

                if (sourcePath.equals(targetPath)) {
                    continue;
                }

                Path tempPath = fileMoveHelper.moveFileWithBackup(sourcePath);
                plannedMovesByBookFileId.put(bookFile.getId(), new PlannedMove(sourcePath, tempPath, targetPath));
                if (sourcePath.getParent() != null) {
                    sourceParentsToCleanup.add(sourcePath.getParent());
                }
            }

            if (plannedMovesByBookFileId.isEmpty()) {
                return;
            }

            List<PlannedMove> committedMoves = new ArrayList<>();

            // Commit file moves FIRST before updating database
            for (PlannedMove planned : plannedMovesByBookFileId.values()) {
                fileMoveHelper.commitMove(planned.temp(), planned.target());
                committedMoves.add(planned);
            }
            plannedMovesByBookFileId.clear();

            // Only update database after all file commits succeed
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    for (var bookFile : bookEntity.getBookFiles()) {
                        String newFileName;
                        if (bookFile.isBook()) {
                            Path targetPath = fileMoveHelper.generateNewFilePath(bookEntity, bookFile, libraryPathEntity, pattern);
                            newFileName = targetPath.getFileName().toString();
                        } else {
                            newFileName = bookFile.getFileName();
                        }
                        bookFileRepository.updateFileNameAndSubPath(bookFile.getId(), newFileName, newFileSubPath);
                    }

                    bookRepository.updateLibrary(bookEntity.getId(), targetLibrary.getId(), libraryPathEntity);
                });
            } catch (Exception e) {
                log.error("Database update failed after files were moved. Attempting to rollback file moves for book ID {}", bookId, e);
                for (PlannedMove committed : committedMoves) {
                    try {
                        fileMoveHelper.moveFile(committed.target(), committed.source());
                    } catch (Exception rollbackEx) {
                        log.error("Failed to rollback file move (Target -> Source) for book ID {}: {} -> {}", bookId, committed.target(), committed.source(), rollbackEx);
                    }
                }
                throw e;
            }

            Set<Path> libraryRoots = targetLibrary.getLibraryPaths().stream()
                    .map(LibraryPathEntity::getPath)
                    .map(Paths::get)
                    .map(Path::toAbsolutePath)
                    .map(Path::normalize)
                    .collect(Collectors.toSet());
            
            for (Path sourceParent : sourceParentsToCleanup) {
                fileMoveHelper.deleteEmptyParentDirsUpToLibraryFolders(sourceParent, libraryRoots);
            }

            entityManager.clear();

            BookEntity fresh = bookRepository.findById(bookId).orElseThrow();

            notificationService.sendMessage(Topic.BOOK_UPDATE, bookMapper.toBookWithDescription(fresh, false));

        } catch (Exception e) {
            log.error("Error moving file for book ID {}: {}", bookId, e.getMessage(), e);
        } finally {
            for (PlannedMove planned : plannedMovesByBookFileId.values()) {
                fileMoveHelper.rollbackMove(planned.temp(), planned.source());
            }
        }
    }

    public FileMoveResult moveSingleFile(BookEntity bookEntity) {
        record PlannedMove(Path source, Path temp, Path target) {}

        validateLocalStorage();

        Long libraryId = bookEntity.getLibraryPath().getLibrary().getId();
        Path libraryRoot = Paths.get(bookEntity.getLibraryPath().getPath()).toAbsolutePath().normalize();
        boolean isLibraryMonitoredWhenCalled = false;
        Map<Long, PlannedMove> plannedMovesByBookFileId = new HashMap<>();
        Set<Path> sourceParentsToCleanup = new HashSet<>();

        try {
            Set<Path> existingPaths = monitoringRegistrationService.getPathsForLibraries(Set.of(libraryId));
            isLibraryMonitoredWhenCalled = monitoringRegistrationService.isLibraryMonitored(libraryId) || !existingPaths.isEmpty();

            // Ensure bookFiles are loaded
            BookEntity bookWithFiles = bookEntity;
            if (bookEntity.getBookFiles() == null || bookEntity.getBookFiles().isEmpty()) {
                Optional<BookEntity> optionalBook = bookRepository.findByIdWithBookFiles(bookEntity.getId());
                if (optionalBook.isEmpty()) {
                    log.warn("Book not found for move operation: bookId={}", bookEntity.getId());
                    return FileMoveResult.builder().moved(false).build();
                }
                bookWithFiles = optionalBook.get();
            }

            if (bookWithFiles.getBookFiles() == null || bookWithFiles.getBookFiles().isEmpty()) {
                log.warn("Book has no files to move: bookId={}", bookEntity.getId());
                return FileMoveResult.builder().moved(false).build();
            }

            String pattern = fileMoveHelper.getFileNamingPattern(bookWithFiles.getLibraryPath().getLibrary());
            Path currentPrimaryFilePath = bookWithFiles.getFullFilePath();
            Path expectedPrimaryFilePath = fileMoveHelper.generateNewFilePath(bookWithFiles, bookWithFiles.getLibraryPath(), pattern);

            if (currentPrimaryFilePath.equals(expectedPrimaryFilePath)) {
                return FileMoveResult.builder().moved(false).build();
            }

            log.info("Files for book ID {} need to be moved to match library pattern", bookWithFiles.getId());

            String newFileSubPath = fileMoveHelper.extractSubPath(expectedPrimaryFilePath, bookWithFiles.getLibraryPath());
            Path targetParentDir = expectedPrimaryFilePath.getParent();

            if (targetParentDir == null) {
                log.warn("Target parent directory could not be determined for move operation: bookId={}", bookWithFiles.getId());
                return FileMoveResult.builder().moved(false).build();
            }

            // Validate all source paths exist before attempting moves
            for (var bookFile : bookWithFiles.getBookFiles()) {
                Path sourcePath = bookFile.getFullFilePath();
                if (!fileMoveHelper.validateSourceExists(sourcePath, bookFile.isFolderBased())) {
                    log.warn("Source {} not found: bookId={}, path={}",
                            bookFile.isFolderBased() ? "folder" : "file", bookWithFiles.getId(), sourcePath);
                    return FileMoveResult.builder().moved(false).build();
                }
            }

            if (isLibraryMonitoredWhenCalled) {
                log.debug("Unregistering library {} before moving files", libraryId);
                fileMoveHelper.unregisterLibrary(libraryId);
                monitoringRegistrationService.waitForEventsDrainedByPaths(existingPaths, EVENT_DRAIN_TIMEOUT_MS);
            }

            // Stage all files to temp locations
            for (var bookFile : bookWithFiles.getBookFiles()) {
                Path sourcePath = bookFile.getFullFilePath();
                Path targetPath;
                if (bookFile.isBook()) {
                    targetPath = fileMoveHelper.generateNewFilePath(bookWithFiles, bookFile, bookWithFiles.getLibraryPath(), pattern);
                } else {
                    targetPath = targetParentDir.resolve(bookFile.getFileName());
                }

                if (sourcePath.equals(targetPath)) {
                    continue;
                }

                Path tempPath = fileMoveHelper.moveFileWithBackup(sourcePath);
                plannedMovesByBookFileId.put(bookFile.getId(), new PlannedMove(sourcePath, tempPath, targetPath));
                if (sourcePath.getParent() != null) {
                    sourceParentsToCleanup.add(sourcePath.getParent());
                }
            }

            if (plannedMovesByBookFileId.isEmpty()) {
                return FileMoveResult.builder().moved(false).build();
            }

            List<PlannedMove> committedMoves = new ArrayList<>();

            // Commit all file moves FIRST before updating database
            for (PlannedMove planned : plannedMovesByBookFileId.values()) {
                fileMoveHelper.commitMove(planned.temp(), planned.target());
                committedMoves.add(planned);
            }
            plannedMovesByBookFileId.clear();

            // Update database for ALL BookFileEntity records (only after all commits succeed)
            BookEntity finalBookWithFiles = bookWithFiles;
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    for (var bookFile : finalBookWithFiles.getBookFiles()) {
                        String newFileName;
                        if (bookFile.isBook()) {
                            Path targetPath = fileMoveHelper.generateNewFilePath(finalBookWithFiles, bookFile, finalBookWithFiles.getLibraryPath(), pattern);
                            newFileName = targetPath.getFileName().toString();
                        } else {
                            newFileName = bookFile.getFileName();
                        }
                        bookFileRepository.updateFileNameAndSubPath(bookFile.getId(), newFileName, newFileSubPath);
                    }
                });
            } catch (Exception e) {
                log.error("Database update failed after files were moved. Attempting to rollback file moves for book ID {}", bookEntity.getId(), e);
                for (PlannedMove committed : committedMoves) {
                    try {
                        fileMoveHelper.moveFile(committed.target(), committed.source());
                    } catch (Exception rollbackEx) {
                        log.error("Failed to rollback file move (Target -> Source) for book ID {}: {} -> {}", bookEntity.getId(), committed.target(), committed.source(), rollbackEx);
                    }
                }
                throw e;
            }

            // Clean up empty parent directories
            Set<Path> libraryRoots = bookWithFiles.getLibraryPath().getLibrary().getLibraryPaths().stream()
                    .map(LibraryPathEntity::getPath)
                    .map(Paths::get)
                    .map(Path::toAbsolutePath)
                    .map(Path::normalize)
                    .collect(Collectors.toSet());
            
            for (Path sourceParent : sourceParentsToCleanup) {
                fileMoveHelper.deleteEmptyParentDirsUpToLibraryFolders(sourceParent, libraryRoots);
            }

            if (isLibraryMonitoredWhenCalled) {
                // Ensure any file system events from the move and cleanup are drained/ignored while we are still unregistered
                sleep(EVENT_DRAIN_TIMEOUT_MS);
            }

            String newFileName = expectedPrimaryFilePath.getFileName().toString();

            return FileMoveResult.builder()
                    .moved(true)
                    .newFileName(newFileName)
                    .newFileSubPath(newFileSubPath)
                    .build();
        } catch (Exception e) {
            log.error("Failed to move files for book ID {}: {}", bookEntity.getId(), e.getMessage(), e);
        } finally {
            // Rollback any uncommitted moves
            for (PlannedMove planned : plannedMovesByBookFileId.values()) {
                fileMoveHelper.rollbackMove(planned.temp(), planned.source());
            }

            if (isLibraryMonitoredWhenCalled) {
                log.debug("Registering library paths for library {} with root {}", libraryId, libraryRoot);
                LibraryEntity libraryEntity = bookEntity.getLibraryPath().getLibrary();
                Library library = libraryMapper.toLibrary(libraryEntity);
                library.setWatch(true);
                monitoringRegistrationService.registerLibrary(library);
            }
        }

        return FileMoveResult.builder().moved(false).build();
    }

    protected void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Sleep interrupted", e);
        }
    }

    private void validateLocalStorage() {
        String diskType = appProperties.getDiskType();
        if (!"LOCAL".equalsIgnoreCase(diskType)) {
            throw new IllegalStateException(
                    "File move operations are only supported on local storage. " +
                    "Current disk type is configured as: " + diskType + ". " +
                    "If you are using local storage, set DISK_TYPE=LOCAL in your environment."
            );
        }
    }
}
