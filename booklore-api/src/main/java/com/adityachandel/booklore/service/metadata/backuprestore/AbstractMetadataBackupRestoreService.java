package com.adityachandel.booklore.service.metadata.backuprestore;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.util.FileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractMetadataBackupRestoreService implements MetadataBackupRestore {

    protected final FileService fileService;
    protected final ObjectMapper objectMapper;
    protected final BookRepository bookRepository;
    protected final BookMetadataRestorer bookMetadataRestorer;

    protected Path resolveBackupDir(BookEntity bookEntity) {
        return Path.of(fileService.getMetadataBackupPath(), String.valueOf(bookEntity.getId()));
    }

    protected void writeMetadata(BookEntity bookEntity, BookMetadata metadata, Path backupDir) throws IOException {
        Path metadataFile = backupDir.resolve("metadata.json");
        Path filenameCheckFile = backupDir.resolve("original-filename.txt");
        String json = objectMapper.writer().writeValueAsString(metadata);
        Files.writeString(metadataFile, json, StandardOpenOption.CREATE_NEW);
        Files.writeString(filenameCheckFile, bookEntity.getFileName(), StandardOpenOption.CREATE_NEW);
    }

    protected void validateBackupIntegrity(BookEntity bookEntity, Path metadataFile, Path filenameCheckFile) throws IOException {
        if (Files.notExists(metadataFile)) {
            throw ApiError.INTERNAL_SERVER_ERROR.createException("Metadata backup file not found.");
        }
        if (Files.notExists(filenameCheckFile)) {
            throw ApiError.INTERNAL_SERVER_ERROR.createException("Filename check file is missing.");
        }
        String backedUpFilename = Files.readString(filenameCheckFile).trim();
        String currentFilename = bookEntity.getFileName().trim();
        if (!currentFilename.equals(backedUpFilename)) {
            throw ApiError.INTERNAL_SERVER_ERROR.createException("The backup is for a different file.");
        }
    }

    protected BookMetadata readMetadata(Path metadataFile, Long bookId) {
        try {
            ObjectReader reader = objectMapper.readerFor(BookMetadata.class);
            return reader.readValue(metadataFile.toFile());
        } catch (IOException e) {
            log.error("Failed to read metadata backup for book ID {}: {}", bookId, e.getMessage(), e);
            throw ApiError.INTERNAL_SERVER_ERROR.createException("Failed to read metadata backup file.");
        }
    }

    @Override
    public BookMetadata getBackedUpMetadata(Long bookId) {
        BookEntity bookEntity = bookRepository.findById(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        Path metadataFile = resolveBackupDir(bookEntity).resolve("metadata.json");
        if (Files.notExists(metadataFile)) {
            throw ApiError.INTERNAL_SERVER_ERROR.createException("Metadata backup file not found.");
        }
        return readMetadata(metadataFile, bookId);
    }
}
