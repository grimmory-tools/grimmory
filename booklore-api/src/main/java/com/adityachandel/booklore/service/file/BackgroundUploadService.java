package com.adityachandel.booklore.service.file;

import com.adityachandel.booklore.model.dto.UploadResponse;
import com.adityachandel.booklore.util.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class BackgroundUploadService {

    private final FileService fileService;

    private static final String JPEG_MIME_TYPE = "image/jpeg";
    private static final String PNG_MIME_TYPE = "image/png";
    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024; // 5MB

    public UploadResponse uploadBackgroundFile(MultipartFile file, Long userId) {
        try {
            validateBackgroundFile(file);

            String originalFilename = Objects.requireNonNull(file.getOriginalFilename());
            String extension = getFileExtension(originalFilename);
            String filename = "1." + extension;

            BufferedImage originalImage;
            try (InputStream inputStream = file.getInputStream()) {
                originalImage = ImageIO.read(inputStream);
            }
            if (originalImage == null) {
                throw new IllegalArgumentException("Invalid image file");
            }

            deleteExistingBackgroundFiles(userId);
            fileService.saveBackgroundImage(originalImage, filename, userId);

            String fileUrl = FileService.getBackgroundUrl(filename, userId);
            return new UploadResponse(fileUrl);
        } catch (Exception e) {
            log.error("Failed to upload background file: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to upload file: " + e.getMessage(), e);
        }
    }

    public UploadResponse uploadBackgroundFromUrl(String imageUrl, Long userId) {
        try {
            URL url = new URI(imageUrl).toURL();
            String originalFilename = Paths.get(url.getPath()).getFileName().toString();
            String extension = getFileExtension(originalFilename);
            String filename = "1." + extension;

            BufferedImage originalImage = FileService.downloadImageFromUrl(imageUrl);
            deleteExistingBackgroundFiles(userId);

            fileService.saveBackgroundImage(originalImage, filename, userId);

            String fileUrl = FileService.getBackgroundUrl(filename, userId);
            return new UploadResponse(fileUrl);
        } catch (Exception e) {
            log.error("Failed to upload background from URL: {}", e.getMessage(), e);
            throw new RuntimeException("Invalid or inaccessible URL: " + e.getMessage(), e);
        }
    }

    public void resetToDefault(Long userId) {
        try {
            deleteExistingBackgroundFiles(userId);
            log.info("Reset background to default successfully for user: {}", userId);
        } catch (Exception e) {
            log.error("Failed to reset background to default: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to reset background: " + e.getMessage(), e);
        }
    }

    private void deleteExistingBackgroundFiles(Long userId) {
        try {
            fileService.deleteBackgroundFile("1.jpg", userId);
            fileService.deleteBackgroundFile("1.jpeg", userId);
            fileService.deleteBackgroundFile("1.png", userId);
        } catch (Exception e) {
            log.warn("Failed to delete existing background files: {}", e.getMessage());
        }
    }

    private void validateBackgroundFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Background image file is empty");
        }
        String contentType = file.getContentType();
        if (!(JPEG_MIME_TYPE.equalsIgnoreCase(contentType) || PNG_MIME_TYPE.equalsIgnoreCase(contentType))) {
            throw new IllegalArgumentException("Background image must be JPEG or PNG format");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("Background image size must not exceed 5 MB");
        }
    }

    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < filename.length() - 1) {
            return filename.substring(lastDotIndex + 1).toLowerCase();
        }
        return "jpg";
    }
}
