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
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.websocket.Topic;
import org.booklore.repository.BookAdditionalFileRepository;
import org.booklore.repository.BookRepository;
import org.booklore.repository.LibraryRepository;
import org.booklore.service.NotificationService;
import org.booklore.service.metadata.sidecar.SidecarMetadataWriter;
import org.booklore.service.monitoring.MonitoringRegistrationService;
import org.booklore.service.watcher.BookFileEventSuppression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.time.Duration;
import java.util.stream.Collectors;

@AllArgsConstructor
@Service
@Slf4j
@Transactional
public class FileMoveService {

    private static final long EVENT_DRAIN_TIMEOUT_MS = 300;
    private final Object moveTransactionKey = new Object();

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
    private final SidecarMetadataWriter sidecarMetadataWriter;
    private final BookFileMutationGuard bookFileMutationGuard;
    private final BookFileEventSuppression bookFileEventSuppression;

    private record PlannedMove(Path source, Path temp, Path target) {}

    private record MoveAttempt(Long bookId, List<PlannedMove> moves, AtomicBoolean restored) {}

    public void bulkMoveFiles(FileMoveRequest request) {
        bookFileMutationGuard.beforeMutation();
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
        } finally {
            List<Library> librariesToRegister = new ArrayList<>();
            for (Long libraryId : allAffectedLibraryIds) {
                libraryRepository.findByIdWithPaths(libraryId)
                        .ifPresent(library -> librariesToRegister.add(libraryMapper.toLibrary(library)));
            }
            afterMoveCompletion(() -> {
                sleep(EVENT_DRAIN_TIMEOUT_MS);
                librariesToRegister.forEach(monitoringRegistrationService::registerLibrary);
            });
        }
    }

    private Set<Long> collectAllAffectedLibraryIds(List<FileMoveRequest.Move> moves) {
        Set<Long> libraryIds = new HashSet<>();
        
        for (FileMoveRequest.Move move : moves) {
            if (move.getTargetLibraryId() != null) {
                libraryIds.add(move.getTargetLibraryId());
            }
            bookRepository.findByIdWithBookFiles(move.getBookId())
                    .ifPresent(book -> libraryIds.add(book.getLibrary().getId()));
        }
        
        return libraryIds;
    }

    private void processSingleMove(FileMoveRequest.Move move) {
        Long bookId = move.getBookId();
        Long targetLibraryId = move.getTargetLibraryId();
        Long targetLibraryPathId = move.getTargetLibraryPathId();

        Map<Long, PlannedMove> plannedMovesByBookFileId = new HashMap<>();
        List<PlannedMove> committedMoves = new ArrayList<>();
        AtomicBoolean restored = new AtomicBoolean();
        boolean databaseUpdated = false;
        Set<Path> sourceParentsToCleanup = new HashSet<>();

        try {
            Optional<BookEntity> optionalBook = bookRepository.findByIdWithBookFiles(bookId);
            if (optionalBook.isEmpty()) {
                log.warn("Book not found for move operation: bookId={}", bookId);
                return;
            }
            BookEntity bookEntity = optionalBook.get();
            if (targetLibraryId == null) {
                targetLibraryId = bookEntity.getLibrary().getId();
            }
            Optional<LibraryEntity> optionalLibrary = libraryRepository.findByIdWithPaths(targetLibraryId);
            if (optionalLibrary.isEmpty()) {
                log.warn("Target library not found for move operation: libraryId={}", targetLibraryId);
                return;
            }
            LibraryEntity targetLibrary = optionalLibrary.get();

            Optional<LibraryPathEntity> optionalLibraryPathEntity = targetLibrary.getLibraryPaths().stream()
                    .filter(libraryPath -> targetLibraryPathId == null || Objects.equals(libraryPath.getId(), targetLibraryPathId))
                    .findFirst();
            if (optionalLibraryPathEntity.isEmpty()) {
                log.warn("Target library path not found for move operation: libraryId={}, pathId={}", targetLibraryId, targetLibraryPathId);
                return;
            }
            LibraryPathEntity libraryPathEntity = optionalLibraryPathEntity.get();

            if (bookEntity.getBookFiles() == null || bookEntity.getBookFiles().isEmpty()) {
                if (Boolean.TRUE.equals(bookEntity.getIsPhysical())) {
                    log.info("Moving physical book (no files): bookId={}", bookId);
                    bookRepository.updateLibrary(bookEntity.getId(), targetLibrary.getId(), libraryPathEntity);
                    entityManager.clear();
                    BookEntity fresh = bookRepository.findByIdWithBookFiles(bookId).orElseThrow();
                    notificationService.sendMessage(Topic.BOOK_UPDATE, bookMapper.toBookWithDescription(fresh, false));
                } else {
                    log.warn("Book has no files to move: bookId={}", bookId);
                }
                return;
            }

            List<BookFileEntity> bookFiles = bookEntity.getBookFiles().stream()
                    .filter(f -> f.getId() != null)
                    .collect(Collectors.toMap(BookFileEntity::getId, f -> f, (a, b) -> a, LinkedHashMap::new))
                    .values().stream().toList();

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
            for (var bookFile : bookFiles) {
                Path sourcePath = bookFile.getFullFilePath();
                if (!fileMoveHelper.validateSourceExists(sourcePath, bookFile.isFolderBased())) {
                    log.warn("Source {} not found: bookId={}, path={}",
                            bookFile.isFolderBased() ? "folder" : "file", bookId, sourcePath);
                    return;
                }
            }

            for (var bookFile : bookFiles) {
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

                bookFileEventSuppression.suppress(sourcePath, Duration.ofMinutes(2), bookFile.isFolderBased());
                bookFileEventSuppression.suppress(targetPath, Duration.ofMinutes(2), bookFile.isFolderBased());

                Path tempPath = fileMoveHelper.moveFileWithBackup(sourcePath);
                plannedMovesByBookFileId.put(bookFile.getId(), new PlannedMove(sourcePath, tempPath, targetPath));
                if (sourcePath.getParent() != null) {
                    sourceParentsToCleanup.add(sourcePath.getParent());
                }
            }

            if (plannedMovesByBookFileId.isEmpty()) {
                return;
            }

            // Commit file moves FIRST before updating database
            for (PlannedMove planned : plannedMovesByBookFileId.values()) {
                fileMoveHelper.commitMove(planned.temp(), planned.target());
                committedMoves.add(planned);
            }
            plannedMovesByBookFileId.clear();

            // Only update database after all file commits succeed
            transactionTemplate.executeWithoutResult(status -> {
                registerMoveRollback(new MoveAttempt(bookId, List.copyOf(committedMoves), restored));
                for (var bookFile : bookFiles) {
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
            databaseUpdated = true;

            Set<Path> libraryRoots = targetLibrary.getLibraryPaths().stream()
                    .map(LibraryPathEntity::getPath)
                    .map(Paths::get)
                    .map(Path::toAbsolutePath)
                    .map(Path::normalize)
                    .collect(Collectors.toCollection(HashSet::new));
            // Also protect the source library root so cleanup doesn't traverse above it
            libraryRoots.add(Paths.get(bookEntity.getLibraryPath().getPath()).toAbsolutePath().normalize());

            afterMoveCommit(() -> {
                sidecarMetadataWriter.moveSidecarFiles(currentPrimaryFilePath, newFilePath);
                for (Path sourceParent : sourceParentsToCleanup) {
                    fileMoveHelper.deleteEmptyParentDirsUpToLibraryFolders(sourceParent, libraryRoots);
                }
            });

            entityManager.clear();

            BookEntity fresh = bookRepository.findByIdWithBookFiles(bookId).orElseThrow();

            notificationService.sendMessage(Topic.BOOK_UPDATE, bookMapper.toBookWithDescription(fresh, false));

        } catch (Exception e) {
            log.error("Error moving file for book ID {}: {}", bookId, e.getMessage(), e);
        } finally {
            if (!databaseUpdated) {
                restoreCommittedMoves(new MoveAttempt(bookId, committedMoves, restored));
            }
            for (PlannedMove planned : plannedMovesByBookFileId.values()) {
                fileMoveHelper.rollbackMove(planned.temp(), planned.source());
            }
        }
    }

    public FileMoveResult moveSingleFile(BookEntity bookEntity) {
        bookFileMutationGuard.beforeMutation();

        validateLocalStorage();

        Long libraryId = bookEntity.getLibraryPath().getLibrary().getId();
        Path libraryRoot = Paths.get(bookEntity.getLibraryPath().getPath()).toAbsolutePath().normalize();

        // Evict the L1-cached LibraryEntity so that findByIdWithPaths() returns a fresh
        // instance with libraryPaths eagerly loaded.  Without this, the caller's EntityGraph
        // (e.g. findAllWithMetadataByIds) may have loaded the LibraryEntity without
        // libraryPaths, poisoning the L1 cache and causing a LazyInitializationException
        // when libraryPaths is accessed later (OSIV is disabled).
        LibraryEntity cachedLibrary = bookEntity.getLibraryPath().getLibrary();
        if (entityManager.contains(cachedLibrary)) {
            entityManager.detach(cachedLibrary);
        }

        boolean isLibraryMonitoredWhenCalled = false;
        Map<Long, PlannedMove> plannedMovesByBookFileId = new HashMap<>();
        List<PlannedMove> committedMoves = new ArrayList<>();
        AtomicBoolean restored = new AtomicBoolean();
        boolean databaseUpdated = false;
        Set<Path> sourceParentsToCleanup = new HashSet<>();

        try {
            Set<Path> existingPaths = monitoringRegistrationService.getPathsForLibraries(Set.of(libraryId));
            isLibraryMonitoredWhenCalled = monitoringRegistrationService.isLibraryMonitored(libraryId) || !existingPaths.isEmpty();

            // Always use the current persistence-context instance. The caller may have already
            // changed metadata or the file hash in this transaction; a separately loaded or
            // detached BookFileEntity would be stale when the move updates its path.
            BookEntity bookWithFiles = bookRepository.findByIdWithBookFiles(bookEntity.getId())
                    .orElse(null);
            if (bookWithFiles == null) {
                log.warn("Book not found for move operation: bookId={}", bookEntity.getId());
                return FileMoveResult.builder().moved(false).build();
            }

            if (bookWithFiles.getBookFiles() == null || bookWithFiles.getBookFiles().isEmpty()) {
                log.warn("Book has no files to move: bookId={}", bookEntity.getId());
                return FileMoveResult.builder().moved(false).build();
            }

            List<BookFileEntity> bookFiles = bookWithFiles.getBookFiles().stream()
                    .filter(f -> f.getId() != null)
                    .collect(Collectors.toMap(BookFileEntity::getId, f -> f, (a, b) -> a, LinkedHashMap::new))
                    .values().stream().toList();

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
            for (var bookFile : bookFiles) {
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
            for (var bookFile : bookFiles) {
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

            // Commit all file moves FIRST before updating database
            for (PlannedMove planned : plannedMovesByBookFileId.values()) {
                fileMoveHelper.commitMove(planned.temp(), planned.target());
                committedMoves.add(planned);
            }
            plannedMovesByBookFileId.clear();

            // Update the managed rows directly. A bulk JPQL update here bypasses Hibernate's
            // first-level cache; the dirty managed BookFileEntity would then flush its stale
            // snapshot at transaction commit and collide with that update under MariaDB.
            transactionTemplate.executeWithoutResult(status -> {
                registerMoveRollback(new MoveAttempt(bookEntity.getId(), List.copyOf(committedMoves), restored));
                for (var bookFile : bookFiles) {
                    String newFileName;
                    if (bookFile.isBook()) {
                        Path targetPath = fileMoveHelper.generateNewFilePath(bookWithFiles, bookFile, bookWithFiles.getLibraryPath(), pattern);
                        newFileName = targetPath.getFileName().toString();
                    } else {
                        newFileName = bookFile.getFileName();
                    }
                    bookFile.setFileName(newFileName);
                    bookFile.setFileSubPath(newFileSubPath);
                }
            });
            databaseUpdated = true;

            // Clean up empty parent directories – load library paths via a separate query
            // to avoid LazyInitializationException (OSIV is disabled) and MultipleBagFetchException
            // (cannot eagerly fetch both bookFiles and libraryPaths in the same EntityGraph).
            Set<Path> libraryRoots = libraryRepository.findByIdWithPaths(libraryId)
                    .map(lib -> lib.getLibraryPaths().stream()
                            .map(LibraryPathEntity::getPath)
                            .map(Paths::get)
                            .map(Path::toAbsolutePath)
                            .map(Path::normalize)
                            .collect(Collectors.toCollection(HashSet::new)))
                    .orElseGet(HashSet::new);
            // Also protect the source library root so cleanup doesn't traverse above it
            libraryRoots.add(Paths.get(bookWithFiles.getLibraryPath().getPath()).toAbsolutePath().normalize());

            afterMoveCommit(() -> {
                sidecarMetadataWriter.moveSidecarFiles(currentPrimaryFilePath, expectedPrimaryFilePath);
                for (Path sourceParent : sourceParentsToCleanup) {
                    fileMoveHelper.deleteEmptyParentDirsUpToLibraryFolders(sourceParent, libraryRoots);
                }
            });

            String newFileName = expectedPrimaryFilePath.getFileName().toString();

            return FileMoveResult.builder()
                    .moved(true)
                    .newFileName(newFileName)
                    .newFileSubPath(newFileSubPath)
                    .build();
        } catch (Exception e) {
            log.error("Failed to move files for book ID {}: {}", bookEntity.getId(), e.getMessage(), e);
        } finally {
            if (!databaseUpdated) {
                restoreCommittedMoves(new MoveAttempt(bookEntity.getId(), committedMoves, restored));
            }
            // Rollback any uncommitted moves
            for (PlannedMove planned : plannedMovesByBookFileId.values()) {
                fileMoveHelper.rollbackMove(planned.temp(), planned.source());
            }

            if (isLibraryMonitoredWhenCalled) {
                log.debug("Registering library paths for library {} with root {}", libraryId, libraryRoot);
                libraryRepository.findByIdWithPaths(libraryId).ifPresent(lib -> {
                    Library library = libraryMapper.toLibrary(lib);
                    library.setWatch(true);
                    afterMoveCompletion(() -> {
                        sleep(EVENT_DRAIN_TIMEOUT_MS);
                        monitoringRegistrationService.registerLibrary(library);
                    });
                });
            }
        }

        return FileMoveResult.builder().moved(false).build();
    }

    private void registerMoveRollback(MoveAttempt attempt) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            moveTransaction().attempts.add(attempt);
        }
    }

    private void afterMoveCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            moveTransaction().afterCommit.add(action);
        } else {
            action.run();
        }
    }

    private void afterMoveCompletion(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            moveTransaction().afterCompletion.add(action);
        } else {
            action.run();
        }
    }

    private MoveTransaction moveTransaction() {
        MoveTransaction transaction = (MoveTransaction) TransactionSynchronizationManager.getResource(moveTransactionKey);
        if (transaction == null) {
            transaction = new MoveTransaction();
            TransactionSynchronizationManager.bindResource(moveTransactionKey, transaction);
            TransactionSynchronizationManager.registerSynchronization(transaction);
        }
        return transaction;
    }

    private void restoreCommittedMoves(MoveAttempt attempt) {
        if (!attempt.restored().compareAndSet(false, true)) {
            return;
        }
        for (PlannedMove committed : attempt.moves().reversed()) {
            try {
                fileMoveHelper.moveFile(committed.target(), committed.source());
            } catch (Exception rollbackEx) {
                log.error("Failed to rollback file move for book ID {}: {} -> {}",
                        attempt.bookId(), committed.target(), committed.source(), rollbackEx);
            }
        }
    }

    private final class MoveTransaction implements TransactionSynchronization {
        private final List<MoveAttempt> attempts = new ArrayList<>();
        private final List<Runnable> afterCommit = new ArrayList<>();
        private final List<Runnable> afterCompletion = new ArrayList<>();

        @Override
        public void suspend() {
            TransactionSynchronizationManager.unbindResource(moveTransactionKey);
        }

        @Override
        public void resume() {
            TransactionSynchronizationManager.bindResource(moveTransactionKey, this);
        }

        @Override
        public void afterCommit() {
            for (Runnable action : afterCommit) {
                try {
                    action.run();
                } catch (Exception e) {
                    log.error("Failed to finish a committed file move", e);
                }
            }
        }

        @Override
        public void afterCompletion(int status) {
            try {
                if (status == STATUS_ROLLED_BACK) {
                    for (MoveAttempt attempt : attempts.reversed()) {
                        restoreCommittedMoves(attempt);
                    }
                } else if (status == STATUS_UNKNOWN) {
                    log.error("File move transaction outcome is unknown; manual reconciliation is required");
                }
                if (status != STATUS_UNKNOWN) {
                    for (Runnable action : afterCompletion) {
                        try {
                            action.run();
                        } catch (Exception e) {
                            log.error("Failed to restore monitoring after file moves", e);
                        }
                    }
                }
            } finally {
                TransactionSynchronizationManager.unbindResource(moveTransactionKey);
            }
        }
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
        if (!appProperties.isLocalStorage()) {
            throw new IllegalStateException(
                    "File move operations are only supported on local storage. " +
                    "Current disk type is configured as: " + appProperties.getDiskType() + ". " +
                    "If you are using local storage, set DISK_TYPE=LOCAL in your environment."
            );
        }
    }
}
