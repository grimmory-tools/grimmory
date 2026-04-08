package org.booklore.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@UtilityClass
public class MimeDetector {

    // Tika is thread-safe one instance for the whole app.
    private static final Tika TIKA = new Tika();

    /**
     * Detects MIME type purely from content bytes, never from the filename.
     * Uses buffered stream; reads only the magic-byte prefix.
     *
     * @return the detected MIME type, or {@code "application/octet-stream"} on failure
     */
    public String detect(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return TIKA.detect(in);
        }
    }

    /**
     * Detects MIME type from an already-open input stream.
     * The stream must support mark/reset (buffered streams do).
     */
    public String detect(InputStream inputStream) throws IOException {
        return TIKA.detect(inputStream); // content-only
    }

    /**
     * Best-effort detection that returns {@code "application/octet-stream"} instead of throwing.
     */
    public String detectSafe(Path path) {
        try {
            return detect(path);
        } catch (IOException e) {
            log.warn("MIME detection failed for {}: {}", path, e.getMessage());
            return "application/octet-stream";
        }
    }

    public boolean isAudio(Path path) throws IOException {
        return detect(path).startsWith("audio/");
    }

    public boolean isImage(Path path) throws IOException {
        return detect(path).startsWith("image/");
    }

    public boolean isImage(String mime) {
        return mime != null && mime.startsWith("image/");
    }

    public boolean isAudio(String mime) {
        return mime != null && mime.startsWith("audio/");
    }

    public boolean isFont(String mime) {
        return mime != null && (mime.startsWith("font/") || mime.startsWith("application/font-")
                || "application/vnd.ms-fontobject".equals(mime));
    }

    public boolean isArchive(String mime) {
        if (mime == null) return false;
        return mime.contains("zip") || mime.contains("rar")
                || mime.contains("7z") || mime.contains("x-7z")
                || mime.contains("gzip") || mime.contains("tar");
    }
}
