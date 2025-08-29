package com.adityachandel.booklore.service.migration;

import com.adityachandel.booklore.config.AppProperties;
import com.adityachandel.booklore.model.entity.AppMigrationEntity;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.repository.AppMigrationRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.BookQueryService;
import com.adityachandel.booklore.service.FileFingerprint;
import com.adityachandel.booklore.service.metadata.MetadataMatchService;
import com.adityachandel.booklore.util.FileService;
import com.adityachandel.booklore.util.FileUtils;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Slf4j
@AllArgsConstructor
@Service
public class AppMigrationService {

    private AppMigrationRepository migrationRepository;
    private BookRepository bookRepository;
    private BookQueryService bookQueryService;
    private MetadataMatchService metadataMatchService;
    private AppProperties appProperties;
    private FileService fileService;

    @Transactional
    public void populateMissingFileSizesOnce() {
        if (migrationRepository.existsById("populateFileSizes")) {
            return;
        }

        List<BookEntity> books = bookRepository.findAllWithMetadataByFileSizeKbIsNull();
        for (BookEntity book : books) {
            Long sizeInKb = FileUtils.getFileSizeInKb(book);
            if (sizeInKb != null) {
                book.setFileSizeKb(sizeInKb);
            }
        }
        bookRepository.saveAll(books);

        log.info("Starting migration 'populateFileSizes' for {} books.", books.size());
        AppMigrationEntity migration = new AppMigrationEntity();
        migration.setKey("populateFileSizes");
        migration.setExecutedAt(LocalDateTime.now());
        migration.setDescription("Populate file size for existing books");
        migrationRepository.save(migration);
        log.info("Migration 'populateFileSizes' executed successfully.");
    }

    @Transactional
    public void populateMetadataScoresOnce() {
        if (migrationRepository.existsById("populateMetadataScores_v2")) return;

        List<BookEntity> books = bookQueryService.getAllFullBookEntities();
        for (BookEntity book : books) {
            Float score = metadataMatchService.calculateMatchScore(book);
            book.setMetadataMatchScore(score);
        }
        bookRepository.saveAll(books);

        log.info("Migration 'populateMetadataScores_v2' applied to {} books.", books.size());
        migrationRepository.save(new AppMigrationEntity("populateMetadataScores_v2", LocalDateTime.now(), "Calculate and store metadata match score for all books"));
    }

    @Transactional
    public void populateFileHashesOnce() {
        if (migrationRepository.existsById("populateFileHashesV2")) return;

        List<BookEntity> books = bookRepository.findAll();
        int updated = 0;

        for (BookEntity book : books) {
            Path path = book.getFullFilePath();
            if (path == null || !Files.exists(path)) {
                log.warn("Skipping hashing for book ID {} — file not found at path: {}", book.getId(), path);
                continue;
            }

            try {
                String hash = FileFingerprint.generateHash(path);
                if (book.getInitialHash() == null) {
                    book.setInitialHash(hash);
                }
                book.setCurrentHash(hash);
                updated++;
            } catch (Exception e) {
                log.error("Failed to compute hash for file: {}", path, e);
            }
        }

        bookRepository.saveAll(books);

        log.info("Migration 'populateFileHashesV2' applied to {} books.", updated);
        migrationRepository.save(new AppMigrationEntity(
                "populateFileHashesV2",
                LocalDateTime.now(),
                "Calculate and store initialHash and currentHash for all books"
        ));
    }

    @Transactional
    public void populateCoversAndResizeThumbnails() {
        if (migrationRepository.existsById("populateCoversAndResizeThumbnails")) return;

        long start = System.nanoTime();
        log.info("Starting migration: populateCoversAndResizeThumbnails");

        String dataFolder = appProperties.getPathConfig();
        Path thumbsDir = Paths.get(dataFolder, "thumbs");
        Path imagesDir = Paths.get(dataFolder, "images");

        try {
            if (Files.exists(thumbsDir)) {
                Files.walk(thumbsDir)
                        .filter(Files::isRegularFile)
                        .forEach(path -> {
                            try {
                                // Load original image
                                BufferedImage originalImage = ImageIO.read(path.toFile());
                                if (originalImage == null) {
                                    log.warn("Skipping non-image file: {}", path);
                                    return;
                                }

                                // Extract bookId from folder structure
                                Path relative = thumbsDir.relativize(path);       // e.g., "11/f.jpg"
                                String bookId = relative.getParent().toString();  // "11"

                                Path bookDir = imagesDir.resolve(bookId);
                                Files.createDirectories(bookDir);

                                // Copy original to cover.jpg
                                Path coverFile = bookDir.resolve("cover.jpg");
                                ImageIO.write(originalImage, "jpg", coverFile.toFile());

                                // Resize and save thumbnail.jpg
                                BufferedImage resized = fileService.resizeImage(originalImage, 250, 350);
                                Path thumbnailFile = bookDir.resolve("thumbnail.jpg");
                                ImageIO.write(resized, "jpg", thumbnailFile.toFile());

                                log.debug("Processed book {}: cover={} thumbnail={}", bookId, coverFile, thumbnailFile);
                            } catch (IOException e) {
                                log.error("Error processing file {}", path, e);
                                throw new UncheckedIOException(e);
                            }
                        });

                // Delete old thumbs directory
                log.info("Deleting old thumbs directory: {}", thumbsDir);
                Files.walk(thumbsDir)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        } catch (IOException e) {
            log.error("Error during migration populateCoversAndResizeThumbnails", e);
            throw new UncheckedIOException(e);
        }

        migrationRepository.save(new AppMigrationEntity(
                "populateCoversAndResizeThumbnails",
                LocalDateTime.now(),
                "Copy thumbnails to images/{bookId}/cover.jpg and create resized 250x350 images as thumbnail.jpg"
        ));

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        log.info("Completed migration: populateCoversAndResizeThumbnails in {} ms", elapsedMs);
    }

}
