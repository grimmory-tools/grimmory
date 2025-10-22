package com.adityachandel.booklore.service.file;

import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.util.PathPatternResolver;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Component
@AllArgsConstructor
public class FileMovingHelper {

    private final AppSettingService appSettingService;


    public String getFileNamingPattern(LibraryEntity library) {
        String pattern = library.getFileNamingPattern();
        if (pattern == null || pattern.trim().isEmpty()) {
            try {
                pattern = appSettingService.getAppSettings().getUploadPattern();
                log.debug("Using default pattern for library {} as no custom pattern is set", library.getName());
            } catch (Exception e) {
                log.warn("Failed to get default upload pattern for library {}: {}", library.getName(), e.getMessage());
            }
        }
        if (pattern == null || pattern.trim().isEmpty()) {
            pattern = "{currentFilename}";
            log.info("No file naming pattern available for library {}. Using fallback pattern: {currentFilename}", library.getName());
        }
        if (pattern.endsWith("/") || pattern.endsWith("\\")) {
            pattern += "{currentFilename}";
        }
        return pattern;
    }

    public Path generateNewFilePath(String libraryRootPath, BookMetadata metadata, String pattern, String fileName) {
        String relativePath = PathPatternResolver.resolvePattern(metadata, pattern, FilenameUtils.getName(fileName));
        return Paths.get(libraryRootPath, relativePath);
    }
}
