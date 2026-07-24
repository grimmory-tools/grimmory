package org.booklore.service.bookdrop;

import org.booklore.model.BookDropFileEvent;
import org.booklore.model.entity.BookdropFileEntity;
import org.booklore.model.enums.BookFileExtension;
import org.booklore.model.enums.PermissionType;
import org.booklore.model.websocket.LogNotification;
import org.booklore.model.websocket.Topic;
import org.booklore.repository.BookdropFileRepository;
import org.booklore.service.NotificationService;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.util.PathNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class BookdropEventHandlerService implements SmartLifecycle {

    private static final int LIFECYCLE_PHASE = 10;

    private final BookdropFileRepository bookdropFileRepository;
    private final NotificationService notificationService;
    private final BookdropNotificationService bookdropNotificationService;
    private final AppSettingService appSettingService;
    private final BookdropMetadataService bookdropMetadataService;

    private final BlockingQueue<BookDropFileEvent> fileQueue = new LinkedBlockingQueue<>();
    private volatile boolean running;
    private Thread workerThread;

    // 2026 Standard: Throttle parallel metadata lookups to avoid overwhelming external providers
    private final Semaphore metadataTaskSemaphore = new Semaphore(10);

    // 2026 Standard: Non-blocking scheduled delay for file stability checks
    private final ScheduledExecutorService delayScheduler = Executors.newScheduledThreadPool(2);

    public BookdropEventHandlerService(
            BookdropFileRepository bookdropFileRepository,
            NotificationService notificationService,
            BookdropNotificationService bookdropNotificationService,
            AppSettingService appSettingService,
            BookdropMetadataService bookdropMetadataService) {
        this.bookdropFileRepository = bookdropFileRepository;
        this.notificationService = notificationService;
        this.bookdropNotificationService = bookdropNotificationService;
        this.appSettingService = appSettingService;
        this.bookdropMetadataService = bookdropMetadataService;
    }

    @Override
    public void start() {
        running = true;
        workerThread = new Thread(this::processQueue, "BookdropFileProcessor");
        workerThread.start();
    }

    @Override
    public void stop() {
        stop(() -> {});
    }

    @Override
    public void stop(Runnable callback) {
        log.info("Stopping BookdropEventHandlerService...");
        running = false;
        if (workerThread != null) {
            workerThread.interrupt();
            try {
                workerThread.join(5000);
            } catch (InterruptedException e) {
                log.warn("Interrupted while waiting for BookdropEventHandlerService workerThread to stop");
                Thread.currentThread().interrupt();
            }
        }
        log.info("Stopped BookdropEventHandlerService");
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

    public void enqueueFile(Path file, WatchEvent.Kind<?> kind) {
        BookDropFileEvent event = new BookDropFileEvent(file, kind);
        if (!fileQueue.contains(event)) {
            fileQueue.offer(event);
        }
    }

    private void processQueue() {
        while (running) {
            try {
                processFileThrottled(fileQueue.take());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("File processing thread interrupted, shutting down.");
            }
        }
    }

    /**
     * Semaphore-throttled wrapper around {@link #processFile(BookDropFileEvent)}.
     * Limits concurrent metadata lookups to prevent resource exhaustion.
     */
    private void processFileThrottled(BookDropFileEvent event) {
        try {
            metadataTaskSemaphore.acquire();
            processFile(event);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for metadata processing slot", e);
        } finally {
            metadataTaskSemaphore.release();
        }
    }

    public void processFile(BookDropFileEvent event) {
        Path file = event.getFile();
        WatchEvent.Kind<?> kind = event.getKind();

        if (kind == StandardWatchEventKinds.ENTRY_CREATE || kind == StandardWatchEventKinds.ENTRY_MODIFY) {
            try {
                if (!Files.exists(file)) {
                    log.warn("File does not exist, ignoring: {}", file);
                    return;
                }

                if (Files.isDirectory(file)) {
                    log.info("New folder detected in bookdrop, ignoring: {}", file);
                    return;
                }

                // 2026 Standard: Apply NFC Unicode normalization for consistent
                // path matching across macOS (NFD), Linux (NFC), and web uploads.
                String filePath = PathNormalizer.normalizePathString(file.toAbsolutePath().toString());
                String fileName = PathNormalizer.normalizeFileName(file.getFileName().toString());

                if (BookFileExtension.fromFileName(fileName).isEmpty()) {
                    log.info("Unsupported file type detected, ignoring file: {}", fileName);
                    return;
                }

                if (bookdropFileRepository.findByFilePath(filePath).isPresent()) {
                    log.info("File already exists in Bookdrop and is pending review or acceptance: {}", filePath);
                    return;
                }

                // 2026 Standard: Non-blocking stability check — if the file was recently
                // modified, re-enqueue with a delay instead of blocking the worker thread.
                if (!isFileStableForProcessing(event)) {
                    return;
                }

                log.info("Handling new bookdrop file: {}", file);

                int queueSize = fileQueue.size();
                notificationService.sendMessageToPermissions(
                        Topic.LOG,
                        LogNotification.info("Processing bookdrop file: " + fileName + " (" + queueSize + " files remaining)"),
                        Set.of(PermissionType.ADMIN, PermissionType.MANAGE_LIBRARY)
                );

                BookdropFileEntity bookdropFileEntity = BookdropFileEntity.builder()
                        .filePath(filePath)
                        .fileName(fileName)
                        .fileSize(Files.size(file))
                        .status(BookdropFileEntity.Status.PENDING_REVIEW)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();

                bookdropFileEntity = bookdropFileRepository.save(bookdropFileEntity);

                if (appSettingService.getAppSettings().isMetadataDownloadOnBookdrop()) {
                    bookdropMetadataService.attachInitialMetadata(bookdropFileEntity.getId());
                    bookdropMetadataService.attachFetchedMetadata(bookdropFileEntity.getId());
                } else {
                    bookdropMetadataService.attachInitialMetadata(bookdropFileEntity.getId());
                    log.info("Metadata download is disabled. Only initial metadata extracted for file: {}", bookdropFileEntity.getFileName());
                }

                bookdropNotificationService.sendBookdropFileSummaryNotification();

                if (fileQueue.isEmpty()) {
                    notificationService.sendMessageToPermissions(
                            Topic.LOG,
                            LogNotification.info("All bookdrop files have finished processing"),
                            Set.of(PermissionType.ADMIN, PermissionType.MANAGE_LIBRARY)
                    );
                } else {
                    notificationService.sendMessageToPermissions(
                            Topic.LOG,
                            LogNotification.info("Finished processing bookdrop file: " + fileName + " (" + fileQueue.size() + " files remaining)"),
                            Set.of(PermissionType.ADMIN, PermissionType.MANAGE_LIBRARY)
                    );
                }

            } catch (Exception e) {
                log.error("Error handling bookdrop file: {}", file, e);

                // 2026 Standard: Persist error state for UI visibility
                String errorFilePath = PathNormalizer.normalizePathString(file.toAbsolutePath().toString());
                bookdropFileRepository.findByFilePath(errorFilePath).ifPresent(entity -> {
                    entity.setStatus(BookdropFileEntity.Status.ERROR);
                    entity.setErrorMessage(e.getMessage());
                    bookdropFileRepository.save(entity);
                });

                notificationService.sendMessageToPermissions(
                        Topic.LOG,
                        LogNotification.error("Failed to process bookdrop file: " + file.getFileName() + " - " + e.getMessage()),
                        Set.of(PermissionType.ADMIN, PermissionType.MANAGE_LIBRARY)
                );
            }

        } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
            String deletedPath = file.toAbsolutePath().toString();
            log.info("Detected deletion event: {}", deletedPath);

            int deletedCount = bookdropFileRepository.deleteAllByFilePathStartingWith(deletedPath);
            log.info("Deleted {} BookdropFile record(s) from database matching path: {}", deletedCount, deletedPath);

            bookdropNotificationService.sendBookdropFileSummaryNotification();
        }
    }

    /**
     * Non-blocking stability check: if the file was modified within the last 1.5 seconds,
     * it is likely still being written. Re-enqueue with a 1-second delay instead of
     * blocking the worker thread for up to 30 seconds.
     *
     * @param event the original file event to re-enqueue if unstable
     * @return true if the file is stable and ready for processing
     */
    private boolean isFileStableForProcessing(BookDropFileEvent event) {
        Path file = event.getFile();
        try {
            long lastModified = Files.getLastModifiedTime(file).toMillis();
            long now = System.currentTimeMillis();

            // File was modified within the last 1.5s — still being written
            if (now - lastModified < 1500) {
                log.info("File recently modified, re-enqueuing with delay: {}", file);
                delayScheduler.schedule(() -> enqueueFile(file, event.getKind()), 1, TimeUnit.SECONDS);
                return false;
            }

            return true;
        } catch (IOException e) {
            log.warn("Could not check last modified time for file: {}", file, e);
            // Proceed if we cannot check — let the downstream logic handle issues
            return true;
        }
    }
}