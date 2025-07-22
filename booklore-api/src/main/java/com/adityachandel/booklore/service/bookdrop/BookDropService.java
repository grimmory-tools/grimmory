package com.adityachandel.booklore.service.bookdrop;

import com.adityachandel.booklore.config.AppProperties;
import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.request.BookdropFinalizeRequest;
import com.adityachandel.booklore.model.dto.response.BookdropFinalizeResult;
import com.adityachandel.booklore.model.dto.response.BookdropFileResult;
import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.*;
import com.adityachandel.booklore.model.enums.*;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.*;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.fileprocessor.*;
import com.adityachandel.booklore.service.metadata.MetadataRefreshService;
import com.adityachandel.booklore.service.monitoring.*;
import com.adityachandel.booklore.util.FileUtils;
import com.adityachandel.booklore.util.PathPatternResolver;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@Slf4j
@AllArgsConstructor
@Service
public class BookDropService {

    private final BookdropFileRepository bookdropFileRepository;
    private final LibraryRepository libraryRepository;
    private final BookRepository bookRepository;
    private final MonitoringService monitoringService;
    private final BookdropMonitoringService bookdropMonitoringService;
    private final NotificationService notificationService;
    private final MetadataRefreshService metadataRefreshService;
    private final BookdropNotificationService bookdropNotificationService;
    private final PdfProcessor pdfProcessor;
    private final EpubProcessor epubProcessor;
    private final CbxProcessor cbxProcessor;
    private final AppProperties appProperties;

    public BookdropFinalizeResult finalizeImport(BookdropFinalizeRequest request) {
        boolean monitoringWasActive = !monitoringService.isPaused();
        if (monitoringWasActive) monitoringService.pauseMonitoring();
        bookdropMonitoringService.pauseMonitoring();

        BookdropFinalizeResult results = BookdropFinalizeResult.builder().build();

        for (BookdropFinalizeRequest.BookdropFinalizeFile fileReq : request.getFiles()) {
            try {
                BookdropFileEntity fileEntity = bookdropFileRepository.findById(fileReq.getFileId()).orElseThrow(() -> ApiError.FILE_NOT_FOUND.createException(fileReq.getFileId()));

                BookdropFileResult result = moveFile(
                        fileReq.getLibraryId(),
                        fileReq.getPathId(),
                        request.getUploadPattern(),
                        fileReq.getMetadata(),
                        fileEntity
                );
                results.getResults().add(result);
            } catch (Exception e) {
                String msg = String.format("Failed to finalize file [id=%s]: %s", fileReq.getFileId(), e.getMessage());
                log.error(msg, e);
                notificationService.sendMessage(Topic.LOG, msg);
            }
        }

        if (monitoringWasActive) {
            Thread.startVirtualThread(() -> {
                try {
                    Thread.sleep(5000);
                    monitoringService.resumeMonitoring();
                    bookdropMonitoringService.resumeMonitoring();
                    log.info("Monitoring resumed after 5s delay");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Interrupted while delaying resume of monitoring");
                }
            });
        }

        return results;
    }

    private BookdropFileResult moveFile(long libraryId, long pathId, String filePattern, BookMetadata metadata, BookdropFileEntity bookdropFile) throws Exception {
        LibraryEntity library = libraryRepository.findById(libraryId)
                .orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(libraryId));

        LibraryPathEntity path = library.getLibraryPaths().stream()
                .filter(p -> p.getId() == pathId)
                .findFirst()
                .orElseThrow(() -> ApiError.INVALID_LIBRARY_PATH.createException(libraryId));

        if (filePattern.endsWith("/") || filePattern.endsWith("\\")) {
            filePattern += "{currentFilename}";
        }

        String relativePath = PathPatternResolver.resolvePattern(metadata, filePattern, FilenameUtils.getName(bookdropFile.getFilePath()));
        Path source = Path.of(bookdropFile.getFilePath());
        Path target = Paths.get(path.getPath(), relativePath);
        File targetFile = target.toFile();

        if (!Files.exists(source)) {
            bookdropFileRepository.deleteById(bookdropFile.getId());
            log.warn("Source file [id={}] not found. Deleting entry.", bookdropFile.getId());
            bookdropNotificationService.sendBookdropFileSummaryNotification();
            return failureResult(targetFile.getName(), "Source file does not exist in bookdrop folder");
        }

        if (targetFile.exists()) {
            return failureResult(targetFile.getName(), "File already exists in the library '" + library.getName() + "'");
        }

        Files.createDirectories(target.getParent());
        Files.move(source, target);

        Book processedBook = processFile(targetFile.getName(), library, path, targetFile,
                BookFileExtension.fromFileName(bookdropFile.getFileName())
                        .orElseThrow(() -> ApiError.INVALID_FILE_FORMAT.createException("Unsupported file extension"))
                        .getType()
        );

        BookEntity bookEntity = bookRepository.findById(processedBook.getId())
                .orElseThrow(() -> ApiError.FILE_NOT_FOUND.createException("Book ID missing after import"));

        notificationService.sendMessage(Topic.BOOK_ADD, processedBook);
        metadataRefreshService.updateBookMetadata(bookEntity, metadata, metadata.getThumbnailUrl() != null, false);
        bookdropFileRepository.deleteById(bookdropFile.getId());
        bookdropNotificationService.sendBookdropFileSummaryNotification();

        File cachedCover = Paths.get(appProperties.getPathConfig(), "bookdrop_temp", bookdropFile.getId() + ".jpg").toFile();
        if (cachedCover.exists()) {
            boolean deleted = cachedCover.delete();
            log.debug("Deleted cached cover image for bookdropId={}: {}", bookdropFile.getId(), deleted);
        }

        return BookdropFileResult.builder()
                .fileName(targetFile.getName())
                .message("File successfully imported into the '" + library.getName() + "' library from the Bookdrop folder")
                .success(true)
                .build();
    }

    private BookdropFileResult failureResult(String fileName, String message) {
        return BookdropFileResult.builder()
                .fileName(fileName)
                .message(message)
                .success(false)
                .build();
    }

    private Book processFile(String fileName, LibraryEntity library, LibraryPathEntity path, File file, BookFileType type) {
        LibraryFile libraryFile = LibraryFile.builder()
                .libraryEntity(library)
                .libraryPathEntity(path)
                .fileSubPath(FileUtils.getRelativeSubPath(path.getPath(), file.toPath()))
                .bookFileType(type)
                .fileName(fileName)
                .build();

        return switch (type) {
            case PDF -> pdfProcessor.processFile(libraryFile, false);
            case EPUB -> epubProcessor.processFile(libraryFile, false);
            case CBX -> cbxProcessor.processFile(libraryFile, false);
        };
    }

    public void discardAllFiles() {
        bookdropMonitoringService.pauseMonitoring();
        Path bookdropPath = Path.of(appProperties.getBookdropFolder());

        AtomicInteger deletedFiles = new AtomicInteger();
        AtomicInteger deletedDirs = new AtomicInteger();
        AtomicInteger deletedCovers = new AtomicInteger();

        try {
            if (!Files.exists(bookdropPath)) {
                log.info("Bookdrop folder does not exist: {}", bookdropPath);
                return;
            }

            try (Stream<Path> paths = Files.walk(bookdropPath)) {
                paths.sorted(Comparator.reverseOrder())
                        .filter(p -> !p.equals(bookdropPath))
                        .forEach(path -> {
                            try {
                                if (Files.isRegularFile(path) && Files.deleteIfExists(path)) {
                                    deletedFiles.incrementAndGet();
                                } else if (Files.isDirectory(path) && Files.deleteIfExists(path)) {
                                    deletedDirs.incrementAndGet();
                                }
                            } catch (IOException e) {
                                log.warn("Failed to delete path: {}", path, e);
                            }
                        });
            }

            long removedDbCount = bookdropFileRepository.count();
            bookdropFileRepository.deleteAll();

            Path tempCoverDir = Paths.get(appProperties.getPathConfig(), "bookdrop_temp");
            if (Files.exists(tempCoverDir)) {
                try (Stream<Path> files = Files.walk(tempCoverDir)) {
                    files
                            .filter(Files::isRegularFile)
                            .filter(p -> p.toString().endsWith(".jpg"))
                            .forEach(p -> {
                                try {
                                    Files.delete(p);
                                    deletedCovers.incrementAndGet();
                                } catch (IOException e) {
                                    log.warn("Failed to delete cached cover: {}", p, e);
                                }
                            });
                } catch (IOException e) {
                    log.warn("Failed to clean bookdrop_temp folder", e);
                }
            }

            bookdropNotificationService.sendBookdropFileSummaryNotification();

            log.info("Discarded all files: deleted {} files, {} folders, {} DB entries, and {} cover images", deletedFiles.get(), deletedDirs.get(), removedDbCount, deletedCovers.get());

        } catch (IOException e) {
            throw new RuntimeException("Failed to clean bookdrop folder", e);
        } finally {
            bookdropMonitoringService.resumeMonitoring();
        }
    }

    public Resource getBookdropCover(long bookdropId) {
        String coverPath = Paths.get(appProperties.getPathConfig(), "bookdrop_temp", bookdropId + ".jpg").toString();
        File coverFile = new File(coverPath);
        if (coverFile.exists() && coverFile.isFile()) {
            return new PathResource(coverFile.toPath());
        } else {
            return null;
        }
    }
}