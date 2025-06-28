package com.adityachandel.booklore.service.metadata.backuprestore;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.metadata.extractor.FileMetadataExtractor;
import com.adityachandel.booklore.util.FileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.documentnode.epub4j.domain.Book;
import io.documentnode.epub4j.domain.Resource;
import io.documentnode.epub4j.epub.EpubReader;
import io.documentnode.epub4j.epub.EpubWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

@Slf4j
@Service
public class EpubMetadataBackupRestoreService extends AbstractMetadataBackupRestoreService {

    private final FileMetadataExtractor epubMetadataExtractor;

    public EpubMetadataBackupRestoreService(FileService fileService, ObjectMapper objectMapper, BookRepository bookRepository, BookMetadataRestorer bookMetadataRestorer, FileMetadataExtractor epubMetadataExtractor) {
        super(fileService, objectMapper, bookRepository, bookMetadataRestorer);
        this.epubMetadataExtractor = epubMetadataExtractor;
    }

    @Override
    public void backupEmbeddedMetadataIfNotExists(BookEntity bookEntity, boolean backupCover) {
        File bookFile = new File(bookEntity.getFullFilePath().toUri());
        Path backupDir = resolveBackupDir(bookEntity);
        Path metadataFile = backupDir.resolve("metadata.json");
        Path coverFile = backupDir.resolve("cover.jpg");

        if (Files.exists(metadataFile)) return;

        try {
            Files.createDirectories(backupDir);
            BookMetadata metadata = epubMetadataExtractor.extractMetadata(bookFile);
            writeMetadata(bookEntity, metadata, backupDir);
            if (backupCover) {
                try (FileInputStream fis = new FileInputStream(bookFile)) {
                    Book epubBook = new EpubReader().readEpub(fis);
                    Resource coverImage = epubBook.getCoverImage();
                    if (coverImage != null) {
                        Files.write(coverFile, coverImage.getData(), StandardOpenOption.CREATE_NEW);
                        log.info("Backup cover image saved for book ID {} at {}", bookEntity.getId(), coverFile);
                    } else {
                        log.warn("No cover image found in EPUB for book ID {}", bookEntity.getId());
                    }
                }
            }
            log.info("Created EPUB metadata backup for book ID {} at {}", bookEntity.getId(), backupDir);
        } catch (Exception e) {
            log.warn("Failed to backup EPUB metadata for book ID {}", bookEntity.getId(), e);
        }
    }

    @Override
    public void restoreEmbeddedMetadata(BookEntity bookEntity) throws IOException {
        Path backupDir = resolveBackupDir(bookEntity);
        Path metadataFile = backupDir.resolve("metadata.json");
        Path coverFile = backupDir.resolve("cover.jpg");
        Path filenameCheckFile = backupDir.resolve("original-filename.txt");

        validateBackupIntegrity(bookEntity, metadataFile, filenameCheckFile);

        BookMetadata backupMetadata = readMetadata(metadataFile, bookEntity.getId());
        bookMetadataRestorer.restoreMetadata(bookEntity, backupMetadata, coverFile.toString());

        updateThumbnailIfNeeded(bookEntity.getId(), coverFile, bookEntity.getMetadata());

        log.info("Successfully restored embedded metadata for EPUB book ID {}", bookEntity.getId());
    }

    @Override
    public org.springframework.core.io.Resource getBackupCover(long bookId) {
        BookEntity bookEntity = bookRepository.findById(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        Path coverPath = resolveBackupDir(bookEntity).resolve("cover.jpg");

        if (Files.notExists(coverPath)) {
            log.warn("No cover image found in backup for book ID {} at {}", bookId, coverPath);
            throw ApiError.INTERNAL_SERVER_ERROR.createException("Backup cover image not found.");
        }

        return new FileSystemResource(coverPath);
    }

    @Override
    public BookFileType getSupportedBookType() {
        return BookFileType.EPUB;
    }

    private void updateThumbnailIfNeeded(long bookId, Path coverPath, BookMetadataEntity metadata) {
        try {
            String thumbnailPath = fileService.createThumbnail(bookId, coverPath.toString());
            metadata.setThumbnail(thumbnailPath);
            metadata.setCoverUpdatedOn(Instant.now());
        } catch (IOException e) {
            log.error("Failed to update thumbnail for book ID {}: {}", bookId, e.getMessage(), e);
        }
    }
}