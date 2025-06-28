package com.adityachandel.booklore.service.metadata.backuprestore;

import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.metadata.extractor.FileMetadataExtractor;
import com.adityachandel.booklore.util.FileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Service
public class PdfMetadataBackupRestoreService extends AbstractMetadataBackupRestoreService {

    private final FileMetadataExtractor pdfMetadataExtractor;

    public PdfMetadataBackupRestoreService(FileService fileService, ObjectMapper objectMapper, BookRepository bookRepository, BookMetadataRestorer bookMetadataRestorer, FileMetadataExtractor pdfMetadataExtractor) {
        super(fileService, objectMapper, bookRepository, bookMetadataRestorer);
        this.pdfMetadataExtractor = pdfMetadataExtractor;
    }

    @Override
    public void backupEmbeddedMetadataIfNotExists(BookEntity bookEntity, boolean backupCover) {
        Path backupDir = resolveBackupDir(bookEntity);
        Path metadataFile = backupDir.resolve("metadata.json");
        if (Files.exists(metadataFile)) return;

        try {
            Files.createDirectories(backupDir);
            BookMetadata metadata = pdfMetadataExtractor.extractMetadata(new File(bookEntity.getFullFilePath().toUri()));
            writeMetadata(bookEntity, metadata, backupDir);
            log.info("Created PDF metadata backup for book ID {}", bookEntity.getId());
        } catch (Exception e) {
            log.warn("Failed to backup metadata for PDF book ID {}", bookEntity.getId(), e);
        }
    }

    @Override
    public void restoreEmbeddedMetadata(BookEntity bookEntity) throws IOException {
        Path backupDir = resolveBackupDir(bookEntity);
        Path metadataFile = backupDir.resolve("metadata.json");
        Path filenameCheckFile = backupDir.resolve("original-filename.txt");

        validateBackupIntegrity(bookEntity, metadataFile, filenameCheckFile);
        BookMetadata backupMetadata = readMetadata(metadataFile, bookEntity.getId());
        bookMetadataRestorer.restoreMetadata(bookEntity, backupMetadata, null);
        log.info("Restored PDF metadata for book ID {}", bookEntity.getId());
    }

    @Override
    public Resource getBackupCover(long bookId) {
        throw new UnsupportedOperationException("Cover backup not supported for PDF files.");
    }

    @Override
    public BookFileType getSupportedBookType() {
        return BookFileType.PDF;
    }
}
