package com.adityachandel.booklore.service;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.UploadedFileMetadata;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.service.fileprocessor.EpubProcessor;
import com.adityachandel.booklore.service.fileprocessor.PdfProcessor;
import com.adityachandel.booklore.util.FileUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Optional;

@RequiredArgsConstructor
@Service
@Slf4j
public class FileUploadService {

    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024;
    private static final String PDF_MIME_TYPE = "application/pdf";
    private static final String EPUB_MIME_TYPE = "application/epub+zip";

    private final LibraryRepository libraryRepository;
    private final PdfProcessor pdfProcessor;
    private final EpubProcessor epubProcessor;
    private final NotificationService notificationService;
    private final AppSettingService appSettingService;
    private final PdfMetadataExtractor pdfMetadataExtractor;
    private final EpubMetadataExtractor epubMetadataExtractor;

    public Book uploadFile(MultipartFile file, long libraryId, long pathId) throws IOException {
        String uploadPattern = appSettingService.getAppSettings().getUploadPattern();
        validateFile(file);

        LibraryEntity libraryEntity = libraryRepository.findById(libraryId)
                .orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(libraryId));

        LibraryPathEntity libraryPathEntity = libraryEntity.getLibraryPaths()
                .stream()
                .filter(p -> p.getId() == pathId)
                .findFirst()
                .orElseThrow(() -> ApiError.INVALID_LIBRARY_PATH.createException(libraryId));

        Path tempPath = Files.createTempFile("upload-", Objects.requireNonNull(file.getOriginalFilename()));
        UploadedFileMetadata metadata;

        try {
            file.transferTo(tempPath);

            String fileType = Objects.requireNonNull(file.getContentType());

            if (fileType.equalsIgnoreCase(PDF_MIME_TYPE)) {
                metadata = pdfMetadataExtractor.extractMetadata(tempPath.toString());
            } else if (fileType.equalsIgnoreCase(EPUB_MIME_TYPE)) {
                metadata = epubMetadataExtractor.extractMetadata(tempPath.toString());
            } else {
                throw ApiError.INVALID_FILE_FORMAT.createException("Unsupported file type.");
            }

            String title = Optional.ofNullable(metadata.getTitle()).orElse("").trim();
            String authors = metadata.getAuthors() != null ? String.join(", ", metadata.getAuthors()) : "";
            String relativePath = resolveUploadPath(uploadPattern, title, authors, file.getOriginalFilename());

            Path finalPath = Paths.get(libraryPathEntity.getPath(), relativePath);
            File finalFile = finalPath.toFile();

            if (finalFile.exists()) {
                throw ApiError.FILE_ALREADY_EXISTS.createException();
            }

            Files.createDirectories(finalPath.getParent());
            Files.move(tempPath, finalPath);

            log.info("File uploaded to final location: {}", finalPath);

            Book book = processFile(file, libraryEntity, libraryPathEntity, finalFile);
            notificationService.sendMessage(Topic.BOOK_ADD, book);
            log.info("Book processed successfully: {}", book.getMetadata().getTitle());
            return book;

        } catch (IOException e) {
            throw ApiError.FILE_READ_ERROR.createException(e.getMessage());
        } finally {
            Files.deleteIfExists(tempPath);
        }
    }

    private void validateFile(MultipartFile file) {
        String fileType = file.getContentType();
        if (!PDF_MIME_TYPE.equals(fileType) && !EPUB_MIME_TYPE.equals(fileType)) {
            throw ApiError.INVALID_FILE_FORMAT.createException();
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw ApiError.FILE_TOO_LARGE.createException();
        }
    }

    private Book processFile(MultipartFile file, LibraryEntity libraryEntity, LibraryPathEntity libraryPathEntity, File storageFile) {
        BookFileType fileType = determineFileType(Objects.requireNonNull(file.getContentType()));
        if (fileType == null) {
            throw ApiError.INVALID_FILE_FORMAT.createException();
        }

        String subPath = FileUtils.getRelativeSubPath(libraryPathEntity.getPath(), storageFile.toPath());

        LibraryFile libraryFile = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileSubPath(subPath)
                .bookFileType(fileType)
                .fileName(file.getOriginalFilename())
                .build();

        return switch (fileType) {
            case PDF -> pdfProcessor.processFile(libraryFile, false);
            case EPUB -> epubProcessor.processFile(libraryFile, false);
        };
    }

    private String resolveUploadPath(String pattern, String title, String authors, String filename) {
        String path = pattern;
        boolean hasTitle = !title.isEmpty();
        boolean hasAuthors = !authors.isEmpty();

        if (hasTitle) {
            path = path.replace("{title}", sanitize(title));
        }
        if (hasAuthors) {
            path = path.replace("{authors}", sanitize(authors));
        }
        if (!hasTitle && pattern.contains("{title}")) {
            return filename;
        }
        if (!hasAuthors && pattern.contains("{authors}")) {
            return hasTitle ? path.replace("{authors}", "").replaceAll("//+", "/") + "/" + filename : filename;
        }
        return path + "/" + filename;
    }

    private String sanitize(String value) {
        return value.replaceAll("[\\/:*?\"<>|]", "").trim();
    }

    private BookFileType determineFileType(String fileType) {
        return switch (fileType.toLowerCase()) {
            case PDF_MIME_TYPE -> BookFileType.PDF;
            case EPUB_MIME_TYPE -> BookFileType.EPUB;
            default -> null;
        };
    }
}