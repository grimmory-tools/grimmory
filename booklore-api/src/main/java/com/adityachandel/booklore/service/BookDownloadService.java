package com.adityachandel.booklore.service;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.dto.settings.KoboSettings;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.service.kobo.KepubConversionService;
import com.adityachandel.booklore.util.FileUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@AllArgsConstructor
@Service
public class BookDownloadService {

    private final BookRepository bookRepository;
    private final KepubConversionService kepubConversionService;
    private final AppSettingService appSettingService;

    public ResponseEntity<Resource> downloadBook(Long bookId) {
        try {
            BookEntity bookEntity = bookRepository.findById(bookId)
                    .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

            Path file = Paths.get(FileUtils.getBookFullPath(bookEntity)).toAbsolutePath().normalize();
            File bookFile = file.toFile();

            if (!bookFile.exists()) {
                throw ApiError.FAILED_TO_DOWNLOAD_FILE.createException(bookId);
            }

            InputStream inputStream = new FileInputStream(bookFile);
            InputStreamResource resource = new InputStreamResource(inputStream);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(bookFile.length())
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFileName() + "\"")
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .header(HttpHeaders.EXPIRES, "0")
                    .body(resource);
        } catch (Exception e) {
            log.error("Failed to download book {}: {}", bookId, e.getMessage(), e);
            throw ApiError.FAILED_TO_DOWNLOAD_FILE.createException(bookId);
        }
    }

    public void downloadKoboBook(Long bookId, HttpServletResponse response) {
        BookEntity bookEntity = bookRepository.findById(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        if (bookEntity.getBookType() != BookFileType.EPUB) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("The requested book is not an EPUB file.");
        }

        KoboSettings koboSettings = appSettingService.getAppSettings().getKoboSettings();
        if (koboSettings == null) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("Kobo settings not found.");
        }


        boolean asKepub = koboSettings.isConvertToKepub() && bookEntity.getFileSizeKb() <= (long) koboSettings.getConversionLimitInMb() * 1024;

        Path tempDir = null;
        try {
            File inputFile = new File(FileUtils.getBookFullPath(bookEntity));
            File fileToSend = inputFile;

            if (asKepub) {
                tempDir = Files.createTempDirectory("kepub-output");
                fileToSend = kepubConversionService.convertEpubToKepub(inputFile, tempDir.toFile());
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
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getName() + "\"");
    }

    private void streamFileToResponse(File file, HttpServletResponse response) throws IOException {
        try (InputStream in = Files.newInputStream(file.toPath())) {
            in.transferTo(response.getOutputStream());
            response.getOutputStream().flush();
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
}
