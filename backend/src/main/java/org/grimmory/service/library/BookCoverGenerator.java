package org.grimmory.service.library;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grimmory.model.dto.settings.LibraryFile;
import org.grimmory.model.entity.BookEntity;
import org.grimmory.model.enums.BookFileType;
import org.grimmory.repository.BookRepository;
import org.grimmory.service.metadata.extractor.AudiobookMetadataExtractor;
import org.grimmory.service.metadata.extractor.MetadataExtractorFactory;
import org.grimmory.util.BookCoverUtils;
import org.grimmory.util.FileService;
import org.grimmory.util.FileUtils;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Handles cover image generation from book files.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookCoverGenerator {

    private final BookRepository bookRepository;
    private final FileService fileService;
    private final MetadataExtractorFactory metadataExtractorFactory;
    private final AudiobookMetadataExtractor audiobookMetadataExtractor;

    /**
     * Generates a cover image from an additional file and attaches it to the book.
     */
    public void generateCoverFromAdditionalFile(BookEntity bookEntity, LibraryFile additionalFile) {
        BookFileType additionalType = additionalFile.getBookFileType();
        boolean additionalIsAudiobook = additionalType == BookFileType.AUDIOBOOK;

        if (!bookEntity.hasFiles()) {
            try {
                if (additionalIsAudiobook) {
                    generateAudiobookCoverFromFile(bookEntity, additionalFile);
                } else {
                    generateEbookCoverFromFile(bookEntity, additionalFile);
                }
            } catch (Exception e) {
                log.warn("Failed to generate cover from additional file {}: {}", additionalFile.getFileName(), e.getMessage());
            }
            return;
        }

        BookFileType primaryType = bookEntity.getPrimaryBookFile().getBookType();
        boolean primaryIsAudiobook = primaryType == BookFileType.AUDIOBOOK;

        if (primaryIsAudiobook == additionalIsAudiobook) {
            return;
        }

        try {
            if (additionalIsAudiobook) {
                generateAudiobookCoverFromFile(bookEntity, additionalFile);
            } else {
                generateEbookCoverFromFile(bookEntity, additionalFile);
            }
        } catch (Exception e) {
            log.warn("Failed to generate cover from additional file {}: {}", additionalFile.getFileName(), e.getMessage());
        }
    }

    private void generateAudiobookCoverFromFile(BookEntity bookEntity, LibraryFile audioFile) {
        try {
            File file = getFileForCoverExtraction(audioFile);
            if (file == null || !file.exists()) {
                log.debug("Audio file not found for cover extraction: {}", audioFile.getFileName());
                return;
            }

            byte[] coverData = audiobookMetadataExtractor.extractCover(file);
            if (coverData == null) {
                log.debug("No cover image found in audiobook '{}'", audioFile.getFileName());
                return;
            }

            try (ByteArrayInputStream bais = new ByteArrayInputStream(coverData)) {
                BufferedImage originalImage = FileService.readImage(bais);
                if (originalImage == null) {
                    log.warn("Failed to decode cover image for audiobook '{}'", audioFile.getFileName());
                    return;
                }
                boolean saved = fileService.saveAudiobookCoverImages(originalImage, bookEntity.getId());
                originalImage.flush();

                if (saved) {
                    if (bookEntity.getMetadata() != null) {
                        bookEntity.getMetadata().setAudiobookCoverUpdatedOn(Instant.now());
                    } else {
                        log.debug("Skipping audiobook cover update on metadata for book ID {}: metadata is null", bookEntity.getId());
                    }
                    bookEntity.setAudiobookCoverHash(BookCoverUtils.generateCoverHash());
                    bookRepository.save(bookEntity);
                    log.info("Generated audiobook cover from additional file: {}", audioFile.getFileName());
                }
            }
        } catch (Exception e) {
            log.warn("Error generating audiobook cover from {}: {}", audioFile.getFileName(), e.getMessage());
        }
    }

    private void generateEbookCoverFromFile(BookEntity bookEntity, LibraryFile ebookFile) {
        try {
            File file = ebookFile.getFullPath().toFile();
            if (!file.exists()) {
                log.debug("Ebook file not found for cover extraction: {}", ebookFile.getFileName());
                return;
            }

            var extractor = metadataExtractorFactory.getExtractor(ebookFile.getBookFileType());
            if (extractor == null) {
                log.debug("No extractor available for file type: {}", ebookFile.getBookFileType());
                return;
            }

            byte[] coverData = extractor.extractCover(file);
            if (coverData == null) {
                log.debug("No cover image found in ebook '{}'", ebookFile.getFileName());
                return;
            }

            try (ByteArrayInputStream bais = new ByteArrayInputStream(coverData)) {
                BufferedImage originalImage = FileService.readImage(bais);
                if (originalImage == null) {
                    log.warn("Failed to decode cover image for ebook '{}'", ebookFile.getFileName());
                    return;
                }
                boolean saved = fileService.saveCoverImages(originalImage, bookEntity.getId());
                originalImage.flush();

                if (saved) {
                    if (bookEntity.getMetadata() != null) {
                        FileService.setBookCoverPath(bookEntity.getMetadata());
                    } else {
                        log.debug("Skipping ebook cover path update for book ID {}: metadata is null", bookEntity.getId());
                    }
                    bookEntity.setBookCoverHash(BookCoverUtils.generateCoverHash());
                    bookRepository.save(bookEntity);
                    log.info("Generated ebook cover from additional file: {}", ebookFile.getFileName());
                }
            }
        } catch (Exception e) {
            log.warn("Error generating ebook cover from {}: {}", ebookFile.getFileName(), e.getMessage());
        }
    }

    private File getFileForCoverExtraction(LibraryFile libraryFile) {
        if (libraryFile.isFolderBased()) {
            Path folderPath = libraryFile.getFullPath();
            return FileUtils.getFirstAudioFileInFolder(folderPath)
                    .map(Path::toFile)
                    .orElse(null);
        } else {
            return libraryFile.getFullPath().toFile();
        }
    }
}
