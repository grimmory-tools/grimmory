package org.booklore.service.customfont;

import org.booklore.config.AppProperties;
import org.booklore.exception.APIException;
import org.booklore.mapper.CustomFontMapper;
import org.booklore.model.dto.CustomFontDto;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.CustomFontEntity;
import org.booklore.model.enums.FontFormat;
import org.booklore.repository.CustomFontRepository;
import org.booklore.repository.UserRepository;
import org.booklore.util.MimeDetector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomFontService {

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern SPECIAL_CHARS_PATTERN = Pattern.compile("[<>\"'`]");
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");
    private static final Pattern CONTROL_CHARS_PATTERN = Pattern.compile("[\\p{Cntrl}\\p{Cc}\\p{Cf}\\p{Co}\\p{Cn}]");
    private final CustomFontRepository customFontRepository;
    private final UserRepository userRepository;
    private final CustomFontMapper customFontMapper;
    private final AppProperties appProperties;

    private static final int MAX_FONTS_PER_USER = 10;
    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;
    private static final String CUSTOM_FONTS_DIR = "custom-fonts";
    private static final int MAX_FONT_NAME_LENGTH = 100;

    @Transactional
    public CustomFontDto uploadFont(MultipartFile file, String fontName, Long userId) {
        Path fontPath = null;
        CustomFontEntity savedEntity = null;

        try {
            validateFontUpload(file, userId);

            BookLoreUserEntity user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.isEmpty()) {
                throw new IllegalArgumentException("Invalid file name");
            }

            String extension = getFileExtension(originalFilename);
            FontFormat format = FontFormat.fromExtension(extension);

            String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            String fileName = String.format("user_%d_font_%s%s", userId, uuid, format.getExtension());

            Path fontDir = getFontDirectory(userId);
            Files.createDirectories(fontDir);
            fontPath = fontDir.resolve(fileName);

            validatePath(fontPath, fontDir);

            file.transferTo(fontPath.toFile());

            validateFontMagicBytes(fontPath, format);

            String sanitizedFontName = sanitizeFontName(fontName, originalFilename);

            CustomFontEntity entity = CustomFontEntity.builder()
                    .user(user)
                    .fontName(sanitizedFontName)
                    .fileName(fileName)
                    .originalFileName(originalFilename)
                    .format(format)
                    .fileSize(file.getSize())
                    .uploadedAt(LocalDateTime.now())
                    .build();

            savedEntity = customFontRepository.save(entity);
            log.info("Font uploaded successfully for user {}: {} ({})", userId, sanitizedFontName, fileName);

            return customFontMapper.toDto(savedEntity);

        } catch (IOException e) {
            log.error("Failed to upload font for user {}: {}", userId, e.getMessage(), e);
            throw new APIException("Failed to upload font. Please try again or contact support if the problem persists.", HttpStatus.INTERNAL_SERVER_ERROR);
        } finally {
            if (fontPath != null && savedEntity == null) {
                try {
                    Files.deleteIfExists(fontPath);
                    log.debug("Cleaned up orphaned font file after upload failure: {}", fontPath);
                } catch (IOException e) {
                    log.warn("Failed to cleanup orphaned font file: {}", fontPath, e);
                }
            }
        }
    }

    public List<CustomFontDto> getUserFonts(Long userId) {
        List<CustomFontEntity> fonts = customFontRepository.findByUserId(userId);
        return fonts.stream()
                .map(customFontMapper::toDto)
                .toList();
    }

    @Transactional
    public void deleteFont(Long fontId, Long userId) {
        CustomFontEntity font = customFontRepository.findByIdAndUserId(fontId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Font not found or access denied"));

        IOException fileDeleteException = null;

        try {
            Path fontDir = getFontDirectory(userId);
            Path fontPath = fontDir.resolve(font.getFileName());

            validatePath(fontPath, fontDir);

            Files.deleteIfExists(fontPath);
        } catch (IOException e) {
            log.error("Failed to delete font file for user {}: {}", userId, e.getMessage(), e);
            fileDeleteException = e;
        }

        try {
            customFontRepository.delete(font);
            log.info("Font deleted successfully for user {}: {} ({})", userId, font.getFontName(), font.getFileName());
        } catch (Exception dbException) {
            log.error("Failed to delete font from database for user {}: {}", userId, dbException.getMessage(), dbException);

            if (fileDeleteException != null) {
                log.error("Additionally, file deletion also failed for font: {}", font.getFileName(), fileDeleteException);
            }

            throw new APIException("Failed to delete font. Please try again or contact support if the problem persists.", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        if (fileDeleteException != null) {
            log.warn("Font deleted from database but failed to delete file for user {}: {}", userId, font.getFileName(), fileDeleteException);
        }
    }

    public Resource getFontFile(Long fontId, Long userId) {
        CustomFontEntity font = customFontRepository.findByIdAndUserId(fontId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Font not found or access denied"));

        Path fontDir = getFontDirectory(userId);
        Path fontPath = fontDir.resolve(font.getFileName());

        try {
            validatePath(fontPath, fontDir);
        } catch (IOException e) {
            log.error("Invalid font path for user {}: {}", userId, fontPath, e);
            throw new IllegalArgumentException("Invalid font file path");
        }

        File fontFile = fontPath.toFile();

        if (!fontFile.exists()) {
            throw new IllegalArgumentException("Font file not found on disk");
        }

        return new FileSystemResource(fontFile);
    }

    public FontFormat getFontFormat(Long fontId, Long userId) {
        CustomFontEntity font = customFontRepository.findByIdAndUserId(fontId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Font not found or access denied"));
        return font.getFormat();
    }

    private void validateFontUpload(MultipartFile file, Long userId) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("File size exceeds maximum limit of 5MB");
        }

        int currentFontCount = customFontRepository.countByUserId(userId);
        if (currentFontCount >= MAX_FONTS_PER_USER) {
            throw new IllegalArgumentException(String.format("Font limit exceeded. Maximum %d fonts per user allowed", MAX_FONTS_PER_USER));
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new IllegalArgumentException("Invalid file name");
        }

        String extension = getFileExtension(originalFilename);
        if (!FontFormat.isSupportedExtension(extension)) {
            throw new IllegalArgumentException("Unsupported font format. Allowed formats: .ttf, .otf, .woff, .woff2");
        }

        String contentType = file.getContentType();
        if (contentType != null && !isSupportedMimeType(contentType)) {
            log.warn("Potentially invalid MIME type for font: {}", contentType);
        }
    }

    private boolean isSupportedMimeType(String mimeType) {
        return mimeType.equals("font/ttf") ||
               mimeType.equals("font/otf") ||
               mimeType.equals("font/woff") ||
               mimeType.equals("font/woff2") ||
               mimeType.equals("application/x-font-ttf") ||
               mimeType.equals("application/x-font-opentype") ||
               mimeType.equals("application/font-woff") ||
               mimeType.equals("application/font-woff2") ||
               mimeType.equals("application/octet-stream");
    }

    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1) {
            throw new IllegalArgumentException("File has no extension");
        }
        return filename.substring(lastDotIndex);
    }

    private Path getFontDirectory(Long userId) {
        return Paths.get(appProperties.getPathConfig(), CUSTOM_FONTS_DIR, String.valueOf(userId));
    }

    /**
     * Validates that the resolved path stays within the expected parent directory
     * to prevent directory traversal attacks.
     */
    private void validatePath(Path resolvedPath, Path expectedParent) throws IOException {
        Path normalizedPath = resolvedPath.toAbsolutePath().normalize();
        Path normalizedParent = expectedParent.toAbsolutePath().normalize();

        if (!normalizedPath.startsWith(normalizedParent)) {
            log.error("Path traversal attempt detected: {} does not start with {}", normalizedPath, normalizedParent);
            throw new IOException("Invalid file path: path traversal detected");
        }
    }

    /**
     * Validates font file format by detecting MIME type from content via Apache Tika.
     * Prevents malicious files from being uploaded with font extensions.
     */
    private void validateFontMagicBytes(Path fontPath, FontFormat expectedFormat) throws IOException {
        String detectedMime = MimeDetector.detect(fontPath);

        FontFormat detectedFormat = null;
        try {
            detectedFormat = FontFormat.fromMimeType(detectedMime);
        } catch (IllegalArgumentException ignored) {
            // Tika may return a generic MIME treated as unknown format below
        }

        // Also accept application/x-font-ttf / application/x-font-otf variants
        if (detectedFormat == null) {
            if (detectedMime.contains("ttf") || detectedMime.equals("application/x-font-truetype")) {
                detectedFormat = FontFormat.TTF;
            } else if (detectedMime.contains("otf") || detectedMime.equals("application/x-font-opentype")) {
                detectedFormat = FontFormat.OTF;
            } else if (detectedMime.contains("woff2")) {
                detectedFormat = FontFormat.WOFF2;
            } else if (detectedMime.contains("woff")) {
                detectedFormat = FontFormat.WOFF;
            }
        }

        if (detectedFormat != expectedFormat) {
            log.error("Font file MIME validation failed. Expected: {}, Detected MIME: {} (mapped: {})",
                    expectedFormat, detectedMime, detectedFormat);
            throw new IllegalArgumentException(
                    String.format("Invalid font file format. File appears to be %s but extension indicates %s",
                            detectedFormat != null ? detectedFormat : "Unknown", expectedFormat)
            );
        }

        log.debug("Font file validated successfully via Tika: {} (MIME: {})", detectedFormat, detectedMime);
    }

    /**
     * Sanitizes font name to prevent XSS and ensure data integrity.
     */
    private String sanitizeFontName(String fontName, String fallbackName) {
        if (fontName == null || fontName.trim().isEmpty()) {
            fontName = fallbackName;
        }

        String sanitized = fontName.trim();

        sanitized = CONTROL_CHARS_PATTERN.matcher(sanitized).replaceAll("");

        sanitized = HTML_TAG_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = SPECIAL_CHARS_PATTERN.matcher(sanitized).replaceAll("");

        sanitized = WHITESPACE_PATTERN.matcher(sanitized).replaceAll(" ");

        sanitized = sanitized.trim();

        if (sanitized.isEmpty()) {
            sanitized = fallbackName;
        }

        if (sanitized.length() > MAX_FONT_NAME_LENGTH) {
            sanitized = sanitized.substring(0, MAX_FONT_NAME_LENGTH).trim();
        }

        return sanitized;
    }
}
