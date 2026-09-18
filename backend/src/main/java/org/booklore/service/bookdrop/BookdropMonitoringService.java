package org.booklore.service.bookdrop;

import lombok.Getter;
import org.booklore.config.AppProperties;
import org.booklore.model.enums.BookFileExtension;
import org.booklore.repository.BookdropFileRepository;
import org.booklore.util.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
public class BookdropMonitoringService implements SmartLifecycle {

    private static final int LIFECYCLE_PHASE = 20;

    private final AppProperties appProperties;
    private final BookdropEventHandlerService eventHandler;
    private final BookdropFileRepository bookdropFileRepository;

    private Path bookdrop;
    private WatchService watchService;
    private Thread watchThread;
    private volatile boolean running;
    private WatchKey watchKey;
    private volatile boolean paused;
    private volatile boolean disabled;
    private final Lock monitorLock = new ReentrantLock();

    public BookdropMonitoringService(
            AppProperties appProperties,
            BookdropEventHandlerService eventHandler,
            BookdropFileRepository bookdropFileRepository
    ) {
        this.appProperties = appProperties;
        this.eventHandler = eventHandler;
        this.bookdropFileRepository = bookdropFileRepository;
    }

    @Override
    public void start() {
        bookdrop = Path.of(appProperties.getBookdropFolder());
        if (Files.notExists(bookdrop)) {
            try {
                Files.createDirectories(bookdrop);
                log.info("Created missing bookdrop folder: {}", bookdrop);
            } catch (IOException e) {
                log.warn("Bookdrop folder is not available at '{}'. Bookdrop monitoring is disabled. " +
                        "Mount a volume at this path to enable it.", bookdrop);
                this.disabled = true;
                return;
            }
        }

        try {
            log.info("Starting bookdrop folder monitor: {}", bookdrop);
            this.watchService = FileSystems.getDefault().newWatchService();
            this.watchKey = bookdrop.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE);
            this.running = true;
            this.paused = false;
            this.watchThread = new Thread(this::processEvents, "BookdropFolderWatcher");
            this.watchThread.setDaemon(true);
            this.watchThread.start();
            this.disabled = false;
        } catch (IOException e) {
            log.warn("Failed to start bookdrop folder monitor. Bookdrop monitoring is disabled.", e);
            this.disabled = true;
        }
    }

    @Override
    public void stop() {
        stop(() -> {});
    }

    @Override
    public void stop(Runnable callback) {
        log.info("Stopping bookdrop folder monitor...");
        running = false;
        if (watchThread != null) {
            watchThread.interrupt();
            try {
                watchThread.join(5000);
            } catch (InterruptedException e) {
                log.warn("Interrupted while waiting for watchThread to stop");
                Thread.currentThread().interrupt();
            }
        }
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                log.error("Error closing WatchService", e);
            }
        }
        log.info("Stopped bookdrop folder monitor");
        callback.run();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return LIFECYCLE_PHASE;
    }

    public void pauseMonitoring() {
        if (disabled) return;
        monitorLock.lock();
        try {
            if (!paused) {
                if (watchKey != null) {
                    watchKey.cancel();
                    watchKey = null;
                }
                paused = true;
                log.info("Bookdrop monitoring paused.");
            } else {
                log.info("Bookdrop monitoring already paused.");
            }
        } finally {
            monitorLock.unlock();
        }
    }

    public void resumeMonitoring() {
        if (disabled) return;
        monitorLock.lock();
        try {
            if (paused) {
                try {
                    watchKey = bookdrop.register(watchService,
                            StandardWatchEventKinds.ENTRY_CREATE,
                            StandardWatchEventKinds.ENTRY_DELETE);
                    paused = false;
                    log.info("Bookdrop monitoring resumed.");
                } catch (IOException e) {
                    log.error("Error reregistering bookdrop folder during resume", e);
                }
            } else {
                log.info("Bookdrop monitoring is not paused, cannot resume.");
            }
        } finally {
            monitorLock.unlock();
        }
    }

    private void processEvents() {
        try {
            scanExistingBookdropFiles();
        } catch (Exception e) {
            log.error("Failed to scan existing bookdrop files", e);
        }

        while (running) {
            if (paused) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    log.info("Bookdrop monitor thread interrupted during pause");
                    Thread.currentThread().interrupt();
                    return;
                }
                continue;
            }

            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException e) {
                log.info("Bookdrop monitor thread interrupted");
                Thread.currentThread().interrupt();
                return;
            } catch (ClosedWatchServiceException e) {
                log.info("WatchService closed, stopping thread");
                return;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                try {
                    processEvent(event);
                } catch (Exception e) {
                    log.error("Failed to process {} event", event.kind(), e);
                }
            }

            boolean valid = key.reset();
            if (!valid) {
                log.warn("WatchKey is no longer valid");
                break;
            }
        }
    }

    private void processEvent(WatchEvent<?> event) {
        WatchEvent.Kind<?> kind = event.kind();

        if (kind == StandardWatchEventKinds.OVERFLOW) {
            log.warn("Overflow event detected");
            return;
        }

        if (event.context() instanceof Path context) {
            Path fullPath = bookdrop.resolve(context);

            log.info("Detected {} event on: {}", kind.name(), fullPath);

            if (kind == StandardWatchEventKinds.ENTRY_CREATE || kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                var filePaths = getSupportedBookdropFiles(fullPath);

                for (var path : filePaths) {
                    eventHandler.enqueueFile(path, kind);
                }
            } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                if (Files.isDirectory(fullPath)) {
                    log.info("Directory deleted: {}, performing bulk DB cleanup", fullPath);
                } else {
                    log.info("File deleted: {}", fullPath);
                }
                eventHandler.enqueueFile(fullPath, kind);
            }
        }
    }

    public void rescanBookdropFolder() {
        if (disabled) {
            log.warn("Bookdrop monitoring is disabled. Skipping rescan.");
            return;
        }
        log.info("Rescan of Bookdrop folder triggered.");
        scanExistingBookdropFiles();
    }

    private static class BookdropFileVisitor implements FileVisitor<Path> {
        @Getter
        private final Set<Path> visitedFiles = new HashSet<>();

        @Override
        @NotNull
        public FileVisitResult preVisitDirectory(Path path, @NotNull BasicFileAttributes basicFileAttributes) {
            if (!Files.isReadable(path)) {
                return FileVisitResult.SKIP_SUBTREE;
            }

            return FileVisitResult.CONTINUE;
        }

        public FileVisitResult visitFile(Path path) {
            if (!Files.isReadable(path) || !Files.isRegularFile(path)) {
                log.debug("Bookdrop file is not a readable regular file, skipping: {}", path);
                return FileVisitResult.CONTINUE;
            }

            if (FileUtils.shouldIgnore(path)) {
                log.debug("Bookdrop file is ignored: {}", path);
                return FileVisitResult.CONTINUE;
            }

            if (BookFileExtension.fromFileName(path.getFileName().toString()).isEmpty()) {
                log.debug("Bookdrop file is not supported: {}", path);
                return FileVisitResult.CONTINUE;
            }

            visitedFiles.add(path);
            return FileVisitResult.CONTINUE;
        }

        @Override
        @NotNull
        public FileVisitResult visitFile(Path path, @NotNull BasicFileAttributes basicFileAttributes) {
            return visitFile(path);
        }

        @Override
        @NotNull
        public FileVisitResult visitFileFailed(Path path, @NotNull IOException e) {
            log.error("Failed to read path in bookdrop: {}", path, e);
            return FileVisitResult.CONTINUE;
        }

        @Override
        @NotNull
        public FileVisitResult postVisitDirectory(Path path, @Nullable IOException e) {
            return FileVisitResult.CONTINUE;
        }
    }

    private List<Path> getSupportedBookdropFiles(Path path) {
        // Given that individual folders may have permissions issues, we can't use the `Files.walk` helper.
        // Instead, we can use a file visitor that ignores exceptions and tracks files.
        log.info("New directory detected, scanning recursively: {}", path);
        var visitor = new BookdropFileVisitor();

        if (Files.isDirectory(path)) {
            try {
                Files.walkFileTree(path, visitor);

            } catch (IOException e) {
                log.error("Failed to scan new directory: {}", path, e);
            }
        } else {
            visitor.visitFile(path);
        }

        return List.copyOf(visitor.getVisitedFiles());
    }

    private void scanExistingBookdropFiles() {
        List<Path> supportedFiles = getSupportedBookdropFiles(bookdrop);

        if (!supportedFiles.isEmpty()) {
            List<String> supportedFilePaths = supportedFiles.stream()
                    .map(Path::toAbsolutePath)
                    .map(Path::toString)
                    .toList();
            List<String> knownFilePaths = bookdropFileRepository.findAllFilePathsIn(supportedFilePaths);
            Set<String> knownPaths = knownFilePaths == null ? Set.of() : new HashSet<>(knownFilePaths);

            supportedFilePaths.stream()
                    .filter(path -> !knownPaths.contains(path))
                    .map(Path::of)
                    .forEach(path -> {
                        log.info("Found existing supported file: {}", path);
                        eventHandler.enqueueFile(path, StandardWatchEventKinds.ENTRY_CREATE);
                    });
        }
    }
}
