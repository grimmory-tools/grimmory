package org.booklore.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Slf4j
@UtilityClass
public class MimeDetector {
    private static final Map<String, String> MEDIA_TYPE_EXTENSIONS = Map.ofEntries(
            Map.entry("text/html", ".html"),
            Map.entry("application/xhtml+xml", ".html"),
            Map.entry("application/javascript", ".js"),
            Map.entry("text/css", ".css"),
            Map.entry("image/jpeg", ".jpg"),
            Map.entry("image/png", ".png"),
            Map.entry("image/gif", ".gif"),
            Map.entry("image/svg+xml", ".svg"),
            Map.entry("font/ttf", ".ttf"),
            Map.entry("font/otf", ".otf"),
            Map.entry("application/vnd.ms-fontobject", ".eot"),
            Map.entry("application/xml", ".xml"),
            Map.entry("application/x-dtbncx+xml", ".ncx"),
            Map.entry("audio/mpeg", ".mp3"),
            Map.entry("video/mp4", ".mp4"),
            Map.entry("audio/mp4", ".m4a"),
            Map.entry("audio/aac", ".aac"),
            Map.entry("audio/wav", ".wav"),
            Map.entry("audio/ogg", ".ogg"),
            Map.entry("application/oebps-package+xml", ".opf"),
            Map.entry("image/webp", ".webp"),
            Map.entry("font/woff", ".woff"),
            Map.entry("font/woff2", ".woff2"),
            Map.entry("application/smil+xml", ".smil"),
            Map.entry("audio/flac", ".flac"),
            Map.entry("video/webm", ".webm"),
            Map.entry("image/avif", ".avif")
    );

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

    public String getExtension(String mediaType) {
        return MEDIA_TYPE_EXTENSIONS.getOrDefault(mediaType, ".bin");
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
