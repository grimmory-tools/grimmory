package org.booklore.service.book;

import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.settings.KoboSettings;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.BookFileRepository;
import org.booklore.repository.BookRepository;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.kobo.CbxConversionService;
import org.booklore.service.kobo.KepubConversionService;
import org.booklore.util.FileUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@AllArgsConstructor
@Service
public class BookDownloadService {

    private static final Pattern NON_ASCII_PATTERN = Pattern.compile("[^\\x00-\\x7F]");

    private final BookRepository bookRepository;
    private final BookFileRepository bookFileRepository;
    private final KepubConversionService kepubConversionService;
    private final CbxConversionService cbxConversionService;
    private final AppSettingService appSettingService;

    public ResponseEntity<Resource> downloadBook(Long bookId) {
        try {
            BookEntity bookEntity = bookRepository.findByIdWithBookFiles(bookId)
                    .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

            BookFileEntity primaryFile = bookEntity.getPrimaryBookFile();
            if (primaryFile == null) {
                throw ApiError.FAILED_TO_DOWNLOAD_FILE.createException(bookId);
            }
            Path file = Paths.get(FileUtils.getBookFullPath(bookEntity)).toAbsolutePath().normalize();

            if (!Files.exists(file)) {
                throw ApiError.FAILED_TO_DOWNLOAD_FILE.createException(bookId);
            }

            // Handle folder-based audiobooks - create ZIP
            if (primaryFile.isFolderBased() && Files.isDirectory(file)) {
                return downloadFolderAsZip(file, primaryFile.getFileName());
            }

            File bookFile = file.toFile();

            // Use FileSystemResource which properly handles file resources and closing
            Resource resource = new FileSystemResource(bookFile);

            String encodedFilename = URLEncoder.encode(file.getFileName().toString(), StandardCharsets.UTF_8)
                    .replace("+", "%20");
            String fallbackFilename = NON_ASCII_PATTERN.matcher(file.getFileName().toString()).replaceAll("_");
            String contentDisposition = String.format("attachment; filename=\"%s\"; filename*=UTF-8''%s",
                    fallbackFilename, encodedFilename);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(bookFile.length())
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .header(HttpHeaders.EXPIRES, "0")
                    .body(resource);
        } catch (Exception e) {
            log.error("Failed to download book {}: {}", bookId, e.getMessage(), e);
            throw ApiError.FAILED_TO_DOWNLOAD_FILE.createException(bookId);
        }
    }

    public ResponseEntity<Resource> downloadBookFile(Long bookId, Long fileId) {
        try {
            BookFileEntity bookFileEntity = bookFileRepository.findByIdWithBookAndLibraryPath(fileId)
                    .orElseThrow(() -> ApiError.FILE_NOT_FOUND.createException(fileId));

            // Verify the file belongs to the specified book
            if (!bookFileEntity.getBook().getId().equals(bookId)) {
                throw ApiError.FILE_NOT_FOUND.createException(fileId);
            }

            Path file = bookFileEntity.getFullFilePath().toAbsolutePath().normalize();

            if (!Files.exists(file)) {
                throw ApiError.FAILED_TO_DOWNLOAD_FILE.createException(fileId);
            }

            // Handle folder-based audiobooks - create ZIP
            if (bookFileEntity.isFolderBased() && Files.isDirectory(file)) {
                return downloadFolderAsZip(file, bookFileEntity.getFileName());
            }

            File bookFile = file.toFile();
            Resource resource = new FileSystemResource(bookFile);

            String encodedFilename = URLEncoder.encode(file.getFileName().toString(), StandardCharsets.UTF_8)
                    .replace("+", "%20");
            String fallbackFilename = NON_ASCII_PATTERN.matcher(file.getFileName().toString()).replaceAll("_");
            String contentDisposition = String.format("attachment; filename=\"%s\"; filename*=UTF-8''%s",
                    fallbackFilename, encodedFilename);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(bookFile.length())
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .header(HttpHeaders.EXPIRES, "0")
                    .body(resource);
        } catch (Exception e) {
            log.error("Failed to download book file {}: {}", fileId, e.getMessage(), e);
            throw ApiError.FAILED_TO_DOWNLOAD_FILE.createException(fileId);
        }
    }

    public void downloadAllBookFiles(Long bookId, HttpServletResponse response) {
        BookEntity bookEntity = bookRepository.findByIdWithBookFiles(bookId)
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        List<BookFileEntity> allFiles = bookEntity.getBookFiles();
        if (allFiles == null || allFiles.isEmpty()) {
            throw ApiError.FILE_NOT_FOUND.createException(bookId);
        }

        // If only one file and it's not folder-based, download it directly
        if (allFiles.size() == 1) {
            BookFileEntity singleFile = allFiles.get(0);
            Path filePath = singleFile.getFullFilePath();

            if (!Files.exists(filePath)) {
                throw ApiError.FAILED_TO_DOWNLOAD_FILE.createException(bookId);
            }

            // For folder-based audiobooks, let it fall through to ZIP creation
            if (!singleFile.isFolderBased() || !Files.isDirectory(filePath)) {
                File file = filePath.toFile();
                setResponseHeaders(response, file);
                streamFileToResponse(file, response);
                return;
            }
        }

        // Sort files by filename for consistent ordering
        allFiles.sort(Comparator.comparing(BookFileEntity::getFileName));

        // Create ZIP with all files
        String bookTitle = bookEntity.getMetadata() != null && bookEntity.getMetadata().getTitle() != null
                ? bookEntity.getMetadata().getTitle()
                : "book-" + bookId;
        String safeTitle = bookTitle.replaceAll("[^a-zA-Z0-9\\-_]", "_");
        String zipFileName = safeTitle + ".zip";

        response.setContentType("application/zip");
        String encodedFilename = URLEncoder.encode(zipFileName, StandardCharsets.UTF_8).replace("+", "%20");
        String fallbackFilename = NON_ASCII_PATTERN.matcher(zipFileName).replaceAll("_");
        String contentDisposition = String.format("attachment; filename=\"%s\"; filename*=UTF-8''%s",
                fallbackFilename, encodedFilename);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, contentDisposition);

        try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
            for (BookFileEntity bookFile : allFiles) {
                Path filePath = bookFile.getFullFilePath();

                if (!Files.exists(filePath)) {
                    log.warn("Skipping missing file during ZIP creation: {}", filePath);
                    continue;
                }

                // Handle folder-based audiobooks - add all files from the folder
                if (bookFile.isFolderBased() && Files.isDirectory(filePath)) {
                    String folderPrefix = bookFile.getFileName() + "/";
                    try (var audioFiles = Files.list(filePath)) {
                        for (Path audioFile : audioFiles
                                .filter(Files::isRegularFile)
                                .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                                .toList()) {
                            String entryName = folderPrefix + audioFile.getFileName().toString();
                            ZipEntry zipEntry = new ZipEntry(entryName);
                            zipEntry.setSize(Files.size(audioFile));
                            zos.putNextEntry(zipEntry);

                            try (InputStream fis = Files.newInputStream(audioFile)) {
                                fis.transferTo(zos);
                            }

                            zos.closeEntry();
                        }
                    }
                } else {
                    // Regular file
                    ZipEntry zipEntry = new ZipEntry(bookFile.getFileName());
                    zipEntry.setSize(Files.size(filePath));
                    zos.putNextEntry(zipEntry);

                    try (InputStream fis = Files.newInputStream(filePath)) {
                        fis.transferTo(zos);
                    }

                    zos.closeEntry();
                }
            }
            zos.finish();
            response.getOutputStream().flush();

            log.info("Successfully created and streamed ZIP for book {} with {} files", bookId, allFiles.size());
        } catch (IOException e) {
            log.error("Failed to create ZIP for book {}: {}", bookId, e.getMessage(), e);
            throw ApiError.FAILED_TO_DOWNLOAD_FILE.createException(bookId);
        }
    }

    public void downloadKoboBook(Long bookId, HttpServletResponse response) {
        BookEntity bookEntity = bookRepository.findByIdWithBookFiles(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        var primaryFile = bookEntity.getPrimaryBookFile();
        if (primaryFile == null) {
            throw ApiError.FAILED_TO_DOWNLOAD_FILE.createException(bookId);
        }
        boolean isEpub = primaryFile.getBookType() == BookFileType.EPUB;
        boolean isCbx = primaryFile.getBookType() == BookFileType.CBX;

        if (!isEpub && !isCbx) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("The requested book is not an EPUB or CBX file.");
        }

        KoboSettings koboSettings = appSettingService.getAppSettings().getKoboSettings();
        if (koboSettings == null) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("Kobo settings not found.");
        }

        boolean convertEpubToKepub = isEpub && koboSettings.isConvertToKepub() && primaryFile.getFileSizeKb() <= (long) koboSettings.getConversionLimitInMb() * 1024;
        boolean convertCbxToEpub = isCbx && koboSettings.isConvertCbxToEpub() && primaryFile.getFileSizeKb() <= (long) koboSettings.getConversionLimitInMbForCbx() * 1024;

        int compressionPercentage = koboSettings.getConversionImageCompressionPercentage();
        Path tempDir = null;
        try {
            File inputFile = new File(FileUtils.getBookFullPath(bookEntity));
            File fileToSend = inputFile;

            if (convertCbxToEpub || convertEpubToKepub) {
                tempDir = Files.createTempDirectory("kobo-conversion");
            }

            if (convertCbxToEpub) {
                fileToSend = cbxConversionService.convertCbxToEpub(inputFile, tempDir.toFile(), bookEntity,compressionPercentage);
            }

            if (convertEpubToKepub) {
                fileToSend = kepubConversionService.convertEpubToKepub(inputFile, tempDir.toFile(),
                    koboSettings.isForceEnableHyphenation());
            }

            setResponseHeaders(response, fileToSend);
            streamFileToResponse(fileToSend, response);

            log.info("Successfully streamed {} ({} bytes) to client", fileToSend.getName(), fileToSend.length());

        } catch (Exception e) {
            log.error("Failed to download kobo book {}: {}", bookId, e.getMessage(), e);
            throw ApiError.FAILED_TO_DOWNLOAD_FILE.createException(bookId);
        } finally {
            cleanupTempDirectory(tempDir);
        }
    }

    private void setResponseHeaders(HttpServletResponse response, File file) {
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setContentLengthLong(file.length());
        String encodedFilename = URLEncoder.encode(file.getName(), StandardCharsets.UTF_8).replace("+", "%20");
        String fallbackFilename = NON_ASCII_PATTERN.matcher(file.getName()).replaceAll("_");
        String contentDisposition = String.format("attachment; filename=\"%s\"; filename*=UTF-8''%s",
                fallbackFilename, encodedFilename);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, contentDisposition);
    }

    private void streamFileToResponse(File file, HttpServletResponse response) {
        try (InputStream in = Files.newInputStream(file.toPath())) {
            in.transferTo(response.getOutputStream());
            response.getOutputStream().flush();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to stream file to response", e);
        }
    }

    private void cleanupTempDirectory(Path tempDir) {
        if (tempDir != null) {
            try {
                FileSystemUtils.deleteRecursively(tempDir);
                log.debug("Deleted temporary directory {}", tempDir);
            } catch (Exception e) {
                log.warn("Failed to delete temporary directory {}: {}", tempDir, e.getMessage());
            }
        }
    }

    private ResponseEntity<Resource> downloadFolderAsZip(Path folderPath, String folderName) throws IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();

        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Get all files in the folder, sorted by name
            try (var files = Files.list(folderPath)) {
                for (Path audioFile : files
                        .filter(Files::isRegularFile)
                        .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                        .toList()) {
                    ZipEntry entry = new ZipEntry(audioFile.getFileName().toString());
                    zos.putNextEntry(entry);
                    Files.copy(audioFile, zos);
                    zos.closeEntry();
                }
            }
        }

        byte[] zipBytes = baos.toByteArray();
        Resource resource = new org.springframework.core.io.ByteArrayResource(zipBytes);

        String zipFileName = folderName + ".zip";
        String encodedFilename = URLEncoder.encode(zipFileName, StandardCharsets.UTF_8).replace("+", "%20");
        String fallbackFilename = NON_ASCII_PATTERN.matcher(zipFileName).replaceAll("_");
        String contentDisposition = String.format("attachment; filename=\"%s\"; filename*=UTF-8''%s",
                fallbackFilename, encodedFilename);

        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(zipBytes.length))
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .body(resource);
    }
}
