package org.booklore.service.file;

import org.booklore.mapper.AdditionalFileMapper;
import org.booklore.model.dto.BookFile;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.repository.BookAdditionalFileRepository;
import org.booklore.repository.BookRepository;
import org.booklore.service.monitoring.MonitoringRegistrationService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@AllArgsConstructor
@Service
public class AdditionalFileService {

    private static final Pattern NON_ASCII = Pattern.compile("[^\\x00-\\x7F]");

    private final BookAdditionalFileRepository additionalFileRepository;
    private final BookRepository bookRepository;
    private final AdditionalFileMapper additionalFileMapper;
    private final MonitoringRegistrationService monitoringRegistrationService;

    public List<BookFile> getAdditionalFilesByBookId(Long bookId) {
        List<BookFileEntity> entities = additionalFileRepository.findByBookId(bookId);
        return additionalFileMapper.toAdditionalFiles(entities);
    }

    public List<BookFile> getAdditionalFilesByBookIdAndIsBook(Long bookId, boolean isBook) {
        List<BookFileEntity> entities = additionalFileRepository.findByBookIdAndIsBookFormat(bookId, isBook);
        return additionalFileMapper.toAdditionalFiles(entities);
    }

    @Transactional
    public void deleteAdditionalFile(Long fileId) {
        Optional<BookFileEntity> fileOpt = additionalFileRepository.findById(fileId);
        if (fileOpt.isEmpty()) {
            throw new IllegalArgumentException("Additional file not found with id: " + fileId);
        }

        BookFileEntity file = fileOpt.get();
        BookEntity book = file.getBook();

        try {
            monitoringRegistrationService.unregisterSpecificPath(file.getFullFilePath().getParent());

            Path filePath = file.getFullFilePath();
            if (file.isFolderBased() && Files.isDirectory(filePath)) {
                deleteDirectoryRecursively(filePath);
                log.info("Deleted folder-based audiobook: {}", filePath);
            } else {
                Files.deleteIfExists(filePath);
                log.info("Deleted additional file: {}", filePath);
            }

            additionalFileRepository.delete(file);
        } catch (IOException e) {
            log.warn("Failed to delete physical file: {}", file.getFullFilePath(), e);
            additionalFileRepository.delete(file);
        }

        if (file.isBook() && book != null) {
            book.getBookFiles().remove(file);
        }
    }

    private void deleteDirectoryRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;

        try (var walk = Files.walk(path)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(java.io.File::delete);
        }
    }

    public ResponseEntity<Resource> downloadAdditionalFile(Long fileId) throws IOException {
        Optional<BookFileEntity> fileOpt = additionalFileRepository.findById(fileId);
        if (fileOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        BookFileEntity file = fileOpt.get();
        Path filePath = file.getFullFilePath();

        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        // Handle folder-based audiobooks - create a ZIP file
        if (file.isFolderBased() && Files.isDirectory(filePath)) {
            return downloadFolderAsZip(file, filePath);
        }

        Resource resource = new UrlResource(filePath.toUri());

        String encodedFilename = URLEncoder.encode(file.getFileName(), StandardCharsets.UTF_8).replace("+", "%20");
        String fallbackFilename = NON_ASCII.matcher(file.getFileName()).replaceAll("_");
        String contentDisposition = String.format("attachment; filename=\"%s\"; filename*=UTF-8''%s",
                fallbackFilename, encodedFilename);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .body(resource);
    }

    private ResponseEntity<Resource> downloadFolderAsZip(BookFileEntity file, Path folderPath) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

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
        Resource resource = new ByteArrayResource(zipBytes);

        String zipFileName = file.getFileName() + ".zip";
        String encodedFilename = URLEncoder.encode(zipFileName, StandardCharsets.UTF_8).replace("+", "%20");
        String fallbackFilename = NON_ASCII.matcher(zipFileName).replaceAll("_");
        String contentDisposition = String.format("attachment; filename=\"%s\"; filename*=UTF-8''%s",
                fallbackFilename, encodedFilename);

        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(zipBytes.length))
                .body(resource);
    }
}
