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
import org.booklore.model.websocket.LogNotification;
import org.booklore.model.websocket.Topic;
import org.booklore.repository.BookAdditionalFileRepository;
import org.booklore.repository.BookRepository;
import org.booklore.repository.LibraryRepository;
import org.booklore.service.NotificationService;
import org.booklore.service.metadata.sidecar.SidecarMetadataWriter;
import org.booklore.service.monitoring.MonitoringRegistrationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

@AllArgsConstructor
@Service
@Slf4j
@Transactional
public class FileMoveService {

    private static final long EVENT_DRAIN_TIMEOUT_MS = 300;

    private record PlannedMove(Path source, Path temp, Path target) {}

    /** A refused move: the user-facing message, and whether it is a leftover temp file (possible only copy). */
    private record Conflict(String message, boolean staleTemp) {}

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


    public void bulkMoveFiles(FileMoveRequest request) {
        List<FileMoveRequest.Move> moves = request.getMoves();

        validateLocalStorage();

        Set<Long> allAffectedLibraryIds = collectAllAffectedLibraryIds(moves);
        Set<Path> libraryPaths = monitoringRegistrationService.getPathsForLibraries(allAffectedLibraryIds);

        log.info("Unregistering {} libraries before bulk file move", allAffectedLibraryIds.size());
        monitoringRegistrationService.unregisterLibraries(allAffectedLibraryIds);
        monitoringRegistrationService.waitForEventsDrainedByPaths(libraryPaths, EVENT_DRAIN_TIMEOUT_MS);

        List<Conflict> conflicts = new ArrayList<>();
        try {
            for (FileMoveRequest.Move move : moves) {
                processSingleMove(move).ifPresent(conflicts::add);
            }
            // Ensure any file system events from the moves are drained/ignored while we are still unregistered
            sleep(EVENT_DRAIN_TIMEOUT_MS);
        } finally {
            // Refused books must be reported even if re-registering a library fails
            try {
                for (Long libraryId : allAffectedLibraryIds) {
                    libraryRepository.findByIdWithPaths(libraryId)
                            .ifPresent(library -> monitoringRegistrationService.registerLibrary(libraryMapper.toLibrary(library)));
                }
            } finally {
                notifyConflicts(conflicts);
            }
        }
    }

    /**
     * Leftover temp files are always reported individually: each may be the only copy of a file, so the warning
     * and its path must never be folded into a summary. Ordinary path conflicts get their own message when there
     * is one, and a single summary when there are several.
     */
    private void notifyConflicts(List<Conflict> conflicts) {
        List<Conflict> taken = new ArrayList<>();
        for (Conflict conflict : conflicts) {
            if (conflict.staleTemp()) {
                notificationService.sendMessage(Topic.LOG, LogNotification.warn(conflict.message()));
            } else {
                taken.add(conflict);
            }
        }
        if (taken.size() == 1) {
            notificationService.sendMessage(Topic.LOG, LogNotification.warn(taken.getFirst().message()));
        } else if (taken.size() > 1) {
            notificationService.sendMessage(Topic.LOG, LogNotification.warn(
                    taken.size() + " books were not moved: their target paths are already taken by other files."));
        }
    }

    /**
     * Short, user-facing description of a refused move, e.g.
     * {@code Not moved: "Comics/Series 01.cbz" is already taken by another file.}
     * A leftover {@value FileMoveHelper#TEMP_SUFFIX} file gets its own message: it is not a destination
     * conflict, and it may be the only remaining copy of a file.
     */
    private static String describeConflict(FileAlreadyExistsException e, String libraryRoot) {
        String shown = e.getFile();
        if (shown != null && libraryRoot != null) {
            Path target = Paths.get(shown).toAbsolutePath().normalize();
            Path root = Paths.get(libraryRoot).toAbsolutePath().normalize();
            if (target.startsWith(root)) {
                shown = root.relativize(target).toString().replace('\\', '/');
            }
        }
        if (FileMoveHelper.isTempPath(shown)) {
            return "Not moved: leftover temporary file \"" + shown + "\" from an earlier failed move. "
                    + "Check it before deleting it: it may be the only copy of the file.";
        }
        return "Not moved: \"" + shown + "\" is already taken by another file.";
    }

    private Set<Long> collectAllAffectedLibraryIds(List<FileMoveRequest.Move> moves) {
        Set<Long> libraryIds = new HashSet<>();
        
        for (FileMoveRequest.Move move : moves) {
            libraryIds.add(move.getTargetLibraryId());
            bookRepository.findByIdWithBookFiles(move.getBookId())
                    .ifPresent(book -> libraryIds.add(book.getLibrary().getId()));
        }
        
        return libraryIds;
    }

    /**
     * @return the conflict, if the move was refused because a target is taken or a leftover temp file blocks it
     */
    private Optional<Conflict> processSingleMove(FileMoveRequest.Move move) {
        Long bookId = move.getBookId();
        Long targetLibraryId = move.getTargetLibraryId();
        Long targetLibraryPathId = move.getTargetLibraryPathId();

        Map<Path, PlannedMove> stagedMoves = new LinkedHashMap<>();
        Set<Path> sourceParentsToCleanup = new HashSet<>();
        // Resolved before any file operation, so the conflict handler needs no further (fallible) query.
        String targetRoot = null;

        try {
            Optional<BookEntity> optionalBook = bookRepository.findByIdWithBookFiles(bookId);
            Optional<LibraryEntity> optionalLibrary = libraryRepository.findByIdWithPaths(targetLibraryId);
            if (optionalBook.isEmpty()) {
                log.warn("Book not found for move operation: bookId={}", bookId);
                return Optional.empty();
            }
            if (optionalLibrary.isEmpty()) {
                log.warn("Target library not found for move operation: libraryId={}", targetLibraryId);
                return Optional.empty();
            }
            BookEntity bookEntity = optionalBook.get();
            LibraryEntity targetLibrary = optionalLibrary.get();

            Optional<LibraryPathEntity> optionalLibraryPathEntity = targetLibrary.getLibraryPaths().stream()
                    .filter(libraryPath -> Objects.equals(libraryPath.getId(), targetLibraryPathId))
                    .findFirst();
            if (optionalLibraryPathEntity.isEmpty()) {
                log.warn("Target library path not found for move operation: libraryId={}, pathId={}", targetLibraryId, targetLibraryPathId);
                return Optional.empty();
            }
            LibraryPathEntity libraryPathEntity = optionalLibraryPathEntity.get();
            targetRoot = libraryPathEntity.getPath();

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
                return Optional.empty();
            }

            List<BookFileEntity> bookFiles = bookEntity.getBookFiles().stream()
                    .filter(f -> f.getId() != null)
                    .collect(Collectors.toMap(BookFileEntity::getId, f -> f, (a, b) -> a, LinkedHashMap::new))
                    .values().stream().toList();

            Path currentPrimaryFilePath = bookEntity.getFullFilePath();
            String pattern = fileMoveHelper.getFileNamingPattern(targetLibrary);
            Path newFilePath = fileMoveHelper.generateNewFilePath(bookEntity, libraryPathEntity, pattern);

            if (currentPrimaryFilePath.equals(newFilePath)) {
                return Optional.empty();
            }

            String newFileSubPath = fileMoveHelper.extractSubPath(newFilePath, libraryPathEntity);
            Path targetParentDir = newFilePath.getParent();

            if (targetParentDir == null) {
                log.warn("Target parent directory could not be determined for move operation: bookId={}", bookId);
                return Optional.empty();
            }

            // Validate all source paths exist before attempting moves
            for (var bookFile : bookFiles) {
                Path sourcePath = bookFile.getFullFilePath();
                if (!fileMoveHelper.validateSourceExists(sourcePath, bookFile.isFolderBased())) {
                    log.warn("Source {} not found: bookId={}, path={}",
                            bookFile.isFolderBased() ? "folder" : "file", bookId, sourcePath);
                    return Optional.empty();
                }
            }

            // Resolve every target up front and refuse to proceed if any of them would overwrite
            // an existing file (e.g. another book whose metadata resolves to the same path).
            Map<BookFileEntity, Path> targetsByBookFile = new LinkedHashMap<>();
            Map<Path, Path> sourceToTarget = new LinkedHashMap<>();
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
                targetsByBookFile.put(bookFile, targetPath);
                sourceToTarget.put(sourcePath, targetPath);
            }
            Set<Path> reservedSlots = new LinkedHashSet<>();
            Map<Path, Path> sidecarsToStage = planSidecarMoves(currentPrimaryFilePath, newFilePath, trackedPaths(bookFiles), sourceToTarget, reservedSlots);
            fileMoveHelper.ensureTargetsAvailable(sourceToTarget, reservedSlots);

            for (var entry : targetsByBookFile.entrySet()) {
                BookFileEntity bookFile = entry.getKey();
                Path sourcePath = bookFile.getFullFilePath();
                Path targetPath = entry.getValue();

                Path tempPath = fileMoveHelper.moveFileWithBackup(sourcePath);
                stagedMoves.put(sourcePath, new PlannedMove(sourcePath, tempPath, targetPath));
                if (sourcePath.getParent() != null) {
                    sourceParentsToCleanup.add(sourcePath.getParent());
                }
            }
            for (var sidecar : sidecarsToStage.entrySet()) {
                Path tempPath = fileMoveHelper.moveFileWithBackup(sidecar.getKey());
                stagedMoves.put(sidecar.getKey(), new PlannedMove(sidecar.getKey(), tempPath, sidecar.getValue()));
            }

            if (stagedMoves.isEmpty()) {
                return Optional.empty();
            }

            List<PlannedMove> committedMoves = new ArrayList<>();

            // Commit file moves FIRST before updating database
            // On failure, already-committed moves are reverted here; still-staged ones are reverted in finally.
            commitPlannedMoves(stagedMoves, committedMoves, bookId);

            // Only update database after all file commits succeed
            try {
                transactionTemplate.executeWithoutResult(status -> {
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
            } catch (Exception e) {
                log.error("Database update failed after files were moved. Attempting to rollback file moves for book ID {}", bookId, e);
                rollbackCommittedMoves(committedMoves, bookId);
                throw e;
            }

            Set<Path> libraryRoots = targetLibrary.getLibraryPaths().stream()
                    .map(LibraryPathEntity::getPath)
                    .map(Paths::get)
                    .map(Path::toAbsolutePath)
                    .map(Path::normalize)
                    .collect(Collectors.toCollection(HashSet::new));
            // Also protect the source library root so cleanup doesn't traverse above it
            libraryRoots.add(Paths.get(bookEntity.getLibraryPath().getPath()).toAbsolutePath().normalize());

            for (Path sourceParent : sourceParentsToCleanup) {
                fileMoveHelper.deleteEmptyParentDirsUpToLibraryFolders(sourceParent, libraryRoots);
            }

            entityManager.clear();

            BookEntity fresh = bookRepository.findByIdWithBookFiles(bookId).orElseThrow();

            notificationService.sendMessage(Topic.BOOK_UPDATE, bookMapper.toBookWithDescription(fresh, false));

        } catch (FileAlreadyExistsException e) {
            log.warn("Refusing to move book ID {}: {}", bookId, e.getMessage());
            return Optional.of(new Conflict(describeConflict(e, targetRoot), FileMoveHelper.isTempPath(e.getFile())));
        } catch (Exception e) {
            log.error("Error moving file for book ID {}: {}", bookId, e.getMessage(), e);
        } finally {
            for (PlannedMove planned : stagedMoves.values()) {
                fileMoveHelper.rollbackMove(planned.temp(), planned.source());
            }
        }
        return Optional.empty();
    }

    public FileMoveResult moveSingleFile(BookEntity bookEntity) {
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
        Map<Path, PlannedMove> stagedMoves = new LinkedHashMap<>();
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

            // Resolve every target up front and refuse to proceed if any of them would overwrite
            // an existing file (e.g. another book whose metadata resolves to the same path).
            Map<BookFileEntity, Path> targetsByBookFile = new LinkedHashMap<>();
            Map<Path, Path> sourceToTarget = new LinkedHashMap<>();
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
                targetsByBookFile.put(bookFile, targetPath);
                sourceToTarget.put(sourcePath, targetPath);
            }
            Set<Path> reservedSlots = new LinkedHashSet<>();
            Map<Path, Path> sidecarsToStage = planSidecarMoves(currentPrimaryFilePath, expectedPrimaryFilePath, trackedPaths(bookFiles), sourceToTarget, reservedSlots);
            fileMoveHelper.ensureTargetsAvailable(sourceToTarget, reservedSlots);

            // Stage all files to temp locations
            for (var entry : targetsByBookFile.entrySet()) {
                BookFileEntity bookFile = entry.getKey();
                Path sourcePath = bookFile.getFullFilePath();
                Path targetPath = entry.getValue();

                Path tempPath = fileMoveHelper.moveFileWithBackup(sourcePath);
                stagedMoves.put(sourcePath, new PlannedMove(sourcePath, tempPath, targetPath));
                if (sourcePath.getParent() != null) {
                    sourceParentsToCleanup.add(sourcePath.getParent());
                }
            }
            for (var sidecar : sidecarsToStage.entrySet()) {
                Path tempPath = fileMoveHelper.moveFileWithBackup(sidecar.getKey());
                stagedMoves.put(sidecar.getKey(), new PlannedMove(sidecar.getKey(), tempPath, sidecar.getValue()));
            }

            if (stagedMoves.isEmpty()) {
                return FileMoveResult.builder().moved(false).build();
            }

            List<PlannedMove> committedMoves = new ArrayList<>();

            // Commit all file moves FIRST before updating database
            // On failure, already-committed moves are reverted here; still-staged ones are reverted in finally.
            commitPlannedMoves(stagedMoves, committedMoves, bookEntity.getId());

            // Update database for ALL BookFileEntity records (only after all commits succeed)
            BookEntity finalBookWithFiles = bookWithFiles;
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    for (var bookFile : bookFiles) {
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
                rollbackCommittedMoves(committedMoves, bookEntity.getId());
                throw e;
            }

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
        } catch (FileAlreadyExistsException e) {
            log.warn("Refusing to move book ID {}: {}", bookEntity.getId(), e.getMessage());
            notificationService.sendMessage(Topic.LOG, LogNotification.warn(describeConflict(e, libraryRoot.toString())));
        } catch (Exception e) {
            log.error("Failed to move files for book ID {}: {}", bookEntity.getId(), e.getMessage(), e);
        } finally {
            // Rollback any uncommitted moves
            for (PlannedMove planned : stagedMoves.values()) {
                fileMoveHelper.rollbackMove(planned.temp(), planned.source());
            }

            if (isLibraryMonitoredWhenCalled) {
                log.debug("Registering library paths for library {} with root {}", libraryId, libraryRoot);
                libraryRepository.findByIdWithPaths(libraryId).ifPresent(lib -> {
                    Library library = libraryMapper.toLibrary(lib);
                    library.setWatch(true);
                    monitoringRegistrationService.registerLibrary(library);
                });
            }
        }

        return FileMoveResult.builder().moved(false).build();
    }

    /**
     * The sidecar metadata file and sidecar cover are located purely from the primary file's base name
     * ({@code X.cbz} -> {@code X.metadata.json}, {@code X.cover.jpg}), so they are moved as part of the same
     * plan as the book files: checked up front, staged, committed and rolled back together.
     * <p>
     * Their target slots are added to {@code targetsToCheck} even when this book has no sidecar yet: if another
     * book's sidecar already sits there, moving this book would make it read (and later overwrite) that sidecar.
     * <p>
     * A sidecar path that is also one of the book's tracked files (in {@code trackedPaths}, whether or not that
     * file moves) is owned by the book-file plan and never moved here, so its database record stays valid. The
     * slot the renamed book will use is still added to {@code reservedSlots}, unless it is itself a tracked file.
     *
     * @return the sidecar files that actually exist and must be staged, source -> target
     */
    private Map<Path, Path> planSidecarMoves(Path currentPrimary, Path newPrimary, Set<Path> trackedPaths,
                                             Map<Path, Path> targetsToCheck, Set<Path> reservedSlots) {
        Map<Path, Path> toStage = new LinkedHashMap<>();
        List<UnaryOperator<Path>> companions = List.of(sidecarMetadataWriter::getSidecarPath, sidecarMetadataWriter::getCoverPath);
        for (UnaryOperator<Path> companion : companions) {
            Path source = companion.apply(currentPrimary);
            Path target = companion.apply(newPrimary);
            if (source == null || target == null || source.equals(target)) {
                continue;
            }
            if (trackedPaths.contains(normalize(source))) {
                if (!trackedPaths.contains(normalize(target))) {
                    reservedSlots.add(target);
                }
                continue;
            }
            targetsToCheck.put(source, target);
            if (fileMoveHelper.validateSourceExists(source, false)) {
                toStage.put(source, target);
            }
        }
        return toStage;
    }

    private static Set<Path> trackedPaths(List<BookFileEntity> bookFiles) {
        return bookFiles.stream()
                .map(BookFileEntity::getFullFilePath)
                .filter(Objects::nonNull)
                .map(FileMoveService::normalize)
                .collect(Collectors.toSet());
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    /**
     * Commits staged moves one by one. Each successful commit is moved from {@code staged} to
     * {@code committed}. If a commit fails, the moves committed so far are reverted before rethrowing,
     * so the caller only has to roll back what is left in {@code staged}.
     */
    private void commitPlannedMoves(Map<Path, PlannedMove> staged, List<PlannedMove> committed, Long bookId) throws IOException {
        Iterator<PlannedMove> it = staged.values().iterator();
        while (it.hasNext()) {
            PlannedMove planned = it.next();
            try {
                fileMoveHelper.commitMove(planned.temp(), planned.target());
            } catch (IOException e) {
                rollbackCommittedMoves(committed, bookId);
                committed.clear();
                throw e;
            }
            committed.add(planned);
            it.remove();
        }
    }

    private void rollbackCommittedMoves(List<PlannedMove> committed, Long bookId) {
        for (int i = committed.size() - 1; i >= 0; i--) {
            PlannedMove move = committed.get(i);
            try {
                fileMoveHelper.moveFile(move.target(), move.source());
            } catch (Exception rollbackEx) {
                log.error("Failed to rollback file move (Target -> Source) for book ID {}: {} -> {}", bookId, move.target(), move.source(), rollbackEx);
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
