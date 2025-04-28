package com.adityachandel.booklore.util;

import com.adityachandel.booklore.model.entity.BookEntity;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Slf4j
public class FileUtils {

    private static final String FILE_NOT_FOUND_MESSAGE = "File does not exist: ";

    public static String getBookFullPath(BookEntity bookEntity) {
        return String.format("%s/%s/%s", bookEntity.getLibraryPath().getPath(), bookEntity.getFileSubPath(), bookEntity.getFileName());
    }

    public static String getRelativeSubPath(String basePath, Path fullFilePath) {
        return Optional.ofNullable(Path.of(basePath)
                        .relativize(fullFilePath)
                        .getParent())
                .map(path -> path.toString().replace("\\", "/"))
                .orElse("");
    }

    public static Long getFileSizeInKb(BookEntity bookEntity) {
        Path filePath = Path.of(getBookFullPath(bookEntity));
        return getFileSizeInKb(filePath);
    }

    public static Long getFileSizeInKb(Path filePath) {
        try {
            if (!Files.exists(filePath)) {
                log.warn(FILE_NOT_FOUND_MESSAGE + "{}", filePath.toAbsolutePath());
                return null;
            }
            return Files.size(filePath) / 1024;
        } catch (IOException e) {
            log.error("Failed to get file size for path [{}]: {}", filePath, e.getMessage(), e);
            return null;
        }
    }
}