package org.grimmory.service.watcher;

import org.booklore.exception.ApiError;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.dto.settings.LibraryFile;
import org.booklore.model.enums.BookFileExtension;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.LibraryOrganizationMode;
import org.booklore.repository.BookRepository;
import org.booklore.repository.LibraryRepository;
import org.booklore.service.file.FileFingerprint;
import org.booklore.service.library.LibraryProcessingService;
import org.booklore.service.library.LibraryScanListener;
import org.booklore.util.FileUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class LibraryFileEventProcessor implements SmartLifecycle {

    private static final int LIFECYCLE_PHASE = 10;
    private static final long DEBOUNCE_MS = 500L;
    private static final long FOLDER_CREATE_DEBOUNCE_MS = 5000L;
    private static final long PENDING_DELETION_GRACE_MS = 8000L;
    private static final long STABILITY_CHECK_INTERVAL_MS = 3000L;
    private static final long STABILITY_MAX_WAIT_MS = 120000L;
    private static final int MIN_AUDIO_FILES_FOR_FOLDER_AUDIOBOOK = 2;

    private final BlockingQueue<FileEvent> eventQueue = new LinkedBlockingQueue<>();
    private final LibraryRepository libraryRepository;
    private final BookRepository bookRepository;
    private final BookFileTransactionalHandler bookFileTransactionalHandler;
    private final BookFilePersistenceService bookFilePersistenceService;
    private final LibraryProcessingService libraryProcessingService;
    private final LibraryScanListener libraryScanListener;
    private final PendingDeletionPool pendingDeletionPool;

    private ScheduledExecutorService scheduler;
    private final ConcurrentMap<Path, ScheduledFuture<?>> pendingDeletes = new ConcurrentHashMap<>();
    private final ConcurrentMap<Path, ScheduledFuture<?>> pendingFolderCreates = new ConcurrentHashMap<>();
    private final Set<Path> filesFromPendingFolder = ConcurrentHashMap.newKeySet();
    private volatile boolean running;
    private Thread workerThread;

    @Override
    public void start() {
        if (running && workerThread != null && workerThread.isAlive()) {
            log.info("LibraryFileEventProcessor is already running.");
            return;
        }

        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newScheduledThreadPool(1);
        }

        running = true;
        workerThread = Thread.ofVirtual().name("lib-event-processor").start(() -> {
            log.info("LibraryFileEventProcessor virtual thread started.");
            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    handleEvent(eventQueue.take());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("LibraryFileEventProcessor virtual thread interrupted.");
                } catch (Exception e) {
                    log.error("Error while processing file event", e);
                }
            }
        });
    }

    @Override
    public void stop() {
        log.info("Shutting down LibraryFileEventProcessor...");
        running = false;
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        if (workerThread != null) {
            workerThread.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return LIFECYCLE_PHASE;
    }

    public void processEvent(WatchEvent.Kind<?> eventKind, long libraryId, Path fullPath, boolean isDirectory) {
        if (libraryScanListener.isScanning(libraryId)) {
            log.debug("[SKIP] Library {} is currently being scanned, ignoring file event for '{}'", libraryId, fullPath);
            return;
        }

        Path path = fullPath.toAbsolutePath().normalize();

        if (eventKind == StandardWatchEventKinds.ENTRY_DELETE) {
            ScheduledFuture<?> existing = pendingDeletes.put(path, scheduler.schedule(() -> {
                eventQueue.offer(new FileEvent(eventKind, libraryId, path, isDirectory));
                pendingDeletes.remove(path);
            }, DEBOUNCE_MS, TimeUnit.MILLISECONDS));

            if (existing != null) existing.cancel(false);
        } else if (eventKind == StandardWatchEventKinds.ENTRY_CREATE) {
            ScheduledFuture<?> pendingDelete = pendingDeletes.remove(path);
            if (pendingDelete != null) {
                pendingDelete.cancel(false);
                log.debug("[DEBOUNCE] CREATE after pending DELETE for '{}', treating as file modification", path);
            }

            if (isDirectory) {
                log.debug("[DEBOUNCE] Scheduling folder create for '{}' with {}ms delay", path, FOLDER_CREATE_DEBOUNCE_MS);
                ScheduledFuture<?> existingFolder = pendingFolderCreates.put(path, scheduler.schedule(() -> {
                    eventQueue.offer(new FileEvent(eventKind, libraryId, path, true));
                    pendingFolderCreates.remove(path);
                    filesFromPendingFolder.removeIf(f -> f.startsWith(path));
                }, FOLDER_CREATE_DEBOUNCE_MS, TimeUnit.MILLISECONDS));

                if (existingFolder != null) existingFolder.cancel(false);
            } else {
                boolean insidePendingFolder = false;
                for (var entry : pendingFolderCreates.entrySet()) {
                    if (path.startsWith(entry.getKey())) {
                        insidePendingFolder = true;
                        filesFromPendingFolder.add(path);
                        ScheduledFuture<?> oldTimer = entry.getValue();
                        if (oldTimer != null) oldTimer.cancel(false);
                        Path folderPath = entry.getKey();
                        pendingFolderCreates.put(folderPath, scheduler.schedule(() -> {
                            eventQueue.offer(new FileEvent(eventKind, libraryId, folderPath, true));
                            pendingFolderCreates.remove(folderPath);
                            filesFromPendingFolder.removeIf(f -> f.startsWith(folderPath));
                        }, FOLDER_CREATE_DEBOUNCE_MS, TimeUnit.MILLISECONDS));
                        log.debug("[DEBOUNCE] File '{}' tracked, reset folder debounce for '{}'", path.getFileName(), folderPath);
                        break;
                    }
                }
                if (!insidePendingFolder) {
                    scheduleWithStabilityCheck(path, () ->
                            eventQueue.offer(new FileEvent(eventKind, libraryId, path, false)));
                }
            }
        } else {
            eventQueue.offer(new FileEvent(eventKind, libraryId, path, false));
        }
    }

    private void handleEvent(FileEvent event) {
        Path path = event.fullPath();
        String fileName = path.getFileName().toString();
        log.info("[PROCESS] '{}' event for '{}'{}", event.eventKind().name(), fileName,
                event.isDirectory() ? " (directory)" : "");

        LibraryEntity library = libraryRepository.findByIdWithPaths(event.libraryId())
                .orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(event.libraryId()));

        if (library.getLibraryPaths().stream().noneMatch(lp -> path.startsWith(lp.getPath()))) {
            log.warn("[SKIP] Path outside of library: '{}'", path);
            return;
        }

        boolean isDirectory = event.isDirectory();

        if (isDirectory) {
            switch (event.eventKind().name()) {
                case "ENTRY_CREATE" -> handleFolderCreate(library, path);
                case "ENTRY_DELETE" -> handleFolderDelete(library, path);
                default -> log.warn("[SKIP] Folder event '{}' ignored for '{}'", event.eventKind().name(), fileName);
            }
            return;
        }

        if (!isBookFile(fileName)) {
            log.debug("[SKIP] Ignored non-book file '{}'", fileName);
            return;
        }

        switch (event.eventKind().name()) {
            case "ENTRY_CREATE" -> handleFileCreate(library, path);
            case "ENTRY_DELETE" -> handleFileDelete(library, path);
            default -> log.debug("[SKIP] File event '{}' ignored for '{}'", event.eventKind().name(), fileName);
        }
    }

    private void handleFileCreate(LibraryEntity library, Path path) {
        if (!fileHasContent(path)) {
            log.debug("[SKIP] Zero-byte file: '{}'", path);
            return;
        }
        log.info("[FILE_CREATE] '{}'", path);
        bookFileTransactionalHandler.handleNewBookFile(library.getId(), path);
    }

    private void handleFileDelete(LibraryEntity library, Path path) {
        log.info("[FILE_DELETE] '{}'", path);
        try {
            String libPath = bookFilePersistenceService.findMatchingLibraryPath(library, path);
            LibraryPathEntity libPathEntity = bookFilePersistenceService.getLibraryPathEntityForFile(library, libPath);

            Path relPath = Paths.get(libPathEntity.getPath()).relativize(path);
            String fileName = relPath.getFileName().toString();
            String fileSubPath = Optional.ofNullable(relPath.getParent()).map(Path::toString).orElse("");

            bookFilePersistenceService.findBookFileByLibraryPathSubPathAndFileName(libPathEntity.getId(), fileSubPath, fileName)
                    .ifPresentOrElse(bookFile -> {
                        var book = bookFile.getBook();
                        ScheduledFuture<?> timer = scheduler.schedule(
                                () -> pendingDeletionPool.expireFileDeletion(path),
                                PENDING_DELETION_GRACE_MS, TimeUnit.MILLISECONDS);
                        pendingDeletionPool.addFileDeletion(path, library.getId(), bookFile, book, timer);
                        log.info("[PENDING_DELETE] Book '{}' deferred deletion (grace period {}ms)", fileName, PENDING_DELETION_GRACE_MS);
                    }, () -> log.debug("[NOT_FOUND] BookFile for deleted path '{}' not found (likely renamed/moved)", path));

        } catch (Exception e) {
            log.warn("[ERROR] While handling file delete '{}': {}", path, e.getMessage());
        }
    }

    private void handleFolderCreate(LibraryEntity library, Path folderPath) {
        log.info("[FOLDER_CREATE] '{}'", folderPath);

        pendingFolderCreates.entrySet().removeIf(entry -> {
            if (entry.getKey().startsWith(folderPath) && !entry.getKey().equals(folderPath)) {
                entry.getValue().cancel(false);
                filesFromPendingFolder.removeIf(f -> f.startsWith(entry.getKey()));
                log.debug("[DEBOUNCE] Cancelled child folder debounce for '{}'", entry.getKey());
                return true;
            }
            return false;
        });

        var mode = library.getOrganizationMode() != null
                ? library.getOrganizationMode() : LibraryOrganizationMode.AUTO_DETECT;

        if (mode == LibraryOrganizationMode.BOOK_PER_FILE) {
            processFilesInFolderIndividually(library, folderPath);
            return;
        }

        if (mode == LibraryOrganizationMode.BOOK_PER_FOLDER) {
            processFilesInFolderAsOneBook(library, folderPath);
            return;
        }

        // AUTO_DETECT: existing audiobook folder detection logic
        FolderAnalysis analysis = analyzeFolderForAudiobook(folderPath);

        if (analysis.isFolderBasedAudiobook()) {
            log.info("[FOLDER_AUDIOBOOK] Detected folder-based audiobook: {} ({} audio files)",
                    folderPath.getFileName(), analysis.audioFileCount());
            try {
                bookFileTransactionalHandler.handleNewFolderAudiobook(library.getId(), folderPath);
                int cleared = 0;
                var iterator = filesFromPendingFolder.iterator();
                while (iterator.hasNext()) {
                    if (iterator.next().startsWith(folderPath)) {
                        iterator.remove();
                        cleared++;
                    }
                }
                if (cleared > 0) {
                    log.debug("[FOLDER_AUDIOBOOK] Cleared {} tracked files that are now part of folder audiobook", cleared);
                }
            } catch (Exception e) {
                log.warn("[ERROR] Processing folder audiobook '{}': {}", folderPath, e.getMessage());
            }
        } else {
            processTrackedAndWalkedFiles(library, folderPath);
        }
    }

    private void processFilesInFolderIndividually(LibraryEntity library, Path folderPath) {
        if (Files.exists(folderPath.resolve(".ignore"))) {
            log.debug("[SKIP] Folder has .ignore file: '{}'", folderPath);
            clearTrackedFilesFor(folderPath);
            return;
        }
        clearTrackedFilesFor(folderPath);
        try (var stream = Files.walk(folderPath)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> !isUnderIgnoredDirectory(p, folderPath))
                    .filter(p -> isBookFile(p.getFileName().toString()))
                    .filter(this::fileHasContent)
                    .forEach(p -> {
                        try {
                            bookFileTransactionalHandler.handleNewBookFile(library.getId(), p);
                        } catch (Exception e) {
                            log.warn("[ERROR] Processing file '{}': {}", p, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.warn("[ERROR] Walking folder '{}': {}", folderPath, e.getMessage());
        }
    }

    private void processFilesInFolderAsOneBook(LibraryEntity library, Path folderPath) {
        if (Files.exists(folderPath.resolve(".ignore"))) {
            log.debug("[SKIP] Folder has .ignore file: '{}'", folderPath);
            clearTrackedFilesFor(folderPath);
            return;
        }
        clearTrackedFilesFor(folderPath);

        List<Path> bookFiles = new ArrayList<>();
        try (var stream = Files.walk(folderPath)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> !isUnderIgnoredDirectory(p, folderPath))
                    .filter(p -> isBookFile(p.getFileName().toString()))
                    .filter(this::fileHasContent)
                    .forEach(bookFiles::add);
        } catch (IOException e) {
            log.warn("[ERROR] Walking folder '{}': {}", folderPath, e.getMessage());
        }

        if (bookFiles.isEmpty()) {
            return;
        }

        // Try folder-level pool matching first
        Map<Path, String> fileHashes = new HashMap<>();
        for (Path p : bookFiles) {
            try {
                fileHashes.put(p, FileFingerprint.generateHash(p));
            } catch (Exception e) {
                log.warn("[ERROR] Hash computation for '{}': {}", p, e.getMessage());
            }
        }

        Optional<PendingDeletionPool.FolderMatchResult> folderMatch = pendingDeletionPool.matchFolderByHashes(fileHashes);
        if (folderMatch.isPresent()) {
            var match = folderMatch.get();
            String matchedLibPath = bookFilePersistenceService.findMatchingLibraryPath(library, folderPath);
            LibraryPathEntity matchedLibPathEntity = bookFilePersistenceService.getLibraryPathEntityForFile(library, matchedLibPath);

            for (var bookEntry : match.pending().affectedBooks().entrySet()) {
                var bookSnap = bookEntry.getValue();
                bookFilePersistenceService.recoverFolderBook(bookSnap, matchedLibPathEntity, folderPath, fileHashes);
            }
            log.info("[FOLDER_CREATE] Folder '{}' matched pending deletion, recovered {} books",
                    folderPath, match.pending().affectedBooks().size());
            return;
        }

        // Try hash-based move detection for each file
        List<Path> unmatched = new ArrayList<>();
        for (Path p : bookFiles) {
            try {
                String hash = fileHashes.get(p);
                if (hash == null) {
                    unmatched.add(p);
                    continue;
                }

                Optional<PendingDeletionPool.MatchResult> poolMatch = pendingDeletionPool.matchByHash(hash);
                if (poolMatch.isPresent()) {
                    var pmatch = poolMatch.get();
                    String libPath = bookFilePersistenceService.findMatchingLibraryPath(library, p);
                    LibraryPathEntity lpEntity = bookFilePersistenceService.getLibraryPathEntityForFile(library, libPath);
                    String subPath = FileUtils.getRelativeSubPath(lpEntity.getPath(), p);
                    pendingDeletionPool.recoverBook(pmatch, lpEntity, subPath, p.getFileName().toString(), hash);
                    log.info("[FOLDER_CREATE] File '{}' matched pending deletion, recovered book id={}", p, pmatch.book().bookId());
                    continue;
                }

                Optional<BookEntity> existing = bookRepository.findByCurrentHashIncludingRecentlyDeleted(
                        hash, Instant.now().minus(60, ChronoUnit.SECONDS));
                if (existing.isPresent()) {
                    bookFilePersistenceService.updatePathIfChanged(existing.get(), library, p, hash);
                    log.info("[FOLDER_CREATE] File '{}' recognized as moved file, updated existing book's path", p);
                } else {
                    unmatched.add(p);
                }
            } catch (Exception e) {
                log.warn("[ERROR] Hash check for '{}': {}", p, e.getMessage());
                unmatched.add(p);
            }
        }

        if (unmatched.isEmpty()) {
            return;
        }

        String libraryPath = bookFilePersistenceService.findMatchingLibraryPath(library, folderPath);
        LibraryPathEntity libPathEntity = bookFilePersistenceService.getLibraryPathEntityForFile(library, libraryPath);

        List<LibraryFile> libraryFiles = new ArrayList<>();
        for (Path p : unmatched) {
            String fileName = p.getFileName().toString();
            var ext = BookFileExtension.fromFileName(fileName);
            if (ext.isPresent()) {
                libraryFiles.add(LibraryFile.builder()
                        .libraryEntity(library)
                        .libraryPathEntity(libPathEntity)
                        .fileSubPath(FileUtils.getRelativeSubPath(libPathEntity.getPath(), p))
                        .fileName(fileName)
                        .bookFileType(ext.get().getType())
                        .build());
            }
        }

        if (!libraryFiles.isEmpty()) {
            libraryProcessingService.processLibraryFiles(libraryFiles, library);
        }
    }

    private void processTrackedAndWalkedFiles(LibraryEntity library, Path folderPath) {
        var trackedFiles = filesFromPendingFolder.stream()
                .filter(p -> p.startsWith(folderPath))
                .filter(p -> !isUnderIgnoredDirectory(p, folderPath))
                .filter(p -> isBookFile(p.getFileName().toString()))
                .filter(this::fileHasContent)
                .toList();

        if (!trackedFiles.isEmpty()) {
            log.info("[FOLDER_CREATE] Processing {} tracked files individually", trackedFiles.size());
            for (Path filePath : trackedFiles) {
                try {
                    bookFileTransactionalHandler.handleNewBookFile(library.getId(), filePath);
                } catch (Exception e) {
                    log.warn("[ERROR] Processing tracked file '{}': {}", filePath, e.getMessage());
                }
                filesFromPendingFolder.remove(filePath);
            }
        }

        try (var stream = Files.walk(folderPath)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> !isUnderIgnoredDirectory(p, folderPath))
                    .filter(p -> isBookFile(p.getFileName().toString()))
                    .filter(this::fileHasContent)
                    .filter(p -> !trackedFiles.contains(p))
                    .forEach(p -> {
                        try {
                            bookFileTransactionalHandler.handleNewBookFile(library.getId(), p);
                        } catch (Exception e) {
                            log.warn("[ERROR] Processing file '{}': {}", p, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.warn("[ERROR] Walking folder '{}': {}", folderPath, e.getMessage());
        }
    }

    private void clearTrackedFilesFor(Path folderPath) {
        filesFromPendingFolder.removeIf(p -> p.startsWith(folderPath));
    }

    private FolderAnalysis analyzeFolderForAudiobook(Path folderPath) {
        List<Path> audioFiles = new ArrayList<>();
        boolean hasNonAudioBook = false;

        try (var stream = Files.walk(folderPath)) {
            var files = stream.filter(Files::isRegularFile)
                    .filter(p -> !isUnderIgnoredDirectory(p, folderPath))
                    .filter(this::fileHasContent)
                    .toList();

            for (Path file : files) {
                String fileName = file.getFileName().toString();
                var ext = BookFileExtension.fromFileName(fileName);
                if (ext.isPresent()) {
                    if (ext.get().getType() == BookFileType.AUDIOBOOK) {
                        audioFiles.add(file);
                    } else {
                        hasNonAudioBook = true;
                    }
                }
            }
        } catch (IOException e) {
            log.warn("[ERROR] Analyzing folder '{}': {}", folderPath, e.getMessage());
        }

        boolean isSeriesFolder = audioFiles.size() >= MIN_AUDIO_FILES_FOR_FOLDER_AUDIOBOOK
                && FileUtils.isSeriesFolder(audioFiles);

        return new FolderAnalysis(audioFiles.size(), hasNonAudioBook, isSeriesFolder);
    }

    private record FolderAnalysis(int audioFileCount, boolean hasNonAudioBook, boolean isSeriesFolder) {
        boolean isFolderBasedAudiobook() {
            return audioFileCount >= MIN_AUDIO_FILES_FOR_FOLDER_AUDIOBOOK && !hasNonAudioBook && !isSeriesFolder;
        }
    }

    private void handleFolderDelete(LibraryEntity library, Path folderPath) {
        log.info("[FOLDER_DELETE] '{}'", folderPath);
        try {
            String libPath = bookFilePersistenceService.findMatchingLibraryPath(library, folderPath);
            LibraryPathEntity libPathEntity = bookFilePersistenceService.getLibraryPathEntityForFile(library, libPath);

            String relativePrefix = Path.of(libPathEntity.getPath()).relativize(folderPath).toString().replace("\\", "/");
            List<BookEntity> booksWithFiles = bookRepository.findBooksWithFilesUnderPath(libPathEntity.getId(), relativePrefix);

            if (booksWithFiles.isEmpty()) {
                log.debug("[FOLDER_DELETE] No books found under '{}'", folderPath);
                return;
            }

            ScheduledFuture<?> timer = scheduler.schedule(
                    () -> pendingDeletionPool.expireFolderDeletion(folderPath),
                    PENDING_DELETION_GRACE_MS, TimeUnit.MILLISECONDS);
            pendingDeletionPool.addFolderDeletion(folderPath, library.getId(), booksWithFiles, timer);
            log.info("[PENDING_DELETE] {} books under '{}' deferred deletion (grace period {}ms)",
                    booksWithFiles.size(), folderPath, PENDING_DELETION_GRACE_MS);
        } catch (Exception e) {
            log.warn("[ERROR] Folder delete '{}': {}", folderPath, e.getMessage());
        }
    }

    private boolean fileHasContent(Path path) {
        try {
            return Files.size(path) > 0;
        } catch (IOException e) {
            return false;
        }
    }

    private boolean isUnderIgnoredDirectory(Path filePath, Path root) {
        Path parent = filePath.getParent();
        while (parent != null && !parent.equals(root)) {
            if (Files.exists(parent.resolve(".ignore"))) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }

    private boolean isBookFile(String fileName) {
        return BookFileExtension.fromFileName(fileName).isPresent();
    }

    private void scheduleWithStabilityCheck(Path path, Runnable onStable) {
        scheduler.schedule(() -> checkStability(path, onStable, System.currentTimeMillis()), 0, TimeUnit.MILLISECONDS);
    }

    private void checkStability(Path path, Runnable onStable, long startTime) {
        try {
            if (!Files.exists(path)) {
                onStable.run();
                return;
            }

            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed >= STABILITY_MAX_WAIT_MS) {
                log.warn("[STABILITY] Max wait exceeded for '{}', processing anyway", path);
                onStable.run();
                return;
            }

            FileTime mtime = Files.getLastModifiedTime(path);
            long msSinceMod = System.currentTimeMillis() - mtime.toMillis();

            if (msSinceMod < STABILITY_CHECK_INTERVAL_MS) {
                log.debug("[STABILITY] File '{}' modified {}ms ago, rechecking in {}ms", path, msSinceMod, STABILITY_CHECK_INTERVAL_MS);
                scheduler.schedule(() -> checkStability(path, onStable, startTime),
                        STABILITY_CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);
            } else {
                onStable.run();
            }
        } catch (IOException e) {
            log.debug("[STABILITY] Error checking mtime for '{}': {}", path, e.getMessage());
            onStable.run();
        }
    }

    public boolean hasPendingEventsForPaths(Set<Path> paths) {
        boolean inQueue = eventQueue.stream()
                .anyMatch(e -> paths.stream().anyMatch(p -> e.fullPath().startsWith(p)));
        boolean inDeletes = pendingDeletes.keySet().stream()
                .anyMatch(p -> paths.stream().anyMatch(p::startsWith));
        boolean inFolderCreates = pendingFolderCreates.keySet().stream()
                .anyMatch(p -> paths.stream().anyMatch(p::startsWith));
        boolean inPool = pendingDeletionPool.hasPendingForPaths(paths);
        return inQueue || inDeletes || inFolderCreates || inPool;
    }

    public record FileEvent(WatchEvent.Kind<?> eventKind, long libraryId, Path fullPath, boolean isDirectory) {
    }
}
