package org.booklore.service.reader;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;
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
        String fileName = path.getFileName().toString().toLowerCase();
        return AUDIO_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }

    /**
     * Get the MIME content type for an audio file.
     */
    public String getContentType(Path audioPath) {
        String fileName = audioPath.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".m4b") || fileName.endsWith(".m4a")) {
            return "audio/mp4";
        } else if (fileName.endsWith(".mp3")) {
            return "audio/mpeg";
        } else if (fileName.endsWith(".opus")) {
            return "audio/opus";
        }
        return "application/octet-stream";
    }

    /**
     * Extract a readable track title from a filename.
     * Removes extension and replaces underscores/hyphens with spaces.
     */
    public String getTrackTitleFromFilename(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0) {
            filename = filename.substring(0, lastDot);
        }
        return filename.replaceAll("[_-]+", " ").trim();
    }

    /**
     * Get the set of supported audio file extensions.
     */
    public Set<String> getSupportedExtensions() {
        return AUDIO_EXTENSIONS;
    }

    private int naturalCompare(Path p1, Path p2) {
        return naturalCompare(p1.getFileName().toString(), p2.getFileName().toString());
    }

    /**
     * Natural alphanumeric sort comparison.
     * Handles filenames like "Track 1.mp3", "Track 2.mp3", ..., "Track 10.mp3" correctly.
     */
    private int naturalCompare(String s1, String s2) {
        int i1 = 0, i2 = 0;
        while (i1 < s1.length() && i2 < s2.length()) {
            char c1 = s1.charAt(i1);
            char c2 = s2.charAt(i2);

            if (Character.isDigit(c1) && Character.isDigit(c2)) {
                StringBuilder num1 = new StringBuilder();
                StringBuilder num2 = new StringBuilder();
                while (i1 < s1.length() && Character.isDigit(s1.charAt(i1))) {
                    num1.append(s1.charAt(i1++));
                }
                while (i2 < s2.length() && Character.isDigit(s2.charAt(i2))) {
                    num2.append(s2.charAt(i2++));
                }
                int cmp = Long.compare(Long.parseLong(num1.toString()), Long.parseLong(num2.toString()));
                if (cmp != 0) return cmp;
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
