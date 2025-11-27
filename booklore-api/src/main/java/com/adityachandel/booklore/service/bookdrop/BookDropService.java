package com.adityachandel.booklore.service.bookdrop;

import com.adityachandel.booklore.config.AppProperties;
import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.mapper.BookdropFileMapper;
import com.adityachandel.booklore.model.FileProcessResult;
import com.adityachandel.booklore.model.MetadataUpdateContext;
import com.adityachandel.booklore.model.MetadataUpdateWrapper;
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
import com.adityachandel.booklore.model.enums.MetadataReplaceMode;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.BookdropFileRepository;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.file.FileMovingHelper;
import com.adityachandel.booklore.service.fileprocessor.BookFileProcessor;
import com.adityachandel.booklore.service.fileprocessor.BookFileProcessorRegistry;
import com.adityachandel.booklore.service.metadata.MetadataRefreshService;
import com.adityachandel.booklore.service.monitoring.MonitoringRegistrationService;
import com.adityachandel.booklore.util.FileUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private final BookdropMonitoringService bookdropMonitoringService;
    private final NotificationService notificationService;
    private final MetadataRefreshService metadataRefreshService;
    private final BookdropNotificationService bookdropNotificationService;
    private final BookFileProcessorRegistry processorRegistry;
    private final AppProperties appProperties;
    private final BookdropFileMapper mapper;
    private final ObjectMapper objectMapper;
    private final FileMovingHelper fileMovingHelper;
    private final MonitoringRegistrationService monitoringRegistrationService;

    private static final int CHUNK_SIZE = 100;

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

    public Resource getBookdropCover(long bookdropId) {
        String coverPath = Paths.get(appProperties.getPathConfig(), "bookdrop_temp", bookdropId + ".jpg").toString();
        File coverFile = new File(coverPath);
        if (coverFile.exists() && coverFile.isFile()) {
            return new PathResource(coverFile.toPath());
        } else {
            return null;
        }
    }

    public BookdropFinalizeResult finalizeImport(BookdropFinalizeRequest request) {
        try {
            bookdropMonitoringService.pauseMonitoring();
            return processFinalizationRequest(request);
        } finally {
            bookdropMonitoringService.resumeMonitoring();
            log.info("Bookdrop monitoring resumed");
        }
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

            List<BookdropFileEntity> filesToDelete = getFilesToDelete(selectAll, excludedIds, selectedIds);
            deleteFilesAndCovers(filesToDelete, deletedFiles, deletedCovers);
            deleteEmptyDirectories(bookdropPath, deletedDirs);

            bookdropFileRepository.deleteAllById(filesToDelete.stream().map(BookdropFileEntity::getId).toList());
            log.info("Deleted {} bookdrop DB entries", filesToDelete.size());

            bookdropNotificationService.sendBookdropFileSummaryNotification();
            log.info("Bookdrop cleanup summary: deleted {} files, {} folders, {} DB entries, {} covers",
                    deletedFiles.get(), deletedDirs.get(), filesToDelete.size(), deletedCovers.get());

        } finally {
            bookdropMonitoringService.resumeMonitoring();
            log.info("Bookdrop monitoring resumed after cleanup (library monitoring unaffected)");
        }
    }

    private BookdropFinalizeResult processFinalizationRequest(BookdropFinalizeRequest request) {
        BookdropFinalizeResult results = BookdropFinalizeResult.builder()
                .processedAt(Instant.now())
                .totalFiles(0)
                .successfullyImported(0)
                .failed(0)
                .build();

        Long defaultLibraryId = request.getDefaultLibraryId();
        Long defaultPathId = request.getDefaultPathId();
        Map<Long, BookdropFinalizeRequest.BookdropFinalizeFile> metadataById = getMetadataMap(request);

        AtomicInteger failedCount = new AtomicInteger();
        AtomicInteger totalFilesProcessed = new AtomicInteger();

        log.info("Starting finalizeImport: selectAll={}, provided file count={}, defaultLibraryId={}, defaultPathId={}", request.getSelectAll(), metadataById.size(), defaultLibraryId, defaultPathId);

        if (Boolean.TRUE.equals(request.getSelectAll())) {
            processAllFiles(request, metadataById, defaultLibraryId, defaultPathId, results, failedCount, totalFilesProcessed);
        } else {
            processSelectedFiles(request, metadataById, defaultLibraryId, defaultPathId, results, failedCount, totalFilesProcessed);
        }

        updateFinalResults(results, totalFilesProcessed, failedCount);
        return results;
    }

    private Map<Long, BookdropFinalizeRequest.BookdropFinalizeFile> getMetadataMap(BookdropFinalizeRequest request) {
        return Optional.ofNullable(request.getFiles())
                .orElse(List.of())
                .stream()
                .collect(Collectors.toMap(BookdropFinalizeRequest.BookdropFinalizeFile::getFileId, Function.identity()));
    }

    private void processAllFiles(BookdropFinalizeRequest request,
                                 Map<Long, BookdropFinalizeRequest.BookdropFinalizeFile> metadataById,
                                 Long defaultLibraryId,
                                 Long defaultPathId,
                                 BookdropFinalizeResult results,
                                 AtomicInteger failedCount,
                                 AtomicInteger totalFilesProcessed) {
        List<Long> excludedIds = Optional.ofNullable(request.getExcludedIds()).orElse(List.of());
        List<Long> allIds = bookdropFileRepository.findAllExcludingIdsFlat(excludedIds);
        log.info("SelectAll: Total files to finalize (after exclusions): {}, Excluded IDs: {}", allIds.size(), excludedIds);

        processFileChunks(allIds, metadataById, defaultLibraryId, defaultPathId, results, failedCount, totalFilesProcessed);
    }

    private void processSelectedFiles(BookdropFinalizeRequest request,
                                      Map<Long, BookdropFinalizeRequest.BookdropFinalizeFile> metadataById,
                                      Long defaultLibraryId,
                                      Long defaultPathId,
                                      BookdropFinalizeResult results,
                                      AtomicInteger failedCount,
                                      AtomicInteger totalFilesProcessed) {
        List<Long> ids = Optional.ofNullable(request.getFiles())
                .orElse(List.of())
                .stream()
                .map(BookdropFinalizeRequest.BookdropFinalizeFile::getFileId)
                .toList();

        log.info("Processing {} manually selected files in chunks of {}. File IDs: {}", ids.size(), CHUNK_SIZE, ids);
        processFileChunks(ids, metadataById, defaultLibraryId, defaultPathId, results, failedCount, totalFilesProcessed);
    }

    private void processFileChunks(List<Long> ids,
                                   Map<Long, BookdropFinalizeRequest.BookdropFinalizeFile> metadataById,
                                   Long defaultLibraryId,
                                   Long defaultPathId,
                                   BookdropFinalizeResult results,
                                   AtomicInteger failedCount,
                                   AtomicInteger totalFilesProcessed) {

        // Collect all libraries that will be affected by moves
        Set<Long> affectedLibraries = collectAffectedLibraries(ids, metadataById, defaultLibraryId, defaultPathId);

        // Unregister monitoring for all affected libraries
        unregisterAffectedLibraries(affectedLibraries);

        try {
            for (int i = 0; i < ids.size(); i += CHUNK_SIZE) {
                int end = Math.min(i + CHUNK_SIZE, ids.size());
                List<Long> chunk = ids.subList(i, end);

                log.info("Processing chunk {}/{} ({} files): IDs={}", (i / CHUNK_SIZE + 1), (int) Math.ceil((double) ids.size() / CHUNK_SIZE), chunk.size(), chunk);

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
        } finally {
            // Re-register monitoring for all affected libraries
            reregisterAffectedLibraries(affectedLibraries);
        }
    }

    private Set<Long> collectAffectedLibraries(List<Long> ids,
                                               Map<Long, BookdropFinalizeRequest.BookdropFinalizeFile> metadataById,
                                               Long defaultLibraryId,
                                               Long defaultPathId) {
        Set<Long> affectedLibraries = new HashSet<>();

        List<BookdropFileEntity> files = bookdropFileRepository.findAllById(ids);

        for (BookdropFileEntity fileEntity : files) {
            try {
                FileProcessingContext context = prepareFileProcessingContext(fileEntity, metadataById.get(fileEntity.getId()), defaultLibraryId, defaultPathId);
                affectedLibraries.add(context.libraryId());
            } catch (Exception e) {
                log.warn("Failed to determine library for file {}: {}", fileEntity.getId(), e.getMessage());
            }
        }

        log.info("Collected {} unique libraries for monitoring unregistration: {}", affectedLibraries.size(), affectedLibraries);
        return affectedLibraries;
    }

    private void unregisterAffectedLibraries(Set<Long> libraryIds) {
        for (Long libraryId : libraryIds) {
            monitoringRegistrationService.unregisterLibrary(libraryId);
            log.debug("Unregistered library {} from monitoring", libraryId);
        }
        log.info("Unregistered {} libraries from monitoring", libraryIds.size());
    }

    private void reregisterAffectedLibraries(Set<Long> libraryIds) {
        for (Long libraryId : libraryIds) {
            try {
                LibraryEntity library = libraryRepository.findById(libraryId).orElse(null);
                if (library != null) {
                    for (LibraryPathEntity libPath : library.getLibraryPaths()) {
                        Path libraryRoot = Path.of(libPath.getPath());
                        monitoringRegistrationService.registerLibraryPaths(libraryId, libraryRoot);
                        log.debug("Re-registered library {} path {} for monitoring", libraryId, libraryRoot);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to re-register library {} for monitoring: {}", libraryId, e.getMessage());
            }
        }
        log.info("Re-registered {} libraries for monitoring", libraryIds.size());
    }

    private void updateFinalResults(BookdropFinalizeResult results, AtomicInteger totalFilesProcessed, AtomicInteger failedCount) {
        results.setTotalFiles(totalFilesProcessed.get());
        results.setFailed(failedCount.get());
        results.setSuccessfullyImported(totalFilesProcessed.get() - failedCount.get());

        log.info("Finalization complete. Success: {}, Failed: {}, Total processed: {}",
                results.getSuccessfullyImported(),
                results.getFailed(),
                results.getTotalFiles());
    }

    private void processFile(BookdropFileEntity fileEntity,
                             BookdropFinalizeRequest.BookdropFinalizeFile fileReq,
                             Long defaultLibraryId,
                             Long defaultPathId,
                             BookdropFinalizeResult results,
                             AtomicInteger failedCount) {
        try {
            FileProcessingContext context = prepareFileProcessingContext(fileEntity, fileReq, defaultLibraryId, defaultPathId);
            BookdropFileResult result = moveFile(context.libraryId, context.pathId, context.metadata, fileEntity);

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

    private FileProcessingContext prepareFileProcessingContext(BookdropFileEntity fileEntity,
                                                               BookdropFinalizeRequest.BookdropFinalizeFile fileReq,
                                                               Long defaultLibraryId,
                                                               Long defaultPathId) throws Exception {
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

        return new FileProcessingContext(libraryId, pathId, metadata);
    }

    private BookdropFileResult moveFile(long libraryId, long pathId, BookMetadata metadata, BookdropFileEntity bookdropFile) {
        LibraryEntity library = libraryRepository.findById(libraryId)
                .orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(libraryId));

        LibraryPathEntity path = library.getLibraryPaths().stream()
                .filter(p -> p.getId().equals(pathId))
                .findFirst()
                .orElseThrow(() -> ApiError.INVALID_LIBRARY_PATH.createException(libraryId));

        String filePattern = fileMovingHelper.getFileNamingPattern(library);
        Path source = Path.of(bookdropFile.getFilePath());
        Path target = fileMovingHelper.generateNewFilePath(path.getPath(), metadata, filePattern, bookdropFile.getFilePath());
        File targetFile = target.toFile();

        log.debug("Preparing to move file id={}, name={}, source={}, target={}, library={}, path={}", bookdropFile.getId(), bookdropFile.getFileName(), source, target, library.getName(), path.getPath());

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

        return performFileMove(bookdropFile, source, target, library, path, metadata);
    }

    private BookdropFileResult performFileMove(BookdropFileEntity bookdropFile, Path source, Path target, LibraryEntity library, LibraryPathEntity path, BookMetadata metadata) {
        Path tempPath = null;
        try {
            tempPath = Files.createTempFile("bookdrop-finalize-", bookdropFile.getFileName());
            Files.copy(source, tempPath, StandardCopyOption.REPLACE_EXISTING);

            Files.createDirectories(target.getParent());
            Files.move(tempPath, target, StandardCopyOption.REPLACE_EXISTING);

            log.info("Moved file id={}, name={} from '{}' to '{}'", bookdropFile.getId(), bookdropFile.getFileName(), source, target);

            BookdropFileResult result;
            try {
                result = processMovedFile(bookdropFile, target.toFile(), library, path, metadata);
            } catch (Exception e) {
                cleanupTargetFile(target, bookdropFile.getId(), "processing exception");
                return failureResult(bookdropFile.getFileName(), "Processing failed: " + e.getMessage());
            }

            if (result.isSuccess()) {
                try {
                    Files.delete(source);
                    log.info("Successfully deleted source file '{}' after successful import for file id={}", source, bookdropFile.getId());
                } catch (IOException e) {
                    log.warn("Failed to delete source file '{}' after successful import for file id={}: {}", source, bookdropFile.getId(), e.getMessage());
                }
            } else {
                cleanupTargetFile(target, bookdropFile.getId(), "logical failure");
            }

            return result;

        } catch (Exception e) {
            log.error("Failed to move file id={}, name={} from '{}' to '{}': {}", bookdropFile.getId(), bookdropFile.getFileName(), source, target, e.getMessage(), e);
            cleanupFailedMove(target);
            return failureResult(bookdropFile.getFileName(), "Failed to move file: " + e.getMessage());
        } finally {
            cleanupTempFile(tempPath);
        }
    }

    private BookdropFileResult processMovedFile(BookdropFileEntity bookdropFile,
                                                File targetFile,
                                                LibraryEntity library,
                                                LibraryPathEntity path,
                                                BookMetadata metadata) throws Exception {
        FileProcessResult fileProcessResult = processFileInLibrary(targetFile.getName(), library, path, targetFile,
                BookFileExtension.fromFileName(bookdropFile.getFileName())
                        .orElseThrow(() -> ApiError.INVALID_FILE_FORMAT.createException("Unsupported file extension"))
                        .getType());

        BookEntity bookEntity = bookRepository.findById(fileProcessResult.getBook().getId())
                .orElseThrow(() -> ApiError.FILE_NOT_FOUND.createException("Book ID missing after import"));

        notificationService.sendMessage(Topic.BOOK_ADD, fileProcessResult.getBook());
        MetadataUpdateContext context = MetadataUpdateContext.builder()
                .bookEntity(bookEntity)
                .metadataUpdateWrapper(MetadataUpdateWrapper.builder()
                        .metadata(metadata)
                        .build())
                .updateThumbnail(metadata.getThumbnailUrl() != null)
                .mergeCategories(false)
                .replaceMode(MetadataReplaceMode.REPLACE_ALL)
                .mergeMoods(true)
                .mergeTags(true)
                .build();

        metadataRefreshService.updateBookMetadata(context);

        cleanupBookdropData(bookdropFile);

        log.info("File import completed: id={}, name={}, library={}, path={}", bookdropFile.getId(), targetFile.getName(), library.getName(), path.getPath());

        return BookdropFileResult.builder()
                .fileName(targetFile.getName())
                .message("File successfully imported into the '" + library.getName() + "' library from the Bookdrop folder")
                .success(true)
                .build();
    }

    private void cleanupBookdropData(BookdropFileEntity bookdropFile) {
        bookdropFileRepository.deleteById(bookdropFile.getId());
        bookdropNotificationService.sendBookdropFileSummaryNotification();

        Path cachedCoverPath = Paths.get(appProperties.getPathConfig(), "bookdrop_temp", bookdropFile.getId() + ".jpg");
        if (Files.exists(cachedCoverPath)) {
            try {
                Files.delete(cachedCoverPath);
                log.debug("Deleted cached cover image for bookdropId={}", bookdropFile.getId());
            } catch (IOException e) {
                log.warn("Failed to delete cached cover image for bookdropId={}: {}", bookdropFile.getId(), e.getMessage());
            }
        }
    }

    private void cleanupFailedMove(Path target) {
        try {
            if (Files.deleteIfExists(target)) {
                log.info("Cleaned up partially created target file: {}", target);
            } else {
                log.debug("No partially created target file to cleanup: {}", target);
            }
        } catch (IOException e) {
            log.warn("Failed to cleanup partially created target file: {}: {}", target, e.getMessage());
        }
    }

    private void cleanupTempFile(Path tempPath) {
        if (tempPath != null) {
            try {
                Files.delete(tempPath);
            } catch (Exception e) {
                log.warn("Failed to cleanup temp file: {}", tempPath, e);
            }
        }
    }

    private void cleanupTargetFile(Path target, Long fileId, String reason) {
        try {
            if (Files.deleteIfExists(target)) {
                log.info("Cleaned up target file '{}' after {} for file id={}", target, reason, fileId);
            } else {
                log.debug("Target file '{}' not present, nothing to cleanup for file id={}", target, fileId);
            }
        } catch (IOException e) {
            log.warn("Failed to cleanup target file '{}' after {} for file id={}: {}", 
                    target, reason, fileId, e.getMessage());
        }
    }

    private FileProcessResult processFileInLibrary(String fileName, LibraryEntity library, LibraryPathEntity path, File file, BookFileType type) {
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

    private List<BookdropFileEntity> getFilesToDelete(boolean selectAll, List<Long> excludedIds, List<Long> selectedIds) {
        if (selectAll) {
            List<BookdropFileEntity> filesToDelete = bookdropFileRepository.findAll().stream()
                    .filter(f -> excludedIds == null || !excludedIds.contains(f.getId()))
                    .toList();
            log.info("Discarding all files except excluded IDs: {}", excludedIds);
            return filesToDelete;
        } else {
            List<BookdropFileEntity> filesToDelete = bookdropFileRepository.findAllById(selectedIds == null ? List.of() : selectedIds);
            log.info("Discarding selected files: {}", selectedIds);
            return filesToDelete;
        }
    }

    private void deleteFilesAndCovers(List<BookdropFileEntity> filesToDelete, AtomicInteger deletedFiles, AtomicInteger deletedCovers) {
        for (BookdropFileEntity entity : filesToDelete) {
            Path filePath = Path.of(entity.getFilePath());
            if (Files.exists(filePath) && Files.isRegularFile(filePath)) {
                try {
                    Files.delete(filePath);
                    deletedFiles.incrementAndGet();
                    log.debug("Deleted file from disk: id={}, path={}", entity.getId(), filePath);
                } catch (IOException e) {
                    log.warn("Failed to delete file from disk for bookdropId={}: {}", entity.getId(), e.getMessage());
                }
            }
            Path coverPath = Paths.get(appProperties.getPathConfig(), "bookdrop_temp", entity.getId() + ".jpg");
            if (Files.exists(coverPath)) {
                try {
                    Files.delete(coverPath);
                    deletedCovers.incrementAndGet();
                    log.debug("Deleted cover image: id={}, path={}", entity.getId(), coverPath);
                } catch (IOException e) {
                    log.warn("Failed to delete cover image for bookdropId={}: {}", entity.getId(), e.getMessage());
                }
            }
        }
    }

    private void deleteEmptyDirectories(Path bookdropPath, AtomicInteger deletedDirs) {
        try (Stream<Path> paths = Files.walk(bookdropPath)) {
            paths.sorted(Comparator.reverseOrder())
                    .filter(p -> !p.equals(bookdropPath) && Files.isDirectory(p))
                    .forEach(p -> {
                        try (Stream<Path> subPaths = Files.list(p)) {
                            if (subPaths.findAny().isEmpty()) {
                                try {
                                    Files.delete(p);
                                    deletedDirs.incrementAndGet();
                                    log.debug("Deleted empty directory: {}", p);
                                } catch (IOException e) {
                                    log.warn("Failed to delete empty directory: {}: {}", p, e.getMessage());
                                }
                            }
                        } catch (IOException e) {
                            log.warn("Failed to delete folder: {}", p, e);
                        }
                    });
        } catch (IOException e) {
            log.warn("Failed to scan bookdrop folder for empty directories", e);
        }
    }

    private BookdropFileResult failureResult(String fileName, String message) {
        return BookdropFileResult.builder()
                .fileName(fileName)
                .message(message)
                .success(false)
                .build();
    }

    private record FileProcessingContext(Long libraryId, Long pathId, BookMetadata metadata) {
    }
}

