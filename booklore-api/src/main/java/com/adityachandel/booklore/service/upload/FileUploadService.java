package com.adityachandel.booklore.service.upload;

import com.adityachandel.booklore.config.AppProperties;
import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.mapper.AdditionalFileMapper;
import com.adityachandel.booklore.model.FileProcessResult;
import com.adityachandel.booklore.model.dto.AdditionalFile;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.BookAdditionalFileEntity;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.enums.AdditionalFileType;
import com.adityachandel.booklore.model.enums.BookFileExtension;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.enums.FileProcessStatus;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.BookAdditionalFileRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.service.FileFingerprint;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.service.fileprocessor.BookFileProcessor;
import com.adityachandel.booklore.service.fileprocessor.BookFileProcessorRegistry;
import com.adityachandel.booklore.service.metadata.extractor.EpubMetadataExtractor;
import com.adityachandel.booklore.service.metadata.extractor.PdfMetadataExtractor;
import com.adityachandel.booklore.service.monitoring.MonitoringService;
import com.adityachandel.booklore.util.FileUtils;
import com.adityachandel.booklore.util.PathPatternResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@RequiredArgsConstructor
@Service
@Slf4j
public class FileUploadService {

    private final LibraryRepository libraryRepository;
    private final BookRepository bookRepository;
    private final BookAdditionalFileRepository additionalFileRepository;
    private final BookFileProcessorRegistry processorRegistry;
    private final NotificationService notificationService;
    private final AppSettingService appSettingService;
    private final AppProperties appProperties;
    private final PdfMetadataExtractor pdfMetadataExtractor;
    private final EpubMetadataExtractor epubMetadataExtractor;
    private final AdditionalFileMapper additionalFileMapper;
    private final MonitoringService monitoringService;

    @Value("${PUID:${USER_ID:0}}")
    private String userId;

    @Value("${PGID:${GROUP_ID:0}}")
    private String groupId;

    public Book uploadFile(MultipartFile file, long libraryId, long pathId) throws IOException {
        validateFile(file);

        LibraryEntity libraryEntity = libraryRepository.findById(libraryId).orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(libraryId));

        LibraryPathEntity libraryPathEntity = libraryEntity.getLibraryPaths()
                .stream()
                .filter(p -> p.getId() == pathId)
                .findFirst()
                .orElseThrow(() -> ApiError.INVALID_LIBRARY_PATH.createException(libraryId));

        Path tempPath = Files.createTempFile("upload-", Objects.requireNonNull(file.getOriginalFilename()));
        FileProcessResult result;

        boolean wePaused = false;
        if (!monitoringService.isPaused()) {
            monitoringService.pauseMonitoring();
            wePaused = true;
        }

        try {
            file.transferTo(tempPath);
            setTemporaryFileOwnership(tempPath);
            BookFileExtension fileExt = BookFileExtension.fromFileName(file.getOriginalFilename()).orElseThrow(() -> ApiError.INVALID_FILE_FORMAT.createException("Unsupported file extension"));
            BookMetadata metadata = extractMetadata(fileExt, tempPath.toFile());
            String uploadPattern = appSettingService.getAppSettings().getUploadPattern();
            if (uploadPattern.endsWith("/") || uploadPattern.endsWith("\\")) {
                uploadPattern += "{currentFilename}";
            }

            String relativePath = PathPatternResolver.resolvePattern(metadata, uploadPattern, file.getOriginalFilename());
            Path finalPath = Paths.get(libraryPathEntity.getPath(), relativePath);
            File finalFile = finalPath.toFile();

            if (finalFile.exists()) {
                throw ApiError.FILE_ALREADY_EXISTS.createException();
            }

            Files.createDirectories(finalPath.getParent());
            Files.move(tempPath, finalPath);

            log.info("File uploaded to final location: {}", finalPath);

            result = processFile(finalFile.getName(), libraryEntity, libraryPathEntity, finalFile, fileExt.getType());
            if (result != null && result.getStatus() != FileProcessStatus.DUPLICATE) {
                notificationService.sendMessage(Topic.BOOK_ADD, result.getBook());
            }

            return result.getBook();

        } catch (IOException e) {
            throw ApiError.FILE_READ_ERROR.createException(e.getMessage());
        } finally {
            Files.deleteIfExists(tempPath);

            if (wePaused) {
                Thread.startVirtualThread(() -> {
                    try {
                        Thread.sleep(5_000);
                        monitoringService.resumeMonitoring();
                        log.info("Monitoring resumed after 5s delay");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("Interrupted while delaying resume of monitoring");
                    }
                });
            }
        }
    }

    @Transactional
    public AdditionalFile uploadAdditionalFile(Long bookId, MultipartFile file, AdditionalFileType additionalFileType, String description) throws IOException {
        Optional<BookEntity> bookOpt = bookRepository.findById(bookId);
        if (bookOpt.isEmpty()) {
            throw new IllegalArgumentException("Book not found with id: " + bookId);
        }

        BookEntity book = bookOpt.get();
        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null) {
            throw new IllegalArgumentException("File must have a name");
        }

        Path tempPath = Files.createTempFile("upload-", Objects.requireNonNull(file.getOriginalFilename()));

        boolean wePaused = false;
        if (!monitoringService.isPaused()) {
            monitoringService.pauseMonitoring();
            wePaused = true;
        }

        try {
            file.transferTo(tempPath);

            setTemporaryFileOwnership(tempPath);

            // Check for duplicates by hash, but only for alternative formats
            String fileHash = FileFingerprint.generateHash(tempPath);
            if (additionalFileType == AdditionalFileType.ALTERNATIVE_FORMAT) {
                Optional<BookAdditionalFileEntity> existingAltFormat = additionalFileRepository.findByAltFormatCurrentHash(fileHash);
                if (existingAltFormat.isPresent()) {
                    throw new IllegalArgumentException("Alternative format file already exists with same content");
                }
            }

            // Store file in same directory as the book
            Path finalPath = Paths.get(book.getLibraryPath().getPath(), book.getFileSubPath(), originalFileName);
            File finalFile = finalPath.toFile();

            if (finalFile.exists()) {
                throw ApiError.FILE_ALREADY_EXISTS.createException();
            }

            Files.createDirectories(finalPath.getParent());
            Files.move(tempPath, finalPath);

            log.info("Additional file uploaded to final location: {}", finalPath);

            // Create entity
            BookAdditionalFileEntity entity = BookAdditionalFileEntity.builder()
                    .book(book)
                    .fileName(originalFileName)
                    .fileSubPath(book.getFileSubPath())
                    .additionalFileType(additionalFileType)
                    .fileSizeKb(file.getSize() / 1024)
                    .initialHash(fileHash)
                    .currentHash(fileHash)
                    .description(description)
                    .addedOn(Instant.now())
                    .build();

            entity = additionalFileRepository.save(entity);

            return additionalFileMapper.toAdditionalFile(entity);
        } catch (IOException e) {
            throw ApiError.FILE_READ_ERROR.createException(e.getMessage());
        } finally {
            Files.deleteIfExists(tempPath);

            if (wePaused) {
                Thread.startVirtualThread(() -> {
                    try {
                        Thread.sleep(5_000);
                        monitoringService.resumeMonitoring();
                        log.info("Monitoring resumed after 5s delay");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("Interrupted while delaying resume of monitoring");
                    }
                });
            }
        }
    }

    public Book uploadFileBookDrop(MultipartFile file) throws IOException {
        validateFile(file);

        Path dropFolder = Paths.get(appProperties.getBookdropFolder());
        Files.createDirectories(dropFolder);

        String originalFilename = Objects.requireNonNull(file.getOriginalFilename());
        Path tempPath = Files.createTempFile("bookdrop-", originalFilename);

        try {
            file.transferTo(tempPath);
            setTemporaryFileOwnership(tempPath);

            Path finalPath = dropFolder.resolve(originalFilename);

            if (Files.exists(finalPath)) {
                throw ApiError.FILE_ALREADY_EXISTS.createException();
            }
            Files.move(tempPath, finalPath);

            log.info("File moved to book-drop folder: {}", finalPath);
            return null;
        } finally {
            Files.deleteIfExists(tempPath);
        }
    }

    private BookMetadata extractMetadata(BookFileExtension fileExt, File file) throws IOException {
        return switch (fileExt) {
            case PDF -> pdfMetadataExtractor.extractMetadata(file);
            case EPUB -> epubMetadataExtractor.extractMetadata(file);
            case CBZ, CBR, CB7 -> new BookMetadata();
        };
    }

    private void validateFile(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || BookFileExtension.fromFileName(originalFilename).isEmpty()) {
            throw ApiError.INVALID_FILE_FORMAT.createException("Unsupported file extension");
        }
        int maxSizeMb = appSettingService.getAppSettings().getMaxFileUploadSizeInMb();
        if (file.getSize() > maxSizeMb * 1024L * 1024L) {
            throw ApiError.FILE_TOO_LARGE.createException(maxSizeMb);
        }
    }

    private void setTemporaryFileOwnership(Path tempPath) throws IOException {
        UserPrincipalLookupService lookupService = FileSystems.getDefault()
                .getUserPrincipalLookupService();
        if (!userId.equals("0")) {
            UserPrincipal user = lookupService.lookupPrincipalByName(userId);
            Files.getFileAttributeView(tempPath, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS).setOwner(user);
        }
        if (!groupId.equals("0")) {
            GroupPrincipal group = lookupService.lookupPrincipalByGroupName(groupId);
            Files.getFileAttributeView(tempPath, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS).setGroup(group);
        }
    }

    private FileProcessResult processFile(String fileName, LibraryEntity libraryEntity, LibraryPathEntity libraryPathEntity, File storageFile, BookFileType type) {
        String subPath = FileUtils.getRelativeSubPath(libraryPathEntity.getPath(), storageFile.toPath());

        LibraryFile libraryFile = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileSubPath(subPath)
                .bookFileType(type)
                .fileName(fileName)
                .build();

        BookFileProcessor processor = processorRegistry.getProcessorOrThrow(type);
        return processor.processFile(libraryFile);
    }
}
