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
import org.booklore.service.kobo.KoboSpanMapService;
import org.booklore.util.FileUtils;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@AllArgsConstructor
@Service
public class BookDownloadService {

    private static final Pattern NON_ALPHANUMERIC_PATTERN = Pattern.compile("[^a-zA-Z0-9\\-_]");
    private static final Pattern ASCII_ONLY_PATTERN = Pattern.compile("\\p{ASCII}*");
    private final BookRepository bookRepository;
    private final BookFileRepository bookFileRepository;
    private final KepubConversionService kepubConversionService;
    private final CbxConversionService cbxConversionService;
    private final KoboSpanMapService koboSpanMapService;
    private final AppSettingService appSettingService;

    public ResponseEntity<StreamingResponseBody> downloadBook(Long bookId) {
        try {
            BookEntity bookEntity = bookRepository.findByIdWithBookFiles(bookId)
                    .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

            BookFileEntity primaryFile = bookEntity.getPrimaryBookFile();
            if (primaryFile == null) {
                throw ApiError.FAILED_TO_DOWNLOAD_FILE.createException(bookId);
            }
            Path libraryRoot = Path.of(bookEntity.getLibraryPath().getPath());
            Path file = FileUtils.requirePathWithinBase(primaryFile.getFullFilePath(), libraryRoot);

            if (!Files.exists(file)) {
                throw ApiError.FAILED_TO_DOWNLOAD_FILE.createException(bookId);
            }

            // Handle folder-based audiobooks - create ZIP
            if (primaryFile.isFolderBased() && Files.isDirectory(file)) {
                return downloadFolderAsZip(file, primaryFile.getFileName());
            }

            return streamFile(file);
        } catch (Exception e) {
            log.error("Failed to download book {}: {}", bookId, e.getMessage(), e);
            throw ApiError.FAILED_TO_DOWNLOAD_FILE.createException(bookId);
        }
    }

    public ResponseEntity<StreamingResponseBody> downloadBookFile(Long bookId, Long fileId) {
        try {
            BookFileEntity bookFileEntity = bookFileRepository.findByIdWithBookAndLibraryPath(fileId)
                    .orElseThrow(() -> ApiError.FILE_NOT_FOUND.createException(fileId));

            // Verify the file belongs to the specified book
            if (!bookFileEntity.getBook().getId().equals(bookId)) {
                throw ApiError.FILE_NOT_FOUND.createException(fileId);
            }

            Path libraryRoot = Path.of(bookFileEntity.getBook().getLibraryPath().getPath());
            Path file = FileUtils.requirePathWithinBase(bookFileEntity.getFullFilePath(), libraryRoot);

            if (!Files.exists(file)) {
                throw ApiError.FAILED_TO_DOWNLOAD_FILE.createException(fileId);
            }

            // Handle folder-based audiobooks - create ZIP
            if (bookFileEntity.isFolderBased() && Files.isDirectory(file)) {
                return downloadFolderAsZip(file, bookFileEntity.getFileName());
            }

            return streamFile(file);
        } catch (Exception e) {
            log.error("Failed to download book file {}: {}", fileId, e.getMessage(), e);
            throw ApiError.FAILED_TO_DOWNLOAD_FILE.createException(fileId);
        }
    }

    private ResponseEntity<StreamingResponseBody> streamFile(Path file) {
        StreamingResponseBody body = outputStream -> {
            try (InputStream in = Files.newInputStream(file)) {
                in.transferTo(outputStream);
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(file.toFile().length())
                .header(HttpHeaders.CONTENT_DISPOSITION, getContentDisposition(file))
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .body(body);
    }

    private String getContentDisposition(File file) {
        return getContentDisposition(file.getName());
    }

    private String getContentDisposition(Path path) {
        return getContentDisposition(path.getFileName().toString());
    }

    private String getContentDisposition(String filename) {
        Charset charset = ASCII_ONLY_PATTERN.matcher(filename).matches() ?
                StandardCharsets.US_ASCII : StandardCharsets.UTF_8;

        return ContentDisposition.builder("attachment")
                .filename(filename, charset)
                .build()
                .toString();
    }

    public ResponseEntity<StreamingResponseBody> downloadAllBookFiles(Long bookId) {
        BookEntity bookEntity = bookRepository.findByIdWithBookFiles(bookId)
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        List<BookFileEntity> allFiles = bookEntity.getBookFiles();
        if (allFiles == null || allFiles.isEmpty()) {
            throw ApiError.FILE_NOT_FOUND.createException(bookId);
        }

        Path libraryRoot = Path.of(bookEntity.getLibraryPath().getPath());

        // If only one file and it's not folder-based, download it directly
        if (allFiles.size() == 1) {
            BookFileEntity singleFile = allFiles.getFirst();
            Path filePath = FileUtils.requirePathWithinBase(singleFile.getFullFilePath(), libraryRoot);

            if (!Files.exists(filePath)) {
                throw ApiError.FAILED_TO_DOWNLOAD_FILE.createException(bookId);
            }

            // For folder-based audiobooks, let it fall through to ZIP creation
            if (!singleFile.isFolderBased() || !Files.isDirectory(filePath)) {
                return streamFile(filePath);
            }
        }

        // Resolve entity data upfront so streaming only handles file I/O
        List<ZipSource> sources = allFiles.stream()
                .sorted(Comparator.comparing(BookFileEntity::getFileName))
                .map(f -> new ZipSource(
                        FileUtils.requirePathWithinBase(f.getFullFilePath(), libraryRoot),
                        f.getFileName(),
                        f.isFolderBased()))
                .toList();

        String bookTitle = bookEntity.getMetadata() != null && bookEntity.getMetadata().getTitle() != null
                ? bookEntity.getMetadata().getTitle()
                : "book-" + bookId;
        String zipFileName = NON_ALPHANUMERIC_PATTERN.matcher(bookTitle).replaceAll("_") + ".zip";

        StreamingResponseBody body = outputStream -> {
            try (ZipOutputStream zos = new ZipOutputStream(outputStream)) {
                for (ZipSource source : sources) {
                    if (!Files.exists(source.path())) {
                        log.warn("Skipping missing file during ZIP creation: {}", source.path());
                        continue;
                    }

                    // Handle folder-based audiobooks - add all files from the folder
                    if (source.folderBased() && Files.isDirectory(source.path())) {
                        String folderPrefix = source.name() + "/";
                        try (var audioFiles = Files.list(source.path())) {
                            for (Path audioFile : audioFiles
                                    .filter(Files::isRegularFile)
                                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                                    .toList()) {
                                addZipEntry(zos, folderPrefix + audioFile.getFileName().toString(), audioFile);
                            }
                        }
                    } else {
                        addZipEntry(zos, source.name(), source.path());
                    }
                }
            }
            log.info("Successfully streamed ZIP for book {} with {} files", bookId, sources.size());
        };

        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, getContentDisposition(zipFileName))
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .body(body);
    }

    private void addZipEntry(ZipOutputStream zos, String entryName, Path file) throws IOException {
        ZipEntry zipEntry = new ZipEntry(entryName);
        zipEntry.setSize(Files.size(file));
        zos.putNextEntry(zipEntry);
        try (InputStream in = Files.newInputStream(file)) {
            in.transferTo(zos);
        }
        zos.closeEntry();
    }

    private record ZipSource(Path path, String name, boolean folderBased) {
    }

    public void downloadKoboBook(Long bookId, HttpServletResponse response) {
        BookEntity bookEntity = bookRepository.findByIdForKoboDownload(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

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

        boolean convertEpubToKepub = isEpub && !primaryFile.isFixedLayout() && koboSettings.isConvertToKepub() && primaryFile.getFileSizeKb() <= (long) koboSettings.getConversionLimitInMb() * 1024;
        boolean convertCbxToEpub = isCbx && koboSettings.isConvertCbxToEpub() && primaryFile.getFileSizeKb() <= (long) koboSettings.getConversionLimitInMbForCbx() * 1024;

        int compressionPercentage = koboSettings.getConversionImageCompressionPercentage();
        Path tempDir = null;
        try {
            Path libraryRoot = Path.of(bookEntity.getLibraryPath().getPath());
            Path normalizedInputPath = FileUtils.requirePathWithinBase(primaryFile.getFullFilePath(), libraryRoot);
            File inputFile = normalizedInputPath.toFile();
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
                try {
                    koboSpanMapService.computeAndStoreIfNeeded(primaryFile, fileToSend);
                } catch (Exception e) {
                    log.warn("Failed to compute Kobo span map for file {}: {}", primaryFile.getId(), e.getMessage());
                }
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
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, getContentDisposition(file));
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

    private ResponseEntity<StreamingResponseBody> downloadFolderAsZip(Path folderPath, String folderName) {
        StreamingResponseBody body = outputStream -> {
            try (ZipOutputStream zos = new ZipOutputStream(outputStream);
                 var files = Files.list(folderPath)) {
                // Get all files in the folder, sorted by name
                for (Path audioFile : files
                        .filter(Files::isRegularFile)
                        .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                        .toList()) {
                    zos.putNextEntry(new ZipEntry(audioFile.getFileName().toString()));
                    Files.copy(audioFile, zos);
                    zos.closeEntry();
                }
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, getContentDisposition(folderName + ".zip"))
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .body(body);
    }
}
