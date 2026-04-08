package org.booklore.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.file.Path;

@Slf4j
@UtilityClass
public class ArchiveUtils {

    public enum ArchiveType {
        ZIP,
        RAR,
        SEVEN_ZIP,
        UNKNOWN
    }

    /**
     * Detects the archive type of a file using content-based MIME detection via Apache Tika.
     * Falls back to extension-based detection only when Tika cannot determine the type.
     */
    public static ArchiveType detectArchiveType(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return ArchiveType.UNKNOWN;
        }

        try {
            String mime = MimeDetector.detect(file.toPath());
            ArchiveType fromContent = mapMimeToArchiveType(mime);
            if (fromContent != ArchiveType.UNKNOWN) {
                return fromContent;
            }
        } catch (IOException e) {
            log.warn("Failed to detect archive type by content for file: {}", file.getAbsolutePath());
        }

        return detectArchiveTypeByExtension(file.getName());
    }

    public static ArchiveType detectArchiveType(Path path) {
        return detectArchiveType(path.toFile());
    }

    public static ArchiveType detectArchiveTypeByExtension(String fileName) {
        if (fileName == null) {
            return ArchiveType.UNKNOWN;
        }
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".cbz") || lower.endsWith(".zip")) {
            return ArchiveType.ZIP;
        }
        if (lower.endsWith(".cbr") || lower.endsWith(".rar")) {
            return ArchiveType.RAR;
        }
        if (lower.endsWith(".cb7") || lower.endsWith(".7z")) {
            return ArchiveType.SEVEN_ZIP;
        }
        return ArchiveType.UNKNOWN;
    }

    private static ArchiveType mapMimeToArchiveType(String mime) {
        if (mime == null) return ArchiveType.UNKNOWN;
        // Tika MIME types:
        //   ZIP:  application/zip
        //   RAR:  application/x-rar-compressed  or  application/vnd.rar
        //   7z:   application/x-7z-compressed
        if (mime.contains("zip")) return ArchiveType.ZIP;
        if (mime.contains("rar")) return ArchiveType.RAR;
        if (mime.contains("7z")) return ArchiveType.SEVEN_ZIP;
        return ArchiveType.UNKNOWN;
    }
}
