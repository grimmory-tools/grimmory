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
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
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
    private final BookRepository bookRepository;
    private final BookAdditionalFileRepository bookAdditionalFileRepository;
    private final BookMapper bookMapper;
    private final RestTemplate restTemplate;

    // @formatter:off
    private static final String IMAGES_DIR          = "images";
    private static final String BACKGROUNDS_DIR     = "backgrounds";
    private static final String THUMBNAIL_FILENAME  = "thumbnail.jpg";
    private static final String COVER_FILENAME      = "cover.jpg";
    private static final String JPEG_MIME_TYPE      = "image/jpeg";
    private static final String PNG_MIME_TYPE       = "image/png";
    private static final long   MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;
    private static final int    THUMBNAIL_WIDTH     = 250;
    private static final int    THUMBNAIL_HEIGHT    = 350;
    private static final String IMAGE_FORMAT        = "JPEG";
    // @formatter:on

    // ========================================
    // PATH UTILITIES
    // ========================================

    public String getImagesFolder(long bookId) {
        return Paths.get(appProperties.getPathConfig(), IMAGES_DIR, String.valueOf(bookId)).toString();
    }

    public String getThumbnailFile(long bookId) {
        return Paths.get(appProperties.getPathConfig(), IMAGES_DIR, String.valueOf(bookId), THUMBNAIL_FILENAME).toString();
    }

    public String getCoverFile(long bookId) {
        return Paths.get(appProperties.getPathConfig(), IMAGES_DIR, String.valueOf(bookId), COVER_FILENAME).toString();
    }

    public String getBackgroundsFolder(Long userId) {
        if (userId != null) {
            return Paths.get(appProperties.getPathConfig(), BACKGROUNDS_DIR, "user-" + userId).toString();
        }
        return Paths.get(appProperties.getPathConfig(), BACKGROUNDS_DIR).toString();
    }

    public String getBackgroundsFolder() {
        return getBackgroundsFolder(null);
    }

    public static String getBackgroundUrl(String filename, Long userId) {
        if (userId != null) {
            return Paths.get("/", BACKGROUNDS_DIR, "user-" + userId, filename).toString().replace("\\", "/");
        }
        return Paths.get("/", BACKGROUNDS_DIR, filename).toString().replace("\\", "/");
    }

    public String getBookMetadataBackupPath(long bookId) {
        return Paths.get(appProperties.getPathConfig(), "metadata_backup", String.valueOf(bookId)).toString();
    }

    public String getCbxCachePath() {
        return Paths.get(appProperties.getPathConfig(), "cbx_cache").toString();
    }

    public String getPdfCachePath() {
        return Paths.get(appProperties.getPathConfig(), "pdf_cache").toString();
    }

    public String getTempBookdropCoverImagePath(long bookdropFileId) {
        return Paths.get(appProperties.getPathConfig(), "bookdrop_temp", bookdropFileId + ".jpg").toString();
    }

    public String getToolsKepubifyPath() {
        return Paths.get(appProperties.getPathConfig(), "tools", "kepubify").toString();
    }

    // ========================================
    // VALIDATION
    // ========================================

    private static void validateCoverFile(MultipartFile file) {
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

    // ========================================
    // IMAGE OPERATIONS
    // ========================================

    public static BufferedImage resizeImage(BufferedImage originalImage, int width, int height) {
        Image tmp = originalImage.getScaledInstance(width, height, Image.SCALE_SMOOTH);
        BufferedImage resizedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resizedImage.createGraphics();
        g2d.drawImage(tmp, 0, 0, null);
        g2d.dispose();
        return resizedImage;
    }

    public static void saveImage(byte[] imageData, String filePath) throws IOException {
        BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(imageData));
        File outputFile = new File(filePath);
        File parentDir = outputFile.getParentFile();
        if (!parentDir.exists() && !parentDir.mkdirs()) {
            throw new IOException("Failed to create directory: " + parentDir);
        }
        ImageIO.write(originalImage, IMAGE_FORMAT, outputFile);
        log.info("Image saved successfully to: {}", filePath);
    }

    public static BufferedImage downloadImageFromUrl(String imageUrl) throws IOException {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, "BookLore/1.0 (Metadata Fetcher)");
            headers.set(HttpHeaders.ACCEPT, "image/*");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<byte[]> response = restTemplate.exchange(
                    imageUrl,
                    HttpMethod.GET,
                    entity,
                    byte[].class
            );

            // Validate and convert
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                try (ByteArrayInputStream inputStream = new ByteArrayInputStream(response.getBody())) {
                    BufferedImage image = ImageIO.read(inputStream);
                    if (image == null) {
                        throw new IOException("Downloaded content is not a supported image format.");
                    }
                    return image;
                }
            } else {
                throw new IOException("Failed to download image. HTTP Status: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Failed to download image from URL: {} - {}", imageUrl, e.getMessage());
            throw new IOException("Failed to download image from URL: " + imageUrl, e);
        }
    }

    // ========================================
    // COVER OPERATIONS
    // ========================================

    public void createThumbnailFromFile(long bookId, MultipartFile file) {
        try {
            validateCoverFile(file);
            BufferedImage originalImage;
            try (InputStream inputStream = file.getInputStream()) {
                originalImage = ImageIO.read(inputStream);
            }
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

    public boolean saveCoverImages(BufferedImage coverImage, long bookId) throws IOException {
        String folderPath = getImagesFolder(bookId);
        File folder = new File(folderPath);
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IOException("Failed to create directory: " + folder.getAbsolutePath());
        }
        BufferedImage rgbImage = new BufferedImage(
                coverImage.getWidth(),
                coverImage.getHeight(),
                BufferedImage.TYPE_INT_RGB
        );
        Graphics2D g = rgbImage.createGraphics();
        g.drawImage(coverImage, 0, 0, Color.WHITE, null);
        g.dispose();

        File originalFile = new File(folder, COVER_FILENAME);
        boolean originalSaved = ImageIO.write(rgbImage, IMAGE_FORMAT, originalFile);

        BufferedImage thumb = resizeImage(rgbImage, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
        File thumbnailFile = new File(folder, THUMBNAIL_FILENAME);
        boolean thumbnailSaved = ImageIO.write(thumb, IMAGE_FORMAT, thumbnailFile);

        return originalSaved && thumbnailSaved;
    }

    public static void setBookCoverPath(BookMetadataEntity bookMetadataEntity) {
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

    // ========================================
    // BACKGROUND OPERATIONS
    // ========================================

    public void saveBackgroundImage(BufferedImage image, String filename, Long userId) throws IOException {
        String backgroundsFolder = getBackgroundsFolder(userId);
        File folder = new File(backgroundsFolder);
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IOException("Failed to create backgrounds directory: " + folder.getAbsolutePath());
        }

        File outputFile = new File(folder, filename);
        boolean saved = ImageIO.write(image, IMAGE_FORMAT, outputFile);
        if (!saved) {
            throw new IOException("Failed to save background image: " + filename);
        }

        log.info("Background image saved successfully for user {}: {}", userId, filename);
    }

    public void deleteBackgroundFile(String filename, Long userId) {
        try {
            String backgroundsFolder = getBackgroundsFolder(userId);
            File file = new File(backgroundsFolder, filename);
            if (file.exists() && file.isFile()) {
                boolean deleted = file.delete();
                if (deleted) {
                    if (userId != null) {
                        deleteEmptyUserBackgroundFolder(userId);
                    }
                } else {
                    log.warn("Failed to delete background file for user {}: {}", userId, filename);
                }
            }
        } catch (Exception e) {
            log.warn("Error deleting background file {} for user {}: {}", filename, userId, e.getMessage());
        }
    }

    private void deleteEmptyUserBackgroundFolder(Long userId) {
        try {
            String userBackgroundsFolder = getBackgroundsFolder(userId);
            File folder = new File(userBackgroundsFolder);

            if (folder.exists() && folder.isDirectory()) {
                File[] files = folder.listFiles();
                if (files != null && files.length == 0) {
                    boolean deleted = folder.delete();
                    if (deleted) {
                        log.info("Deleted empty background folder for user: {}", userId);
                    } else {
                        log.warn("Failed to delete empty background folder for user: {}", userId);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error checking/deleting empty background folder for user {}: {}", userId, e.getMessage());
        }
    }

    public Resource getBackgroundResource(Long userId) {
        String[] possibleFiles = {"1.jpg", "1.jpeg", "1.png"};

        if (userId != null) {
            String userBackgroundsFolder = getBackgroundsFolder(userId);
            for (String filename : possibleFiles) {
                File customFile = new File(userBackgroundsFolder, filename);
                if (customFile.exists() && customFile.isFile()) {
                    return new FileSystemResource(customFile);
                }
            }
        }
        String globalBackgroundsFolder = getBackgroundsFolder();
        for (String filename : possibleFiles) {
            File customFile = new File(globalBackgroundsFolder, filename);
            if (customFile.exists() && customFile.isFile()) {
                return new FileSystemResource(customFile);
            }
        }
        return new ClassPathResource("static/images/background.jpg");
    }

    // ========================================
    // UTILITY METHODS
    // ========================================

    @Transactional
    public Optional<Book> checkForDuplicateAndUpdateMetadataIfNeeded(LibraryFile libraryFile, String hash) {
        if (StringUtils.isBlank(hash)) {
            log.warn("Skipping file due to missing hash: {}", libraryFile.getFullPath());
            return Optional.empty();
        }

        // First check for soft-deleted books with the same hash
        Optional<BookEntity> softDeletedBook = bookRepository.findByCurrentHashAndDeletedTrue(hash);
        if (softDeletedBook.isPresent()) {
            BookEntity book = softDeletedBook.get();
            log.info("Found soft-deleted book with same hash, undeleting: bookId={} file='{}'",
                    book.getId(), libraryFile.getFileName());

            // Undelete the book
            book.setDeleted(false);
            book.setDeletedAt(null);

            // Update file information
            book.setFileName(libraryFile.getFileName());
            book.setFileSubPath(libraryFile.getFileSubPath());
            book.setLibraryPath(libraryFile.getLibraryPathEntity());
            book.setLibrary(libraryFile.getLibraryEntity());

            return Optional.of(bookMapper.toBook(book));
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
        if (input == null) return null;
        if (maxLength <= 0) return "";
        return input.length() <= maxLength ? input : input.substring(0, maxLength);
    }
}
