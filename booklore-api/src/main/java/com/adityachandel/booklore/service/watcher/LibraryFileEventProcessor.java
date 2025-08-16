package com.adityachandel.booklore.service.watcher;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.enums.BookFileExtension;
import com.adityachandel.booklore.model.enums.PermissionType;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.service.FileFingerprint;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.util.FileUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.*;

@Slf4j
@Service
@AllArgsConstructor
public class LibraryFileEventProcessor {

    private final BlockingQueue<FileEvent> eventQueue = new LinkedBlockingQueue<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final LibraryRepository libraryRepository;
    private final BookFileTransactionalHandler bookFileTransactionalHandler;
    private final BookFilePersistenceService bookFilePersistenceService;
    private final NotificationService notificationService;

    @PostConstruct
    public void init() {
        Thread.ofVirtual().start(() -> {
            log.info("LibraryFileEventProcessor virtual thread started.");
            while (!Thread.currentThread().isInterrupted()) {
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

    public void processFile(WatchEvent.Kind<?> eventKind, long libraryId, String libraryPath, String filePath) {
        eventQueue.offer(new FileEvent(eventKind, libraryId, libraryPath, filePath));
    }

    private void handleEvent(FileEvent event) {
        Path path = Paths.get(event.filePath());
        String fileName = path.getFileName().toString();
        log.info("[PROCESS] '{}' event for '{}'", event.eventKind().name(), fileName);

        LibraryEntity library = libraryRepository.findById(event.libraryId()).orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(event.libraryId()));

        if (library.getLibraryPaths().stream().noneMatch(lp -> path.toString().startsWith(lp.getPath()))) {
            log.warn("[SKIP] Path outside of library: '{}'", path);
            return;
        }

        if (isFolder(path)) {
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
        log.info("[FILE_CREATE] '{}'", path);
        String hash = FileFingerprint.generateHash(path);
        bookFileTransactionalHandler.handleNewBookFile(library.getId(), path, hash);
    }

    private void handleFileDelete(LibraryEntity library, Path path) {
        log.info("[FILE_DELETE] '{}'", path);
        try {
            String libPath = bookFilePersistenceService.findMatchingLibraryPath(library, path);
            LibraryPathEntity libPathEntity = bookFilePersistenceService.getLibraryPathEntityForFile(library, libPath);

            Path relPath = Paths.get(libPathEntity.getPath()).relativize(path.toAbsolutePath().normalize());
            String fileName = relPath.getFileName().toString();
            String fileSubPath = Optional.ofNullable(relPath.getParent()).map(Path::toString).orElse("");

            bookFilePersistenceService.findByLibraryPathSubPathAndFileName(libPathEntity.getId(), fileSubPath, fileName)
                    .ifPresentOrElse(book -> {
                        book.setDeleted(true);
                        bookFilePersistenceService.save(book);
                        notificationService.sendMessageToPermissions(Topic.BOOKS_REMOVE, Set.of(book.getId()), Set.of(PermissionType.ADMIN, PermissionType.MANIPULATE_LIBRARY));
                        log.info("[MARKED_DELETED] Book '{}' marked as deleted", fileName);
                    }, () -> log.warn("[NOT_FOUND] Book for deleted path '{}' not found", path));

        } catch (Exception e) {
            log.warn("[ERROR] While handling file delete '{}': {}", path, e.getMessage());
        }
    }

    private void handleFolderCreate(LibraryEntity library, Path folderPath) {
        log.info("[FOLDER_CREATE] '{}'", folderPath);
        try (var stream = Files.walk(folderPath)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> isBookFile(p.getFileName().toString()))
                    .forEach(p -> {
                        try {
                            String hash = FileFingerprint.generateHash(p);
                            bookFileTransactionalHandler.handleNewBookFile(library.getId(), p, hash);
                        } catch (Exception e) {
                            log.warn("[ERROR] Processing file '{}': {}", p, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.warn("[ERROR] Walking folder '{}': {}", folderPath, e.getMessage());
        }
    }

    private void handleFolderDelete(LibraryEntity library, Path folderPath) {
        log.info("[FOLDER_DELETE] '{}'", folderPath);
        try {
            String libPath = bookFilePersistenceService.findMatchingLibraryPath(library, folderPath);
            LibraryPathEntity libPathEntity = bookFilePersistenceService.getLibraryPathEntityForFile(library, libPath);

            String relativePrefix = FileUtils.getRelativeSubPath(libPathEntity.getPath(), folderPath);
            int count = bookFilePersistenceService.markAllBooksUnderPathAsDeleted(libPathEntity.getId(), relativePrefix);
            log.info("[MARKED_DELETED] {} books under '{}'", count, folderPath);
        } catch (Exception e) {
            log.warn("[ERROR] Folder delete '{}': {}", folderPath, e.getMessage());
        }
    }

    private boolean isFolder(Path path) {
        return !path.getFileName().toString().contains(".");
    }

    private boolean isBookFile(String fileName) {
        return BookFileExtension.fromFileName(fileName).isPresent();
    }

    @PreDestroy
    public void shutdown() {
        worker.shutdownNow();
        log.info("LibraryFileEventProcessor worker shutdown.");
    }

    public record FileEvent(WatchEvent.Kind<?> eventKind, long libraryId, String libraryPath, String filePath) {
    }
}