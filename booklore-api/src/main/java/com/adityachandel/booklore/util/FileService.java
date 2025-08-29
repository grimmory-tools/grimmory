package com.adityachandel.booklore.util;

import com.adityachandel.booklore.config.AppProperties;
import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.BookAdditionalFileEntity;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.repository.BookAdditionalFileRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.mapper.BookMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
@Service
public class FileService {

    private final AppProperties appProperties;

    // @formatter:off
    private static final String IMAGES_DIR          = "images";
    private static final String THUMBNAIL_FILENAME  = "thumbnail.jpg";
    private static final String COVER_FILENAME      = "cover.jpg";
    private static final String JPEG_MIME_TYPE      = "image/jpeg";
    private static final String PNG_MIME_TYPE       = "image/png";
    private static final long   MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;
    private static final int    THUMBNAIL_WIDTH     = 250;
    private static final int    THUMBNAIL_HEIGHT    = 350;
    private static final String IMAGE_FORMAT        = "JPEG";
    // @formatter:on

    public void createThumbnailFromFile(long bookId, MultipartFile file) {
        try {
            validateCoverFile(file);
            BufferedImage originalImage = ImageIO.read(file.getInputStream());
            if (originalImage == null) {
                throw ApiError.IMAGE_NOT_FOUND.createException();
            }
            boolean success = saveCoverImages(originalImage, bookId);
            if (!success) {
                throw ApiError.FILE_READ_ERROR.createException("Failed to save cover images");
            }
            log.info("Cover images created and saved for book ID: {}", bookId);
        } catch (Exception e) {
            log.error("An error occurred while creating the thumbnail: {}", e.getMessage(), e);
            throw ApiError.FILE_READ_ERROR.createException(e.getMessage());
        }
    }

    public boolean saveCoverImages(BufferedImage coverImage, long bookId) throws IOException {
        String folderPath = getImagesFolder(bookId);
        File folder = new File(folderPath);
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IOException("Failed to create directory: " + folder.getAbsolutePath());
        }
        File originalFile = new File(folder, COVER_FILENAME);
        boolean originalSaved = ImageIO.write(coverImage, IMAGE_FORMAT, originalFile);
        BufferedImage thumb = resizeImage(coverImage, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
        File thumbnailFile = new File(folder, THUMBNAIL_FILENAME);
        boolean thumbnailSaved = ImageIO.write(thumb, IMAGE_FORMAT, thumbnailFile);
        return originalSaved && thumbnailSaved;
    }

    public BufferedImage resizeImage(BufferedImage originalImage, int width, int height) {
        Image tmp = originalImage.getScaledInstance(width, height, Image.SCALE_SMOOTH);
        BufferedImage resizedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resizedImage.createGraphics();
        g2d.drawImage(tmp, 0, 0, null);
        g2d.dispose();
        return resizedImage;
    }

    public void saveImage(byte[] imageData, String filePath) throws IOException {
        BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(imageData));
        File outputFile = new File(filePath);
        File parentDir = outputFile.getParentFile();
        if (!parentDir.exists() && !parentDir.mkdirs()) {
            throw new IOException("Failed to create directory: " + parentDir);
        }
        ImageIO.write(originalImage, IMAGE_FORMAT, outputFile);
        log.info("Image saved successfully to: {}", filePath);
    }

    public void setBookCoverPath(BookMetadataEntity bookMetadataEntity) {
        bookMetadataEntity.setCoverUpdatedOn(Instant.now());
    }

    public void deleteBookCovers(Set<Long> bookIds) {
        for (Long bookId : bookIds) {
            String bookCoverFolder = getImagesFolder(bookId);
            Path folderPath = Paths.get(bookCoverFolder);
            try {
                if (Files.exists(folderPath) && Files.isDirectory(folderPath)) {
                    try (Stream<Path> walk = Files.walk(folderPath)) {
                        walk.sorted(Comparator.reverseOrder())
                                .forEach(path -> {
                                    try {
                                        Files.delete(path);
                                    } catch (IOException e) {
                                        log.error("Failed to delete file: {} - {}", path, e.getMessage());
                                    }
                                });
                    }
                }
            } catch (IOException e) {
                log.error("Error processing folder: {} - {}", folderPath, e.getMessage());
            }
        }
        log.info("Deleted {} book covers", bookIds.size());
    }

    @Transactional
    public Optional<Book> checkForDuplicateAndUpdateMetadataIfNeeded(LibraryFile libraryFile, String hash, BookRepository bookRepository, BookAdditionalFileRepository bookAdditionalFileRepository, BookMapper bookMapper) {
        if (StringUtils.isBlank(hash)) {
            log.warn("Skipping file due to missing hash: {}", libraryFile.getFullPath());
            return Optional.empty();
        }
        Optional<BookEntity> existingByHash = bookRepository.findByCurrentHash(hash);
        if (existingByHash.isPresent()) {
            BookEntity book = existingByHash.get();
            String fileName = libraryFile.getFullPath().getFileName().toString();
            if (!book.getFileName().equals(fileName)) {
                book.setFileName(fileName);
            }
            if (!Objects.equals(book.getLibraryPath().getId(), libraryFile.getLibraryPathEntity().getId())) {
                book.setLibraryPath(libraryFile.getLibraryPathEntity());
                book.setFileSubPath(libraryFile.getFileSubPath());
            }
            return Optional.of(bookMapper.toBook(book));
        }
        Optional<BookAdditionalFileEntity> existingAdditionalFile = bookAdditionalFileRepository.findByAltFormatCurrentHash(hash);
        if (existingAdditionalFile.isPresent()) {
            BookAdditionalFileEntity additionalFile = existingAdditionalFile.get();
            BookEntity book = additionalFile.getBook();

            // Additional file might have a different name or path, so there is no need
            // to update the file name or library path here
            return Optional.of(bookMapper.toBook(book));
        }

        return Optional.empty();
    }

    public static String truncate(String input, int maxLength) {
        return input == null ? null : (input.length() <= maxLength ? input : input.substring(0, maxLength));
    }

    private void validateCoverFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        String contentType = file.getContentType();
        if (!(JPEG_MIME_TYPE.equalsIgnoreCase(contentType) || PNG_MIME_TYPE.equalsIgnoreCase(contentType))) {
            throw new IllegalArgumentException("Only JPEG and PNG files are allowed");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("File size must not exceed 5 MB");
        }
    }


    public String getImagesFolder(long bookId) {
        return appProperties.getPathConfig() + "/" + IMAGES_DIR + "/" + bookId + "/";
    }

    public String getThumbnailFile(long bookId) {
        return appProperties.getPathConfig() + "/" + IMAGES_DIR + "/" + bookId + "/" + THUMBNAIL_FILENAME;
    }

    public String getCoverFile(long bookId) {
        return appProperties.getPathConfig() + "/" + IMAGES_DIR + "/" + bookId + "/" + COVER_FILENAME;
    }

    public String getMetadataBackupPath() {
        return appProperties.getPathConfig() + "/metadata_backup/";
    }

    public String getBookMetadataBackupPath(long bookId) {
        return appProperties.getPathConfig() + "/metadata_backup/" + bookId + "/";
    }

    public String getCbxCachePath() {
        return appProperties.getPathConfig() + "/cbx_cache";
    }

    public String getPdfCachePath() {
        return appProperties.getPathConfig() + "/pdf_cache";
    }

    public String getTempBookdropCoverImagePath(long bookdropFileId) {
        return Paths.get(appProperties.getPathConfig(), "bookdrop_temp", bookdropFileId + ".jpg").toString();
    }

    public void createThumbnailFromUrl(long bookId, String imageUrl) {
        try {
            BufferedImage originalImage = downloadImageFromUrl(imageUrl);
            boolean success = saveCoverImages(originalImage, bookId);
            if (!success) {
                throw ApiError.FILE_READ_ERROR.createException("Failed to save cover images");
            }
            log.info("Cover images created and saved from URL for book ID: {}", bookId);
        } catch (Exception e) {
            log.error("An error occurred while creating thumbnail from URL: {}", e.getMessage(), e);
            throw ApiError.FILE_READ_ERROR.createException(e.getMessage());
        }
    }

    private BufferedImage downloadImageFromUrl(String imageUrl) throws IOException {
        try {
            URL url = new URL(imageUrl);
            BufferedImage image = ImageIO.read(url);
            if (image == null) {
                throw new IOException("Unable to read image from URL: " + imageUrl);
            }
            return image;
        } catch (Exception e) {
            log.error("Failed to download image from URL: {} - {}", imageUrl, e.getMessage());
            throw new IOException("Failed to download image from URL: " + imageUrl, e);
        }
    }
}