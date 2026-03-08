package org.booklore.service.reader;

import org.booklore.exception.ApiError;
import org.booklore.model.dto.response.AudiobookInfo;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.BookRepository;
import org.booklore.service.FileStreamingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Service for audiobook reader operations.
 * Orchestrates metadata extraction, file access, and streaming.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AudiobookReaderService {

    private static final Pattern NON_ASCII_PATTERN = Pattern.compile("[^\\x00-\\x7F]");

    private final BookRepository bookRepository;
    private final AudioMetadataService audioMetadataService;
    private final AudioFileUtilityService audioFileUtility;
    private final FileStreamingService fileStreamingService;

    /**
     * Get audiobook information including metadata, chapters, and tracks.
     */
    public AudiobookInfo getAudiobookInfo(Long bookId, String bookType) {
        BookFileEntity bookFile = getAudiobookFile(bookId, bookType);
        Path audioPath = bookFile.getFullFilePath();

        try {
            return audioMetadataService.getMetadata(bookFile, audioPath);
        } catch (Exception e) {
            log.error("Failed to read audiobook metadata for book {}", bookId, e);
            throw ApiError.FILE_READ_ERROR.createException("Failed to read audiobook: " + e.getMessage());
        }
    }

    /**
     * Get the path to an audio file for streaming.
     * For folder-based audiobooks, returns the path to a specific track.
     */
    public Path getAudioFilePath(Long bookId, String bookType, Integer trackIndex) {
        BookFileEntity bookFile = getAudiobookFile(bookId, bookType);

        if (bookFile.isFolderBased()) {
            if (trackIndex == null) {
                trackIndex = 0;
            }
            List<Path> tracks = audioFileUtility.listAudioFiles(bookFile.getFullFilePath());
            if (trackIndex < 0 || trackIndex >= tracks.size()) {
                throw ApiError.FILE_NOT_FOUND.createException("Track index out of range: " + trackIndex);
            }
            return tracks.get(trackIndex);
        } else {
            return bookFile.getFullFilePath();
        }
    }

    /**
     * Stream an audio file with HTTP Range support for seeking.
     */
    public void streamWithRangeSupport(Path filePath, HttpServletRequest request, HttpServletResponse response) throws IOException {
        String contentType = audioFileUtility.getContentType(filePath);
        fileStreamingService.streamWithRangeSupport(filePath, contentType, request, response);
    }

    /**
     * Get embedded cover art from an audiobook file.
     */
    public byte[] getEmbeddedCoverArt(Long bookId, String bookType) {
        BookFileEntity bookFile = getAudiobookFile(bookId, bookType);
        Path audioPath = bookFile.isFolderBased() ? bookFile.getFirstAudioFile() : bookFile.getFullFilePath();
        return audioMetadataService.getEmbeddedCoverArt(audioPath);
    }

    /**
     * Get the MIME type of embedded cover art.
     */
    public String getCoverArtMimeType(Long bookId, String bookType) {
        BookFileEntity bookFile = getAudiobookFile(bookId, bookType);
        Path audioPath = bookFile.isFolderBased() ? bookFile.getFirstAudioFile() : bookFile.getFullFilePath();
        return audioMetadataService.getCoverArtMimeType(audioPath);
    }

    /**
     * Get the content type for an audio file.
     */
    public String getContentType(Path audioPath) {
        return audioFileUtility.getContentType(audioPath);
    }

    /**
     * Download the full audiobook.
     * Single-file audiobooks are returned as-is; folder-based audiobooks are streamed as a ZIP archive.
     */
    public ResponseEntity<Resource> downloadAudiobook(Long bookId, String bookType) {
        BookFileEntity bookFile = getAudiobookFile(bookId, bookType);
        Path filePath = bookFile.getFullFilePath();

        try {
            if (bookFile.isFolderBased() && Files.isDirectory(filePath)) {
                return downloadFolderAsZip(filePath, bookFile.getFileName());
            }

            if (!Files.exists(filePath)) {
                throw ApiError.FILE_NOT_FOUND.createException("Audiobook file not found for book " + bookId);
            }

            Resource resource = new FileSystemResource(filePath.toFile());
            String fileName = filePath.getFileName().toString();
            String contentDisposition = buildContentDisposition(fileName);
            String contentType = audioFileUtility.getContentType(filePath);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .contentLength(filePath.toFile().length())
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .header(HttpHeaders.EXPIRES, "0")
                    .body(resource);
        } catch (Exception e) {
            log.error("Failed to download audiobook for book {}: {}", bookId, e.getMessage(), e);
            throw ApiError.FAILED_TO_DOWNLOAD_FILE.createException(bookId);
        }
    }

    /**
     * Download a single track from a folder-based audiobook (0-indexed).
     */
    public ResponseEntity<Resource> downloadAudiobookTrack(Long bookId, Integer trackIndex, String bookType) {
        BookFileEntity bookFile = getAudiobookFile(bookId, bookType);

        if (!bookFile.isFolderBased()) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("Track download is only available for folder-based audiobooks");
        }

        List<Path> tracks = audioFileUtility.listAudioFiles(bookFile.getFullFilePath());
        if (trackIndex < 0 || trackIndex >= tracks.size()) {
            throw ApiError.FILE_NOT_FOUND.createException("Track index out of range: " + trackIndex);
        }

        Path trackPath = tracks.get(trackIndex);
        if (!Files.exists(trackPath)) {
            throw ApiError.FILE_NOT_FOUND.createException("Track file not found: " + trackPath.getFileName());
        }

        try {
            Resource resource = new FileSystemResource(trackPath.toFile());
            String fileName = trackPath.getFileName().toString();
            String contentDisposition = buildContentDisposition(fileName);
            String contentType = audioFileUtility.getContentType(trackPath);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .contentLength(trackPath.toFile().length())
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .header(HttpHeaders.EXPIRES, "0")
                    .body(resource);
        } catch (Exception e) {
            log.error("Failed to download track {} for book {}: {}", trackIndex, bookId, e.getMessage(), e);
            throw ApiError.FAILED_TO_DOWNLOAD_FILE.createException(bookId);
        }
    }

    private String buildContentDisposition(String fileName) {
        String encodedFilename = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        String fallbackFilename = NON_ASCII_PATTERN.matcher(fileName).replaceAll("_");
        return String.format("attachment; filename=\"%s\"; filename*=UTF-8''%s", fallbackFilename, encodedFilename);
    }

    private ResponseEntity<Resource> downloadFolderAsZip(Path folderPath, String folderName) throws IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();

        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            List<Path> files = Files.list(folderPath)
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();

            for (Path audioFile : files) {
                ZipEntry entry = new ZipEntry(audioFile.getFileName().toString());
                zos.putNextEntry(entry);
                try (InputStream in = Files.newInputStream(audioFile)) {
                    in.transferTo(zos);
                }
                zos.closeEntry();
            }
        }

        byte[] zipBytes = baos.toByteArray();
        Resource resource = new org.springframework.core.io.ByteArrayResource(zipBytes);
        String zipFileName = folderName + ".zip";

        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, buildContentDisposition(zipFileName))
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(zipBytes.length))
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .body(resource);
    }

    private BookFileEntity getAudiobookFile(Long bookId, String bookType) {
        BookEntity bookEntity = bookRepository.findByIdWithBookFiles(bookId)
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        if (bookType != null) {
            BookFileType requestedType = BookFileType.valueOf(bookType.toUpperCase());
            return bookEntity.getBookFiles().stream()
                    .filter(bf -> bf.getBookType() == requestedType)
                    .findFirst()
                    .orElseThrow(() -> ApiError.FILE_NOT_FOUND.createException("No file of type " + bookType + " found for book"));
        }

        return bookEntity.getBookFiles().stream()
                .filter(bf -> bf.getBookType() == BookFileType.AUDIOBOOK && bf.isBookFormat())
                .findFirst()
                .orElseThrow(() -> ApiError.FILE_NOT_FOUND.createException("No audiobook file found for book " + bookId));
    }
}
