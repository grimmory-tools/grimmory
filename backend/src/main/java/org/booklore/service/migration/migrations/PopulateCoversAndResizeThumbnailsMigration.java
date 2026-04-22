package org.booklore.service.migration.migrations;

import org.booklore.config.AppProperties;
import org.booklore.service.migration.Migration;
import org.booklore.util.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

@Slf4j
@Component
@RequiredArgsConstructor
public class PopulateCoversAndResizeThumbnailsMigration implements Migration {

    private final AppProperties appProperties;

    @Override
    public String getKey() {
        return "populateCoversAndResizeThumbnails";
    }

    @Override
    public String getDescription() {
        return "Copy thumbnails to images/{bookId}/cover.jpg and create resized 250x350 images as thumbnail.jpg";
    }

    @Override
    public void execute() {
        long start = System.nanoTime();
        log.info("Starting migration: {}", getKey());

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
            log.error("Error during migration {}", getKey(), e);
            throw new UncheckedIOException(e);
        }

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        log.info("Completed migration: {} in {} ms", getKey(), elapsedMs);
    }
}

