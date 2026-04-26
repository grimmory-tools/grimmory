package org.grimmory.service.bookdrop;

import org.grimmory.model.BookDropFileEvent;
import org.grimmory.model.entity.BookdropFileEntity;
import org.grimmory.model.enums.BookFileExtension;
import org.grimmory.model.enums.PermissionType;
import org.grimmory.model.websocket.LogNotification;
import org.grimmory.model.websocket.Topic;
import org.grimmory.repository.BookdropFileRepository;
import org.grimmory.service.NotificationService;
import org.grimmory.service.appsettings.AppSettingService;
import lombok.RequiredArgsConstructor;
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
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
@Service
public class BookdropEventHandlerService implements SmartLifecycle {

    private static final int LIFECYCLE_PHASE = 10;

    private final BookdropFileRepository bookdropFileRepository;
    private final NotificationService notificationService;
    private final BookdropNotificationService bookdropNotificationService;
    private final AppSettingService appSettingService;
    private final BookdropMetadataService bookdropMetadataService;

    private static final long STABILITY_CHECK_INTERVAL_MS = 500;
    private static final int STABILITY_REQUIRED_CHECKS = 3;
    private static final long STABILITY_MAX_WAIT_MS = 30_000;

    private final BlockingQueue<BookDropFileEvent> fileQueue = new LinkedBlockingQueue<>();
    private volatile boolean running;
    private Thread workerThread;

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
                processFile(fileQueue.take());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("File processing thread interrupted, shutting down.");
            }
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

                String filePath = file.toAbsolutePath().toString();
                String fileName = file.getFileName().toString();

                if (BookFileExtension.fromFileName(fileName).isEmpty()) {
                    log.info("Unsupported file type detected, ignoring file: {}", fileName);
                    return;
                }

                if (bookdropFileRepository.findByFilePath(filePath).isPresent()) {
                    log.info("File already exists in Bookdrop and is pending review or acceptance: {}", filePath);
                    return;
                }

                if (!waitForFileStability(file)) {
                    log.warn("File did not stabilize within timeout, skipping: {}", file);
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
            }

        } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
            String deletedPath = file.toAbsolutePath().toString();
            log.info("Detected deletion event: {}", deletedPath);

            int deletedCount = bookdropFileRepository.deleteAllByFilePathStartingWith(deletedPath);
            log.info("Deleted {} BookdropFile record(s) from database matching path: {}", deletedCount, deletedPath);

            bookdropNotificationService.sendBookdropFileSummaryNotification();
        }
    }

    private boolean waitForFileStability(Path file) {
        long startTime = System.currentTimeMillis();
        long lastSize = -1;
        int stableCount = 0;

        while (System.currentTimeMillis() - startTime < STABILITY_MAX_WAIT_MS) {
            try {
                if (!Files.exists(file)) {
                    return false;
                }

                long currentSize = Files.size(file);

                if (currentSize == lastSize && currentSize > 0) {
                    stableCount++;
                    if (stableCount >= STABILITY_REQUIRED_CHECKS) {
                        return true;
                    }
                } else {
                    stableCount = 0;
                }

                lastSize = currentSize;
                Thread.sleep(STABILITY_CHECK_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } catch (IOException e) {
                log.warn("Error checking file size for stability: {}", file, e);
                return false;
            }
        }

        log.warn("File size did not stabilize after {}ms: {}", STABILITY_MAX_WAIT_MS, file);
        return false;
    }
}