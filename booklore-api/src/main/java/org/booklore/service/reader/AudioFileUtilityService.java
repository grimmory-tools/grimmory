package org.booklore.service.reader;

import lombok.extern.slf4j.Slf4j;
import org.booklore.util.MimeDetector;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Utility service for audio file operations.
 * Provides file listing, sorting, content type detection, and other audio file utilities.
 */
@Slf4j
@Service
public class AudioFileUtilityService {

    private static final Set<String> AUDIO_EXTENSIONS = Set.of(
            ".mp3", ".m4a", ".m4b", ".opus"
    );
    private static final Pattern SEPARATOR_PATTERN = Pattern.compile("[_-]+");

    /**
     * List all audio files in a folder, sorted naturally (Track 1, Track 2, ... Track 10).
     */
    public List<Path> listAudioFiles(Path folderPath) {
        try (Stream<Path> files = Files.list(folderPath)) {
            return files.filter(Files::isRegularFile)
                    .filter(this::isAudioFile)
                    .sorted(this::naturalCompare)
                    .toList();
        } catch (IOException e) {
            log.error("Failed to list audio files in folder: {}", folderPath, e);
            return Collections.emptyList();
        }
    }

    /**
     * Check if a file is an audio file based on its extension.
     */
    public boolean isAudioFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return AUDIO_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }

    /**
     * Get the supported audio file extensions.
     */
    public Set<String> getSupportedExtensions() {
        return AUDIO_EXTENSIONS;
    }

    private static final Map<String, String> AUDIO_MIME = Map.of(
            ".mp3", "audio/mpeg", ".m4a", "audio/mp4",
            ".m4b", "audio/mp4", ".opus", "audio/opus");

    /**
     * Get the MIME content type for an audio file using extension-based detection,
     * falling back to Apache Tika for unknown extensions.
     */
    public String getContentType(Path audioPath) {
        String name = audioPath.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            String mime = AUDIO_MIME.get(name.substring(dot));
            if (mime != null) return mime;
        }
        return MimeDetector.detectSafe(audioPath);
    }

    /**
     * Extract a track title from a filename by removing extensions, track numbers, and separators.
     */
    public String getTrackTitleFromFilename(String filename) {
        int dot = filename.lastIndexOf('.');
        String name = dot >= 0 ? filename.substring(0, dot) : filename;

        // Replace separators with spaces
        name = SEPARATOR_PATTERN.matcher(name).replaceAll(" ");

        return name.trim();
    }

    /**
     * Natural sort comparator for strings that may contain numbers.
     */
    private int naturalCompare(Path p1, Path p2) {
        String s1 = p1.getFileName().toString();
        String s2 = p2.getFileName().toString();

        int i1 = 0, i2 = 0;
        while (i1 < s1.length() && i2 < s2.length()) {
            char c1 = s1.charAt(i1);
            char c2 = s2.charAt(i2);

            if (Character.isDigit(c1) && Character.isDigit(c2)) {
                StringBuilder b1 = new StringBuilder();
                while (i1 < s1.length() && Character.isDigit(s1.charAt(i1))) {
                    b1.append(s1.charAt(i1++));
                }
                StringBuilder b2 = new StringBuilder();
                while (i2 < s2.length() && Character.isDigit(s2.charAt(i2))) {
                    b2.append(s2.charAt(i2++));
                }

                long v1 = Long.parseLong(b1.toString());
                long v2 = Long.parseLong(b2.toString());
                if (v1 != v2) return Long.compare(v1, v2);
            } else {
                int cmp = Character.compare(Character.toLowerCase(c1), Character.toLowerCase(c2));
                if (cmp != 0) return cmp;
                i1++;
                i2++;
            }
        }
        return s1.length() - s2.length();
    }
}
