package com.adityachandel.booklore.service.migration;

import com.adityachandel.booklore.config.AppProperties;
import com.adityachandel.booklore.model.entity.AppMigrationEntity;
import com.adityachandel.booklore.model.entity.AppSettingEntity;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.repository.AppMigrationRepository;
import com.adityachandel.booklore.repository.AppSettingsRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.book.BookQueryService;
import com.adityachandel.booklore.service.file.FileFingerprint;
import com.adityachandel.booklore.service.metadata.MetadataMatchService;
import com.adityachandel.booklore.util.BookUtils;
import com.adityachandel.booklore.util.FileService;
import com.adityachandel.booklore.util.FileUtils;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Slf4j
@AllArgsConstructor
@Service
public class AppMigrationService {

    private static final String INSTALLATION_ID_KEY = "installation_id";

    private AppMigrationRepository migrationRepository;
    private AppSettingsRepository appSettingsRepository;
    private BookRepository bookRepository;
    private BookQueryService bookQueryService;
    private MetadataMatchService metadataMatchService;
    private AppProperties appProperties;
    private FileService fileService;

    @Transactional
    public void generateInstallationId() {
        if (migrationRepository.existsById("generateInstallationId")) return;

        AppSettingEntity setting = appSettingsRepository.findByName(INSTALLATION_ID_KEY);

        if (setting == null) {
            LocalDateTime now = LocalDateTime.now();
            String uuid = UUID.randomUUID().toString();
            String combined = now + "_" + uuid;

            String installationId = hashToSha256(combined).substring(0, 24);

            setting = new AppSettingEntity();
            setting.setName(INSTALLATION_ID_KEY);
            setting.setVal(installationId);
            appSettingsRepository.save(setting);

            log.info("Generated new installation ID");
        }

        migrationRepository.save(new AppMigrationEntity("generateInstallationId", LocalDateTime.now(), "Generate unique installation ID using timestamp and UUID"));
    }

    private String hashToSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    @Transactional
    public void populateSearchTextOnce() {
        if (migrationRepository.existsById("populateSearchText")) return;

        int batchSize = 1000;
        int processedCount = 0;
        int offset = 0;

        while (true) {
            List<BookEntity> bookBatch = bookRepository.findBooksForMigrationBatch(offset, batchSize);
            if (bookBatch.isEmpty()) break;

            List<Long> bookIds = bookBatch.stream().map(BookEntity::getId).toList();
            List<BookEntity> books = bookRepository.findBooksWithMetadataAndAuthors(bookIds);

            for (BookEntity book : books) {
                BookMetadataEntity m = book.getMetadata();
                if (m != null) {
                    try {
                        m.setSearchText(BookUtils.buildSearchText(m));
                    } catch (Exception ex) {
                        log.warn("Failed to build search text for book {}: {}", book.getId(), ex.getMessage());
                    }
                }
            }

            bookRepository.saveAll(books);
            processedCount += books.size();
            offset += batchSize;

            log.info("Migration progress: {} books processed", processedCount);

            if (bookBatch.size() < batchSize) break;
        }

        log.info("Migration 'populateSearchText' completed. Total books processed: {}", processedCount);
        migrationRepository.save(new AppMigrationEntity(
                "populateSearchText",
                LocalDateTime.now(),
                "Populate search_text column for all books"
        ));
    }

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
                try (var stream = Files.walk(thumbsDir)) {
                    stream.filter(Files::isRegularFile)
                            .forEach(path -> {
                                BufferedImage originalImage = null;
                                BufferedImage resized = null;
                                try {
                                    // Load original image
                                    originalImage = ImageIO.read(path.toFile());
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
                                    resized = FileService.resizeImage(originalImage, 250, 350);
                                    Path thumbnailFile = bookDir.resolve("thumbnail.jpg");
                                    ImageIO.write(resized, "jpg", thumbnailFile.toFile());

                                    log.debug("Processed book {}: cover={} thumbnail={}", bookId, coverFile, thumbnailFile);
                                } catch (IOException e) {
                                    log.error("Error processing file {}", path, e);
                                    throw new UncheckedIOException(e);
                                } finally {
                                    if (originalImage != null) {
                                        originalImage.flush();
                                    }
                                    if (resized != null) {
                                        resized.flush();
                                    }
                                }
                            });
                }

                // Delete old thumbs directory
                log.info("Deleting old thumbs directory: {}", thumbsDir);
                try (var stream = Files.walk(thumbsDir)) {
                    stream.sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                }
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

    @Transactional
    public void moveIconsToDataFolder() {
        if (migrationRepository.existsById("moveIconsToDataFolder")) return;

        long start = System.nanoTime();
        log.info("Starting migration: moveIconsToDataFolder");

        try {
            String targetFolder = fileService.getIconsSvgFolder();
            Path targetDir = Paths.get(targetFolder);
            Files.createDirectories(targetDir);

            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:static/images/icons/svg/*.svg");

            int copiedCount = 0;
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null) continue;

                Path targetFile = targetDir.resolve(filename);

                try (var inputStream = resource.getInputStream()) {
                    Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
                    copiedCount++;
                    log.debug("Copied icon: {} to {}", filename, targetFile);
                } catch (IOException e) {
                    log.error("Failed to copy icon: {}", filename, e);
                }
            }

            log.info("Copied {} SVG icons from resources to data folder", copiedCount);

            migrationRepository.save(new AppMigrationEntity(
                    "moveIconsToDataFolder",
                    LocalDateTime.now(),
                    "Move SVG icons from resources/static/images/icons/svg to data/icons/svg"
            ));

            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            log.info("Completed migration: moveIconsToDataFolder in {} ms", elapsedMs);
        } catch (IOException e) {
            log.error("Error during migration moveIconsToDataFolder", e);
            throw new UncheckedIOException(e);
        }
    }

}
