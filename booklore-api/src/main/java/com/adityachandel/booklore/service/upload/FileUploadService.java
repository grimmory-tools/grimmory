package com.adityachandel.booklore.service.upload;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.enums.BookFileExtension;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.service.fileprocessor.CbxProcessor;
import com.adityachandel.booklore.service.fileprocessor.EpubProcessor;
import com.adityachandel.booklore.service.fileprocessor.PdfProcessor;
import com.adityachandel.booklore.service.metadata.extractor.EpubMetadataExtractor;
import com.adityachandel.booklore.service.metadata.extractor.PdfMetadataExtractor;
import com.adityachandel.booklore.service.monitoring.MonitoringService;
import com.adityachandel.booklore.util.FileUtils;
import com.adityachandel.booklore.util.PathPatternResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.Objects;

@RequiredArgsConstructor
@Service
@Slf4j
public class FileUploadService {

    private final LibraryRepository libraryRepository;
    private final PdfProcessor pdfProcessor;
    private final EpubProcessor epubProcessor;
    private final CbxProcessor cbxProcessor;
    private final NotificationService notificationService;
    private final AppSettingService appSettingService;
    private final PdfMetadataExtractor pdfMetadataExtractor;
    private final EpubMetadataExtractor epubMetadataExtractor;
    private final MonitoringService monitoringService;

    @Value("${PUID:0}")
    private String userId;

    @Value("${GUID:0}")
    private String groupId;

    public Book uploadFile(MultipartFile file, long libraryId, long pathId) throws IOException {
        validateFile(file);

        LibraryEntity libraryEntity = libraryRepository.findById(libraryId)
                .orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(libraryId));

        LibraryPathEntity libraryPathEntity = libraryEntity.getLibraryPaths()
                .stream()
                .filter(p -> p.getId() == pathId)
                .findFirst()
                .orElseThrow(() -> ApiError.INVALID_LIBRARY_PATH.createException(libraryId));

        Path tempPath = Files.createTempFile("upload-", Objects.requireNonNull(file.getOriginalFilename()));
        Book book;

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

            book = processFile(finalFile.getName(), libraryEntity, libraryPathEntity, finalFile, fileExt.getType());
            notificationService.sendMessage(Topic.BOOK_ADD, book);

            return book;

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

    private Book processFile(String fileName, LibraryEntity libraryEntity, LibraryPathEntity libraryPathEntity, File storageFile, BookFileType fileType) {
        String subPath = FileUtils.getRelativeSubPath(libraryPathEntity.getPath(), storageFile.toPath());

        LibraryFile libraryFile = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileSubPath(subPath)
                .bookFileType(fileType)
                .fileName(fileName)
                .build();

        return switch (fileType) {
            case PDF -> pdfProcessor.processFile(libraryFile, false);
            case EPUB -> epubProcessor.processFile(libraryFile, false);
            case CBX -> cbxProcessor.processFile(libraryFile, false);
        };
    }

}