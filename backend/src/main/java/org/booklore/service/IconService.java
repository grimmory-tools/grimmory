package org.booklore.service;

import org.booklore.config.AppProperties;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.request.SvgIconCreateRequest;
import org.booklore.model.dto.response.SvgIconBatchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

@Slf4j
@RequiredArgsConstructor
@Service
public class IconService {

    private static final Pattern INVALID_FILENAME_CHARS_PATTERN = Pattern.compile("[^a-zA-Z0-9._-]");
    private final AppProperties appProperties;

    private final Cache<String, String> svgCache = Caffeine.newBuilder()
            .maximumSize(200)
            .expireAfterAccess(java.time.Duration.ofHours(1))
            .build();

    private static final String ICONS_DIR = "icons";
    private static final String SVG_DIR = "svg";
    private static final String SVG_EXTENSION = ".svg";
    private static final String SVG_START_TAG = "<svg";
    private static final String XML_DECLARATION = "<?xml";
    private static final String SVG_END_TAG = "</svg>";

    public void saveSvgIcon(SvgIconCreateRequest request) {
        validateSvgData(request.getSvgData());

        String filename = normalizeFilename(request.getSvgName());
        Path filePath = validateAndResolvePath(filename);

        if (Files.exists(filePath)) {
            log.warn("SVG icon already exists: {}", filename);
            throw ApiError.ICON_ALREADY_EXISTS.createException(request.getSvgName());
        }

        try {
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, request.getSvgData(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            updateCache(filename, request.getSvgData());

            log.info("SVG icon saved successfully: {}", filename);
        } catch (IOException e) {
            log.error("Failed to save SVG icon: {}", e.getMessage(), e);
            throw ApiError.FILE_READ_ERROR.createException("Failed to save SVG icon: " + e.getMessage());
        }
    }

    public SvgIconBatchResponse saveBatchSvgIcons(List<SvgIconCreateRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw ApiError.INVALID_INPUT.createException("Icons list cannot be empty");
        }

        List<SvgIconBatchResponse.IconSaveResult> results = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;

        for (SvgIconCreateRequest request : requests) {
            try {
                saveSvgIcon(request);
                results.add(SvgIconBatchResponse.IconSaveResult.builder()
                        .iconName(request.getSvgName())
                        .success(true)
                        .build());
                successCount++;
            } catch (Exception e) {
                log.warn("Failed to save icon '{}': {}", request.getSvgName(), e.getMessage());
                results.add(SvgIconBatchResponse.IconSaveResult.builder()
                        .iconName(request.getSvgName())
                        .success(false)
                        .errorMessage(e.getMessage())
                        .build());
                failureCount++;
            }
        }

        log.info("Batch save completed: {} successful, {} failed", successCount, failureCount);

        return SvgIconBatchResponse.builder()
                .totalRequested(requests.size())
                .successCount(successCount)
                .failureCount(failureCount)
                .results(results)
                .build();
    }

    public String getSvgIcon(String name) {
        String filename = normalizeFilename(name);
        String cachedSvg = svgCache.getIfPresent(filename);

        if (cachedSvg != null) {
            return cachedSvg;
        }
        return loadAndCacheIcon(filename, name);
    }

    private String loadAndCacheIcon(String filename, String originalName) {
        Path filePath = validateAndResolvePath(filename);

        if (!Files.exists(filePath)) {
            log.warn("SVG icon not found: {}", filename);
            throw ApiError.FILE_NOT_FOUND.createException("SVG icon not found: " + originalName);
        }

        try {
            String svgData = Files.readString(filePath);
            updateCache(filename, svgData);
            return svgData;
        } catch (IOException e) {
            log.error("Failed to read SVG icon: {}", e.getMessage(), e);
            throw ApiError.FILE_READ_ERROR.createException("Failed to read SVG icon: " + e.getMessage());
        }
    }

    public void deleteSvgIcon(String svgName) {
        String filename = normalizeFilename(svgName);
        Path filePath = validateAndResolvePath(filename);

        try {
            if (!Files.exists(filePath)) {
                log.warn("SVG icon not found for deletion: {}", filename);
                throw ApiError.FILE_NOT_FOUND.createException("SVG icon not found: " + svgName);
            }

            Files.delete(filePath);
            svgCache.invalidate(filename);

            log.info("SVG icon deleted successfully: {}", filename);
        } catch (IOException e) {
            log.error("Failed to delete SVG icon: {}", e.getMessage(), e);
            throw ApiError.FILE_READ_ERROR.createException("Failed to delete SVG icon: " + e.getMessage());
        }
    }

    private Path validateAndResolvePath(String filename) {
        Path baseDir = getIconsSvgPath().toAbsolutePath().normalize();
        Path filePath = baseDir.resolve(filename).toAbsolutePath().normalize();

        if (!filePath.startsWith(baseDir)) {
            log.error("Potential path traversal attempt: {}", filename);
            throw ApiError.INVALID_INPUT.createException("Invalid icon name");
        }
        return filePath;
    }

    public Page<String> getIconNames(int page, int size) {
        validatePaginationParams(page, size);

        Path iconsPath = getIconsSvgPath();

        if (!Files.exists(iconsPath)) {
            return new PageImpl<>(Collections.emptyList(), PageRequest.of(page, size), 0);
        }

        try (Stream<Path> paths = Files.list(iconsPath)) {
            List<String> allIcons = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(SVG_EXTENSION))
                    .map(path -> path.getFileName().toString().replace(SVG_EXTENSION, ""))
                    .sorted()
                    .toList();

            return createPage(allIcons, page, size);
        } catch (IOException e) {
            log.error("Failed to read icon names: {}", e.getMessage(), e);
            throw ApiError.FILE_READ_ERROR.createException("Failed to read icon names: " + e.getMessage());
        }
    }

    private Page<String> createPage(List<String> allIcons, int page, int size) {
        int totalElements = allIcons.size();
        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, totalElements);

        List<String> pageContent = fromIndex < totalElements
                ? allIcons.subList(fromIndex, toIndex)
                : Collections.emptyList();

        return new PageImpl<>(pageContent, PageRequest.of(page, size), totalElements);
    }

    private void updateCache(String filename, String content) {
        svgCache.put(filename, content);
    }

    private Path getIconsSvgPath() {
        return Paths.get(appProperties.getPathConfig(), ICONS_DIR, SVG_DIR);
    }

    private void validateSvgData(String svgData) {
        if (svgData == null || svgData.isBlank()) {
            throw ApiError.INVALID_INPUT.createException("SVG data cannot be empty");
        }

        String trimmed = svgData.trim();
        if (!trimmed.startsWith(SVG_START_TAG) && !trimmed.startsWith(XML_DECLARATION)) {
            throw ApiError.INVALID_INPUT.createException("Invalid SVG format: must start with <svg or <?xml");
        }

        if (!trimmed.contains(SVG_END_TAG)) {
            throw ApiError.INVALID_INPUT.createException("Invalid SVG format: missing closing </svg> tag");
        }
    }

    private void validatePaginationParams(int page, int size) {
        if (page < 0) {
            throw ApiError.INVALID_INPUT.createException("Page index must not be less than zero");
        }
        if (size < 1) {
            throw ApiError.INVALID_INPUT.createException("Page size must not be less than one");
        }
    }

    private String normalizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw ApiError.INVALID_INPUT.createException("Filename cannot be empty");
        }

        String sanitized = INVALID_FILENAME_CHARS_PATTERN.matcher(filename.trim()).replaceAll("_");
        return sanitized.endsWith(SVG_EXTENSION) ? sanitized : sanitized + SVG_EXTENSION;
    }

    Cache<String, String> getSvgCache() {
        return svgCache;
    }

    public long getIconsLastModified() {
        Path iconsPath = getIconsSvgPath();
        if (!Files.exists(iconsPath)) {
            return 0L;
        }

        try {
            long dirMtime = Files.getLastModifiedTime(iconsPath).toMillis();
            try (Stream<Path> paths = Files.list(iconsPath)) {
                long maxFileMtime = paths.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(SVG_EXTENSION))
                        .mapToLong(path -> {
                            try {
                                return Files.getLastModifiedTime(path).toMillis();
                            } catch (IOException e) {
                                return 0L;
                            }
                        })
                        .max()
                        .orElse(0L);
                return Math.max(dirMtime, maxFileMtime);
            }
        } catch (IOException e) {
            log.error("Failed to check icons directory last modified: {}", e.getMessage());
            return 0L;
        }
    }

    public Map<String, String> getAllIconsContent() {
        Path iconsPath = getIconsSvgPath();

        if (!Files.exists(iconsPath)) {
            return Collections.emptyMap();
        }

        try (Stream<Path> paths = Files.list(iconsPath)) {
            Map<String, String> iconMap = new HashMap<>();

            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(SVG_EXTENSION))
                    .forEach(path -> {
                        try {
                            String filename = path.getFileName().toString();
                            String iconName = filename.replace(SVG_EXTENSION, "");
                            String cached = svgCache.getIfPresent(filename);
                            String content;
                            if (cached != null) {
                                content = cached;
                            } else {
                                content = Files.readString(path);
                                svgCache.put(filename, content);
                            }
                            iconMap.put(iconName, content);
                        } catch (IOException e) {
                            log.warn("Failed to read icon: {}", path.getFileName(), e);
                        }
                    });
            return iconMap;
        } catch (IOException e) {
            log.error("Failed to read icons directory: {}", e.getMessage(), e);
            throw ApiError.FILE_READ_ERROR.createException("Failed to read icons: " + e.getMessage());
        }
    }
}
