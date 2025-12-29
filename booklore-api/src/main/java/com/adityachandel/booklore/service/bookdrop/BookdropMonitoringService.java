package com.adityachandel.booklore.service.bookdrop;

import com.adityachandel.booklore.config.AppProperties;
import com.adityachandel.booklore.model.enums.BookFileExtension;
import com.adityachandel.booklore.util.FileUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.stream.Stream;

@Slf4j
@Service
public class BookdropMonitoringService {

    private final AppProperties appProperties;
    private final BookdropEventHandlerService eventHandler;

    private Path bookdrop;
    private WatchService watchService;
    private Thread watchThread;
    private volatile boolean running;
    private WatchKey watchKey;
    private volatile boolean paused;

    public BookdropMonitoringService(AppProperties appProperties, BookdropEventHandlerService eventHandler) {
        this.appProperties = appProperties;
        this.eventHandler = eventHandler;
    }

    @PostConstruct
    public void start() throws IOException {
        bookdrop = Path.of(appProperties.getBookdropFolder());
        if (Files.notExists(bookdrop)) {
            try {
                Files.createDirectories(bookdrop);
                log.info("Created missing bookdrop folder: {}", bookdrop);
            } catch (IOException e) {
                log.error("Failed to create bookdrop folder: {}", bookdrop, e);
                throw e;
            }
        }

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

    public synchronized void pauseMonitoring() {
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
    }

    public synchronized void resumeMonitoring() {
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
        log.info("Manual rescan of Bookdrop folder triggered.");
        scanExistingBookdropFiles();
    }

    private void scanExistingBookdropFiles() {
        try (Stream<Path> files = Files.walk(bookdrop)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> !FileUtils.shouldIgnore(path))
                    .filter(path -> BookFileExtension.fromFileName(path.getFileName().toString()).isPresent())
                    .forEach(file -> {
                        log.info("Found existing supported file on startup: {}", file);
                        eventHandler.enqueueFile(file, StandardWatchEventKinds.ENTRY_CREATE);
                    });
        } catch (IOException e) {
            log.error("Error scanning bookdrop folder on startup", e);
        }
    }
}