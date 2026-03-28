package org.booklore.service.bookdrop;

import org.booklore.config.AppProperties;
import org.booklore.model.enums.BookFileExtension;
import org.booklore.repository.BookdropFileRepository;
import org.booklore.util.FileUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

@Slf4j
@Service
public class BookdropMonitoringService {

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

    @PostConstruct
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
            scanExistingBookdropFiles();
        } catch (IOException e) {
            log.warn("Failed to start bookdrop folder monitor. Bookdrop monitoring is disabled.", e);
            this.disabled = true;
        }
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (watchThread != null) {
            watchThread.interrupt();
        }
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                log.error("Error closing WatchService", e);
            }
        }
        log.info("Stopped bookdrop folder monitor");
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
                WatchEvent.Kind<?> kind = event.kind();

                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    log.warn("Overflow event detected");
                    continue;
                }

                Path context = (Path) event.context();
                Path fullPath = bookdrop.resolve(context);

                log.info("Detected {} event on: {}", kind.name(), fullPath);

                if (kind == StandardWatchEventKinds.ENTRY_CREATE || kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                    if (Files.isDirectory(fullPath)) {
                        log.info("New directory detected, scanning recursively: {}", fullPath);
                        try (Stream<Path> pathStream = Files.walk(fullPath)) {
                            pathStream
                                    .filter(Files::isRegularFile)
                                    .filter(path -> !FileUtils.shouldIgnore(path))
                                    .filter(path -> BookFileExtension.fromFileName(path.getFileName().toString()).isPresent())
                                    .forEach(path -> eventHandler.enqueueFile(path, StandardWatchEventKinds.ENTRY_CREATE));
                        } catch (IOException e) {
                            log.error("Failed to scan new directory: {}", fullPath, e);
                        }
                    } else {
                        if (!FileUtils.shouldIgnore(fullPath)) {
                            if (BookFileExtension.fromFileName(fullPath.getFileName().toString()).isPresent()) {
                                eventHandler.enqueueFile(fullPath, kind);
                            } else {
                                log.info("Ignored unsupported file type: {}", fullPath);
                            }
                        }
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

            boolean valid = key.reset();
            if (!valid) {
                log.warn("WatchKey is no longer valid");
                break;
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

    private void scanExistingBookdropFiles() {
        List<Path> supportedFiles;
        try (Stream<Path> files = Files.walk(bookdrop)) {
            supportedFiles = files.filter(Files::isRegularFile)
                    .filter(path -> !FileUtils.shouldIgnore(path))
                    .filter(path -> BookFileExtension.fromFileName(path.getFileName().toString()).isPresent())
                    .toList();
        } catch (IOException e) {
            log.error("Error scanning bookdrop folder", e);
            return;
        }

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
