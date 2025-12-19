package com.adityachandel.booklore.util;

import com.adityachandel.booklore.config.AppProperties;
import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.dto.settings.CoverCroppingSettings;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.repository.BookMetadataRepository;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
@Service
public class FileService {

    private final AppProperties appProperties;
    private final RestTemplate restTemplate;
    private final AppSettingService appSettingService;
    private final BookMetadataRepository bookMetadataRepository;

    private static final double TARGET_COVER_ASPECT_RATIO = 1.5;
    private static final int SMART_CROP_COLOR_TOLERANCE = 30;
    private static final double SMART_CROP_MARGIN_PERCENT = 0.02;

    // @formatter:off
    private static final String IMAGES_DIR          = "images";
    private static final String BACKGROUNDS_DIR     = "backgrounds";
    private static final String ICONS_DIR           = "icons";
    private static final String SVG_DIR             = "svg";
    private static final String THUMBNAIL_FILENAME  = "thumbnail.jpg";
    private static final String COVER_FILENAME      = "cover.jpg";
    private static final String JPEG_MIME_TYPE      = "image/jpeg";
    private static final String PNG_MIME_TYPE       = "image/png";
    private static final long   MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;
    private static final int    THUMBNAIL_WIDTH     = 250;
    private static final int    THUMBNAIL_HEIGHT    = 350;
    private static final int    MAX_ORIGINAL_WIDTH  = 1000;
    private static final int    MAX_ORIGINAL_HEIGHT = 1500;
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
        if (contentType == null) {
            throw new IllegalArgumentException("Content type is required");
        }
        String lowerType = contentType.toLowerCase();
        if (!lowerType.startsWith(JPEG_MIME_TYPE) && !lowerType.startsWith(PNG_MIME_TYPE)) {
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
        BufferedImage originalImage = null;
        try {
            originalImage = ImageIO.read(new ByteArrayInputStream(imageData));
            if (originalImage == null) {
                throw new IOException("Invalid image data: unable to decode image");
            }
            File outputFile = new File(filePath);
            File parentDir = outputFile.getParentFile();
            if (!parentDir.exists() && !parentDir.mkdirs()) {
                throw new IOException("Failed to create directory: " + parentDir);
            }
            ImageIO.write(originalImage, IMAGE_FORMAT, outputFile);
            log.info("Image saved successfully to: {}", filePath);
        } finally {
            if (originalImage != null) {
                originalImage.flush(); // Release native resources
            }
        }
    }

    public BufferedImage downloadImageFromUrl(String imageUrl) throws IOException {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, "BookLore/1.0 (Book and Comic Metadata Fetcher; +https://github.com/booklore-app/booklore)");
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
            throw new IOException("Failed to download image from URL: " + imageUrl + " - " + e.getMessage(), e);
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
            originalImage.flush(); // Release resources after processing
            log.info("Cover images created and saved for book ID: {}", bookId);
        } catch (Exception e) {
            log.error("An error occurred while creating the thumbnail: {}", e.getMessage(), e);
            throw ApiError.FILE_READ_ERROR.createException(e.getMessage());
        }
    }

    public void createThumbnailFromBytes(long bookId, byte[] imageBytes) {
        try {
            BufferedImage originalImage;
            try (InputStream inputStream = new java.io.ByteArrayInputStream(imageBytes)) {
                originalImage = ImageIO.read(inputStream);
            }
            if (originalImage == null) {
                throw ApiError.IMAGE_NOT_FOUND.createException();
            }
            boolean success = saveCoverImages(originalImage, bookId);
            if (!success) {
                throw ApiError.FILE_READ_ERROR.createException("Failed to save cover images");
            }
            originalImage.flush();
            log.info("Cover images created and saved from bytes for book ID: {}", bookId);
        } catch (Exception e) {
            log.error("An error occurred while creating thumbnail from bytes: {}", e.getMessage(), e);
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
            originalImage.flush(); // Release resources after processing
            log.info("Cover images created and saved from URL for book ID: {}", bookId);
        } catch (Exception e) {
            log.error("An error occurred while creating thumbnail from URL: {}", e.getMessage(), e);
            throw ApiError.FILE_READ_ERROR.createException(e.getMessage());
        }
    }

    public boolean saveCoverImages(BufferedImage coverImage, long bookId) throws IOException {
        BufferedImage rgbImage = null;
        BufferedImage cropped = null;
        BufferedImage resized = null;
        BufferedImage thumb = null;
        try {
            String folderPath = getImagesFolder(bookId);
            File folder = new File(folderPath);
            if (!folder.exists() && !folder.mkdirs()) {
                throw new IOException("Failed to create directory: " + folder.getAbsolutePath());
            }

            rgbImage = new BufferedImage(
                    coverImage.getWidth(),
                    coverImage.getHeight(),
                    BufferedImage.TYPE_INT_RGB
            );
            Graphics2D g = rgbImage.createGraphics();
            g.drawImage(coverImage, 0, 0, Color.WHITE, null);
            g.dispose();
            // Note: coverImage is not flushed here - caller is responsible for its lifecycle

            cropped = applyCoverCropping(rgbImage);
            if (cropped != rgbImage) {
                rgbImage.flush();
                rgbImage = cropped;
            }

            // Resize original image if too large to prevent OOM
            double scale = Math.min(
                    (double) MAX_ORIGINAL_WIDTH / rgbImage.getWidth(),
                    (double) MAX_ORIGINAL_HEIGHT / rgbImage.getHeight()
            );
            if (scale < 1.0) {
                resized = resizeImage(rgbImage, (int) (rgbImage.getWidth() * scale), (int) (rgbImage.getHeight() * scale));
                rgbImage.flush(); // Release resources of the original large image
                rgbImage = resized;
            }

            File originalFile = new File(folder, COVER_FILENAME);
            boolean originalSaved = ImageIO.write(rgbImage, IMAGE_FORMAT, originalFile);

            thumb = resizeImage(rgbImage, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
            File thumbnailFile = new File(folder, THUMBNAIL_FILENAME);
            boolean thumbnailSaved = ImageIO.write(thumb, IMAGE_FORMAT, thumbnailFile);

            if (originalSaved && thumbnailSaved) {
                bookMetadataRepository.updateCoverTimestamp(bookId, Instant.now());
            }
            return originalSaved && thumbnailSaved;
        } finally {
            // Cleanup resources created within this method
            // Note: cropped/resized may equal rgbImage after reassignment, avoid double-flush
            if (rgbImage != null) {
                rgbImage.flush();
            }
            if (cropped != null && cropped != rgbImage) {
                cropped.flush();
            }
            if (resized != null && resized != rgbImage) {
                resized.flush();
            }
            if (thumb != null) {
                thumb.flush();
            }
        }
    }

    private BufferedImage applyCoverCropping(BufferedImage image) {
        CoverCroppingSettings settings = appSettingService.getAppSettings().getCoverCroppingSettings();
        if (settings == null) {
            return image;
        }

        int width = image.getWidth();
        int height = image.getHeight();
        double heightToWidthRatio = (double) height / width;
        double widthToHeightRatio = (double) width / height;
        double threshold = settings.getAspectRatioThreshold();
        boolean smartCrop = settings.isSmartCroppingEnabled();

        boolean isExtremelyTall = settings.isVerticalCroppingEnabled() && heightToWidthRatio > threshold;
        if (isExtremelyTall) {
            int croppedHeight = (int) (width * TARGET_COVER_ASPECT_RATIO);
            log.debug("Cropping tall image: {}x{} (ratio {}) -> {}x{}, smartCrop={}", 
                    width, height, String.format("%.2f", heightToWidthRatio), width, croppedHeight, smartCrop);
            return cropFromTop(image, width, croppedHeight, smartCrop);
        }

        boolean isExtremelyWide = settings.isHorizontalCroppingEnabled() && widthToHeightRatio > threshold;
        if (isExtremelyWide) {
            int croppedWidth = (int) (height / TARGET_COVER_ASPECT_RATIO);
            log.debug("Cropping wide image: {}x{} (ratio {}) -> {}x{}, smartCrop={}", 
                    width, height, String.format("%.2f", widthToHeightRatio), croppedWidth, height, smartCrop);
            return cropFromLeft(image, croppedWidth, height, smartCrop);
        }

        return image;
    }

    private BufferedImage cropFromTop(BufferedImage image, int targetWidth, int targetHeight, boolean smartCrop) {
        int startY = 0;
        if (smartCrop) {
            int contentStartY = findContentStartY(image);
            int margin = (int) (targetHeight * SMART_CROP_MARGIN_PERCENT);
            startY = Math.max(0, contentStartY - margin);
            
            int maxStartY = image.getHeight() - targetHeight;
            startY = Math.min(startY, maxStartY);
        }
        return image.getSubimage(0, startY, targetWidth, targetHeight);
    }

    private BufferedImage cropFromLeft(BufferedImage image, int targetWidth, int targetHeight, boolean smartCrop) {
        int startX = 0;
        if (smartCrop) {
            int contentStartX = findContentStartX(image);
            int margin = (int) (targetWidth * SMART_CROP_MARGIN_PERCENT);
            startX = Math.max(0, contentStartX - margin);
            
            int maxStartX = image.getWidth() - targetWidth;
            startX = Math.min(startX, maxStartX);
        }
        return image.getSubimage(startX, 0, targetWidth, targetHeight);
    }

    private int findContentStartY(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            if (!isRowUniformColor(image, y)) {
                return y;
            }
        }
        return 0;
    }

    private int findContentStartX(BufferedImage image) {
        for (int x = 0; x < image.getWidth(); x++) {
            if (!isColumnUniformColor(image, x)) {
                return x;
            }
        }
        return 0;
    }

    private boolean isRowUniformColor(BufferedImage image, int y) {
        int firstPixel = image.getRGB(0, y);
        for (int x = 1; x < image.getWidth(); x++) {
            if (!colorsAreSimilar(firstPixel, image.getRGB(x, y))) {
                return false;
            }
        }
        return true;
    }

    private boolean isColumnUniformColor(BufferedImage image, int x) {
        int firstPixel = image.getRGB(x, 0);
        for (int y = 1; y < image.getHeight(); y++) {
            if (!colorsAreSimilar(firstPixel, image.getRGB(x, y))) {
                return false;
            }
        }
        return true;
    }

    private boolean colorsAreSimilar(int rgb1, int rgb2) {
        int r1 = (rgb1 >> 16) & 0xFF, g1 = (rgb1 >> 8) & 0xFF, b1 = rgb1 & 0xFF;
        int r2 = (rgb2 >> 16) & 0xFF, g2 = (rgb2 >> 8) & 0xFF, b2 = rgb2 & 0xFF;
        return Math.abs(r1 - r2) <= SMART_CROP_COLOR_TOLERANCE
            && Math.abs(g1 - g2) <= SMART_CROP_COLOR_TOLERANCE
            && Math.abs(b1 - b2) <= SMART_CROP_COLOR_TOLERANCE;
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
        // Note: input image is not flushed here - caller is responsible for its lifecycle
    }    public void deleteBackgroundFile(String filename, Long userId) {
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

    public String getIconsSvgFolder() {
        return Paths.get(appProperties.getPathConfig(), ICONS_DIR, SVG_DIR).toString();
    }

    // ========================================
    // UTILITY METHODS
    // ========================================

    public static String truncate(String input, int maxLength) {
        if (input == null) return null;
        if (maxLength <= 0) return "";
        return input.length() <= maxLength ? input : input.substring(0, maxLength);
    }
}
