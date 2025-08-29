package com.adityachandel.booklore.service;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.mapper.AdditionalFileMapper;
import com.adityachandel.booklore.model.dto.AdditionalFile;
import com.adityachandel.booklore.model.entity.BookAdditionalFileEntity;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.enums.AdditionalFileType;
import com.adityachandel.booklore.repository.BookAdditionalFileRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.monitoring.MonitoringService;
import com.adityachandel.booklore.util.FileUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@AllArgsConstructor
@Service
public class AdditionalFileService {

    private final BookAdditionalFileRepository additionalFileRepository;
    private final AdditionalFileMapper additionalFileMapper;

    public List<AdditionalFile> getAdditionalFilesByBookId(Long bookId) {
        List<BookAdditionalFileEntity> entities = additionalFileRepository.findByBookId(bookId);
        return additionalFileMapper.toAdditionalFiles(entities);
    }

    public List<AdditionalFile> getAdditionalFilesByBookIdAndType(Long bookId, AdditionalFileType type) {
        List<BookAdditionalFileEntity> entities = additionalFileRepository.findByBookIdAndAdditionalFileType(bookId, type);
        return additionalFileMapper.toAdditionalFiles(entities);
    }

    @Transactional
    public void deleteAdditionalFile(Long fileId) {
        Optional<BookAdditionalFileEntity> fileOpt = additionalFileRepository.findById(fileId);
        if (fileOpt.isEmpty()) {
            throw new IllegalArgumentException("Additional file not found with id: " + fileId);
        }

        BookAdditionalFileEntity file = fileOpt.get();

        // Delete physical file
        try {
            Files.deleteIfExists(file.getFullFilePath());
        } catch (IOException e) {
            log.warn("Failed to delete physical file: {}", file.getFullFilePath(), e);
        }

        // Delete database record
        additionalFileRepository.delete(file);
    }

    public ResponseEntity<Resource> downloadAdditionalFile(Long fileId) throws IOException {
        Optional<BookAdditionalFileEntity> fileOpt = additionalFileRepository.findById(fileId);
        if (fileOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        BookAdditionalFileEntity file = fileOpt.get();
        Path filePath = file.getFullFilePath();

        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new UrlResource(filePath.toUri());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFileName() + "\"")
                .body(resource);
    }
}
