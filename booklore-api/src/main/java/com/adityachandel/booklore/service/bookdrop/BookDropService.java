package com.adityachandel.booklore.service.bookdrop;

import com.adityachandel.booklore.config.AppProperties;
import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.mapper.BookdropFileMapper;
import com.adityachandel.booklore.model.FileProcessResult;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.BookdropFile;
import com.adityachandel.booklore.model.dto.BookdropFileNotification;
import com.adityachandel.booklore.model.dto.request.BookdropFinalizeRequest;
import com.adityachandel.booklore.model.dto.response.BookdropFileResult;
import com.adityachandel.booklore.model.dto.response.BookdropFinalizeResult;
import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookdropFileEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.enums.BookFileExtension;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.BookdropFileRepository;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.service.fileprocessor.BookFileProcessor;
import com.adityachandel.booklore.service.fileprocessor.BookFileProcessorRegistry;
import com.adityachandel.booklore.service.metadata.MetadataRefreshService;
import com.adityachandel.booklore.service.monitoring.MonitoringProtectionService;
import com.adityachandel.booklore.util.FileUtils;
import com.adityachandel.booklore.util.PathPatternResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@AllArgsConstructor
@Service
public class BookDropService {

    private final BookdropFileRepository bookdropFileRepository;
    private final LibraryRepository libraryRepository;
    private final BookRepository bookRepository;
    private final MonitoringProtectionService monitoringProtectionService;
    private final BookdropMonitoringService bookdropMonitoringService;
    private final NotificationService notificationService;
    private final MetadataRefreshService metadataRefreshService;
    private final BookdropNotificationService bookdropNotificationService;
    private final BookFileProcessorRegistry processorRegistry;
    private final AppProperties appProperties;
    private final BookdropFileMapper mapper;
    private final ObjectMapper objectMapper;
    AppSettingService appSettingService;

    public BookdropFileNotification getFileNotificationSummary() {
        long pendingCount = bookdropFileRepository.countByStatus(BookdropFileEntity.Status.PENDING_REVIEW);
        long totalCount = bookdropFileRepository.count();
        return new BookdropFileNotification((int) pendingCount, (int) totalCount, Instant.now().toString());
    }

    public Page<BookdropFile> getFilesByStatus(String status, Pageable pageable) {
        if ("pending".equalsIgnoreCase(status)) {
            return bookdropFileRepository.findAllByStatus(BookdropFileEntity.Status.PENDING_REVIEW, pageable).map(mapper::toDto);
        } else {
            return bookdropFileRepository.findAll(pageable).map(mapper::toDto);
        }
    }

    public BookdropFinalizeResult finalizeImport(BookdropFinalizeRequest request) {
        return monitoringProtectionService.executeWithProtection(() -> {
            try {
                bookdropMonitoringService.pauseMonitoring();

                BookdropFinalizeResult results = BookdropFinalizeResult.builder()
                        .processedAt(Instant.now())
                        .build();
                Long defaultLibraryId = request.getDefaultLibraryId();
                Long defaultPathId = request.getDefaultPathId();

                Map<Long, BookdropFinalizeRequest.BookdropFinalizeFile> metadataById = Optional.ofNullable(request.getFiles())
                        .orElse(List.of())
                        .stream()
                        .collect(Collectors.toMap(BookdropFinalizeRequest.BookdropFinalizeFile::getFileId, Function.identity()));

                final int CHUNK_SIZE = 100;
                AtomicInteger failedCount = new AtomicInteger();
                AtomicInteger totalFilesProcessed = new AtomicInteger();

                log.info("Starting finalizeImport: selectAll={}, provided file count={}, defaultLibraryId={}, defaultPathId={}",
                        request.getSelectAll(), metadataById.size(), defaultLibraryId, defaultPathId);

                if (Boolean.TRUE.equals(request.getSelectAll())) {
                    List<Long> excludedIds = Optional.ofNullable(request.getExcludedIds()).orElse(List.of());

                    List<Long> allIds = bookdropFileRepository.findAllExcludingIdsFlat(excludedIds);
                    log.info("SelectAll: Total files to finalize (after exclusions): {}, Excluded IDs: {}", allIds.size(), excludedIds);

                    for (int i = 0; i < allIds.size(); i += CHUNK_SIZE) {
                        int end = Math.min(i + CHUNK_SIZE, allIds.size());
                        List<Long> chunk = allIds.subList(i, end);

                        log.info("Processing chunk {}/{} ({} files): IDs={}", (i / CHUNK_SIZE + 1), (int) Math.ceil((double) allIds.size() / CHUNK_SIZE), chunk.size(), chunk);

                        List<BookdropFileEntity> chunkFiles = bookdropFileRepository.findAllById(chunk);
                        Map<Long, BookdropFileEntity> fileMap = chunkFiles.stream().collect(Collectors.toMap(BookdropFileEntity::getId, Function.identity()));

                        for (Long id : chunk) {
                            BookdropFileEntity file = fileMap.get(id);
                            if (file == null) {
                                log.warn("File ID {} missing in DB during finalizeImport chunk processing", id);
                                failedCount.incrementAndGet();
                                totalFilesProcessed.incrementAndGet();
                                continue;
                            }
                            processFile(file, metadataById.get(id), defaultLibraryId, defaultPathId, results, failedCount);
                            totalFilesProcessed.incrementAndGet();
                        }
                    }
                } else {
                    List<Long> ids = Optional.ofNullable(request.getFiles())
                            .orElse(List.of())
                            .stream()
                            .map(BookdropFinalizeRequest.BookdropFinalizeFile::getFileId)
                            .toList();

                    log.info("Processing {} manually selected files in chunks of {}. File IDs: {}", ids.size(), CHUNK_SIZE, ids);

                    for (int i = 0; i < ids.size(); i += CHUNK_SIZE) {
                        int end = Math.min(i + CHUNK_SIZE, ids.size());
                        List<Long> chunkIds = ids.subList(i, end);
                        List<BookdropFileEntity> chunkFiles = bookdropFileRepository.findAllById(chunkIds);

                        log.info("Processing chunk {} of {} ({} files): IDs={}", (i / CHUNK_SIZE + 1), (int) Math.ceil((double) ids.size() / CHUNK_SIZE), chunkFiles.size(), chunkIds);

                        Map<Long, BookdropFileEntity> fileMap = chunkFiles.stream()
                                .collect(Collectors.toMap(BookdropFileEntity::getId, Function.identity()));

                        for (Long id : chunkIds) {
                            BookdropFileEntity file = fileMap.get(id);
                            if (file == null) {
                                log.error("File ID {} not found in DB during finalizeImport chunk processing", id);
                                failedCount.incrementAndGet();
                                totalFilesProcessed.incrementAndGet();
                                continue;
                            }
                            processFile(file, metadataById.get(id), defaultLibraryId, defaultPathId, results, failedCount);
                            totalFilesProcessed.incrementAndGet();
                        }
                    }
                }

                results.setTotalFiles(totalFilesProcessed.get());
                results.setFailed(failedCount.get());
                results.setSuccessfullyImported(totalFilesProcessed.get() - failedCount.get());

                log.info("Finalization complete. Success: {}, Failed: {}, Total processed: {}",
                        results.getSuccessfullyImported(),
                        results.getFailed(),
                        results.getTotalFiles());

                return results;

            } finally {
                bookdropMonitoringService.resumeMonitoring();
            }
        }, "bookdrop finalize import");
    }

    private void processFile(
            BookdropFileEntity fileEntity,
            BookdropFinalizeRequest.BookdropFinalizeFile fileReq,
            Long defaultLibraryId,
            Long defaultPathId,
            BookdropFinalizeResult results,
            AtomicInteger failedCount
    ) {
        try {
            Long libraryId;
            Long pathId;
            BookMetadata metadata;

            if (fileReq != null) {
                libraryId = fileReq.getLibraryId() != null ? fileReq.getLibraryId() : defaultLibraryId;
                pathId = fileReq.getPathId() != null ? fileReq.getPathId() : defaultPathId;
                metadata = fileReq.getMetadata();
                log.debug("Processing fileId={}, fileName={} with provided metadata, libraryId={}, pathId={}", fileEntity.getId(), fileEntity.getFileName(), libraryId, pathId);
            } else {
                if (defaultLibraryId == null || defaultPathId == null) {
                    log.warn("Missing default metadata for fileId={}", fileEntity.getId());
                    throw ApiError.GENERIC_BAD_REQUEST.createException("Missing metadata and defaults for fileId=" + fileEntity.getId());
                }

                metadata = fileEntity.getFetchedMetadata() != null
                        ? objectMapper.readValue(fileEntity.getFetchedMetadata(), BookMetadata.class)
                        : objectMapper.readValue(fileEntity.getOriginalMetadata(), BookMetadata.class);

                libraryId = defaultLibraryId;
                pathId = defaultPathId;
                log.debug("Processing fileId={}, fileName={} with default metadata, libraryId={}, pathId={}", fileEntity.getId(), fileEntity.getFileName(), libraryId, pathId);
            }

            BookdropFileResult result = moveFile(libraryId, pathId, metadata, fileEntity);

            results.getResults().add(result);
            if (!result.isSuccess()) {
                log.warn("Finalization failed (non-exception) for file id={}, name={}, message={}", fileEntity.getId(), fileEntity.getFileName(), result.getMessage());
                failedCount.incrementAndGet();
            } else {
                log.info("Successfully finalized file id={}, name={}", fileEntity.getId(), fileEntity.getFileName());
            }

        } catch (Exception e) {
            failedCount.incrementAndGet();
            String msg = String.format("Error finalizing file [id=%s, name=%s]: %s", fileEntity.getId(), fileEntity.getFileName(), e.getMessage());
            log.error(msg, e);
            notificationService.sendMessage(Topic.LOG, msg);
        }
    }

    private BookdropFileResult moveFile(long libraryId, long pathId, BookMetadata metadata, BookdropFileEntity bookdropFile) throws Exception {
        LibraryEntity library = libraryRepository.findById(libraryId)
                .orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(libraryId));

        LibraryPathEntity path = library.getLibraryPaths().stream()
                .filter(p -> p.getId() == pathId)
                .findFirst()
                .orElseThrow(() -> ApiError.INVALID_LIBRARY_PATH.createException(libraryId));

        String filePattern = library.getFileNamingPattern();
        if (filePattern == null || filePattern.isBlank()) {
            filePattern = appSettingService.getAppSettings().getUploadPattern();
        }

        if (filePattern.endsWith("/") || filePattern.endsWith("\\")) {
            filePattern += "{currentFilename}";
        }

        String relativePath = PathPatternResolver.resolvePattern(metadata, filePattern, FilenameUtils.getName(bookdropFile.getFilePath()));
        Path source = Path.of(bookdropFile.getFilePath());
        Path target = Paths.get(path.getPath(), relativePath);
        File targetFile = target.toFile();

        log.debug("Preparing to move file id={}, name={}, source={}, target={}, library={}, path={}",
                bookdropFile.getId(), bookdropFile.getFileName(), source, target, library.getName(), path.getPath());

        if (!Files.exists(source)) {
            bookdropFileRepository.deleteById(bookdropFile.getId());
            log.warn("Source file [id={}] not found at '{}'. Deleting entry from DB.", bookdropFile.getId(), source);
            bookdropNotificationService.sendBookdropFileSummaryNotification();
            return failureResult(targetFile.getName(), "Source file does not exist in bookdrop folder");
        }

        if (targetFile.exists()) {
            log.warn("Target file already exists: id={}, name={}, target={}", bookdropFile.getId(), bookdropFile.getFileName(), target);
            return failureResult(targetFile.getName(), "File already exists in the library '" + library.getName() + "'");
        }

        return monitoringProtectionService.executeWithProtection(() -> {
            try {
                Files.createDirectories(target.getParent());
                Files.move(source, target);

                log.info("Moved file id={}, name={} from '{}' to '{}'", bookdropFile.getId(), bookdropFile.getFileName(), source, target);

                FileProcessResult fileProcessResult = processFile(targetFile.getName(), library, path, targetFile,
                        BookFileExtension.fromFileName(bookdropFile.getFileName())
                                .orElseThrow(() -> ApiError.INVALID_FILE_FORMAT.createException("Unsupported file extension"))
                                .getType());

                BookEntity bookEntity = bookRepository.findById(fileProcessResult.getBook().getId())
                        .orElseThrow(() -> ApiError.FILE_NOT_FOUND.createException("Book ID missing after import"));

                notificationService.sendMessage(Topic.BOOK_ADD, fileProcessResult.getStatus());
                metadataRefreshService.updateBookMetadata(bookEntity, metadata, metadata.getThumbnailUrl() != null, false);
                bookdropFileRepository.deleteById(bookdropFile.getId());
                bookdropNotificationService.sendBookdropFileSummaryNotification();

                File cachedCover = Paths.get(appProperties.getPathConfig(), "bookdrop_temp", bookdropFile.getId() + ".jpg").toFile();
                if (cachedCover.exists()) {
                    boolean deleted = cachedCover.delete();
                    log.debug("Deleted cached cover image for bookdropId={}: {}", bookdropFile.getId(), deleted);
                }

                log.info("File import completed: id={}, name={}, library={}, path={}", bookdropFile.getId(), targetFile.getName(), library.getName(), path.getPath());

                return BookdropFileResult.builder()
                        .fileName(targetFile.getName())
                        .message("File successfully imported into the '" + library.getName() + "' library from the Bookdrop folder")
                        .success(true)
                        .build();

            } catch (Exception e) {
                log.error("Failed to move file id={}, name={} from '{}' to '{}': {}", bookdropFile.getId(), bookdropFile.getFileName(), source, target, e.getMessage(), e);
                try {
                    if (Files.exists(target)) {
                        Files.deleteIfExists(target);
                        log.info("Cleaned up partially created target file: {}", target);
                    }
                } catch (Exception cleanupException) {
                    log.warn("Failed to cleanup target file after move error: {}", target, cleanupException);
                }
                return failureResult(bookdropFile.getFileName(), "Failed to move file: " + e.getMessage());
            }
        }, "bookdrop file move");
    }

    private BookdropFileResult failureResult(String fileName, String message) {
        return BookdropFileResult.builder()
                .fileName(fileName)
                .message(message)
                .success(false)
                .build();
    }

    private FileProcessResult processFile(String fileName, LibraryEntity library, LibraryPathEntity path, File file, BookFileType type) {
        LibraryFile libraryFile = LibraryFile.builder()
                .libraryEntity(library)
                .libraryPathEntity(path)
                .fileSubPath(FileUtils.getRelativeSubPath(path.getPath(), file.toPath()))
                .bookFileType(type)
                .fileName(fileName)
                .build();

        BookFileProcessor processor = processorRegistry.getProcessorOrThrow(type);
        return processor.processFile(libraryFile);
    }

    public void discardSelectedFiles(boolean selectAll, List<Long> excludedIds, List<Long> selectedIds) {
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

            List<BookdropFileEntity> filesToDelete;
            if (selectAll) {
                filesToDelete = bookdropFileRepository.findAll().stream()
                        .filter(f -> excludedIds == null || !excludedIds.contains(f.getId()))
                        .toList();
                log.info("Discarding all files except excluded IDs: {}", excludedIds);
            } else {
                filesToDelete = bookdropFileRepository.findAllById(selectedIds == null ? List.of() : selectedIds);
                log.info("Discarding selected files: {}", selectedIds);
            }

            for (BookdropFileEntity entity : filesToDelete) {
                try {
                    Path filePath = Path.of(entity.getFilePath());
                    if (Files.exists(filePath) && Files.isRegularFile(filePath) && Files.deleteIfExists(filePath)) {
                        deletedFiles.incrementAndGet();
                        log.debug("Deleted file from disk: id={}, path={}", entity.getId(), filePath);
                    }
                    Path coverPath = Paths.get(appProperties.getPathConfig(), "bookdrop_temp", entity.getId() + ".jpg");
                    if (Files.exists(coverPath) && Files.deleteIfExists(coverPath)) {
                        deletedCovers.incrementAndGet();
                        log.debug("Deleted cover image: id={}, path={}", entity.getId(), coverPath);
                    }
                } catch (IOException e) {
                    log.warn("Failed to delete file or cover for bookdropId={}: {}", entity.getId(), e.getMessage());
                }
            }

            bookdropFileRepository.deleteAllById(filesToDelete.stream().map(BookdropFileEntity::getId).toList());
            log.info("Deleted {} bookdrop DB entries", filesToDelete.size());

            try (Stream<Path> paths = Files.walk(bookdropPath)) {
                paths.sorted(Comparator.reverseOrder())
                        .filter(p -> !p.equals(bookdropPath) && Files.isDirectory(p))
                        .forEach(p -> {
                            try (Stream<Path> subPaths = Files.list(p)) {
                                if (subPaths.findAny().isEmpty()) {
                                    Files.deleteIfExists(p);
                                    deletedDirs.incrementAndGet();
                                    log.debug("Deleted empty directory: {}", p);
                                }
                            } catch (IOException e) {
                                log.warn("Failed to delete folder: {}", p, e);
                            }
                        });
            } catch (IOException e) {
                log.warn("Failed to scan bookdrop folder for empty directories", e);
            }

            bookdropNotificationService.sendBookdropFileSummaryNotification();
            log.info("Bookdrop cleanup summary: deleted {} files, {} folders, {} DB entries, {} covers",
                    deletedFiles.get(), deletedDirs.get(), filesToDelete.size(), deletedCovers.get());

        } finally {
            bookdropMonitoringService.resumeMonitoring();
            log.info("Bookdrop monitoring resumed after cleanup");
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
