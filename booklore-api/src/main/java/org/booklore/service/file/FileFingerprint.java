package org.booklore.service.file;

import org.booklore.util.FileUtils;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;

public class FileFingerprint {

    public static String generateHash(Path filePath) {
        final long base = 1024L;
        final int blockSize = 1024;
        Path normalizedFilePath = validateReadableFilePath(filePath);

        try (RandomAccessFile raf = new RandomAccessFile(normalizedFilePath.toFile(), "r")) {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[blockSize];

            for (int i = -1; i <= 10; i++) {
                long position = base << (2 * i);
                if (position >= raf.length()) break;

                raf.seek(position);
                int read = raf.read(buffer);
                if (read > 0) {
                    md5.update(buffer, 0, read);
                }
            }

            byte[] hash = md5.digest();
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                result.append(String.format("%02x", b));
            }
            return result.toString();

        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to compute partial MD5 hash for input file", e);
        }
    }

    /**
     * Generate a hash for a folder-based audiobook.
     * Uses the first audio file's hash combined with the file count.
     */
    public static String generateFolderHash(Path folderPath) {
        Path normalizedFolderPath = validateReadableFolderPath(folderPath);
        try {
            if (!Files.exists(normalizedFolderPath) || !Files.isDirectory(normalizedFolderPath)) {
                throw new RuntimeException("Folder does not exist: " + normalizedFolderPath);
            }

            List<Path> audioFiles;
            try (var files = Files.list(normalizedFolderPath)) {
                audioFiles = files
                        .filter(Files::isRegularFile)
                        .filter(p -> isAudioFile(p.getFileName().toString()))
                        .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                        .toList();
            }

            if (audioFiles.isEmpty()) {
                throw new RuntimeException("No audio files found in folder");
            }

            // Hash first file and combine with file count for a representative hash
            String firstFileHash = generateHash(audioFiles.getFirst());
            int fileCount = audioFiles.size();

            MessageDigest md5 = MessageDigest.getInstance("MD5");
            md5.update(firstFileHash.getBytes());
            md5.update(String.valueOf(fileCount).getBytes());

            byte[] hash = md5.digest();
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                result.append(String.format("%02x", b));
            }
            return result.toString();

        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to compute folder hash", e);
        }
    }

    private static Path validateReadableFilePath(Path filePath) {
        if (filePath == null) {
            throw new RuntimeException("File path cannot be null");
        }
        if (FileUtils.containsParentTraversal(filePath)) {
            throw new RuntimeException("Invalid file path: traversal detected");
        }

        Path normalizedPath = FileUtils.normalizeAbsolutePath(filePath);
        if (!Files.exists(normalizedPath) || !Files.isRegularFile(normalizedPath)) {
            throw new RuntimeException("File does not exist or is not a regular file");
        }

        return normalizedPath;
    }

    private static Path validateReadableFolderPath(Path folderPath) {
        if (folderPath == null) {
            throw new RuntimeException("Folder path cannot be null");
        }
        if (FileUtils.containsParentTraversal(folderPath)) {
            throw new RuntimeException("Invalid folder path: traversal detected");
        }

        Path normalizedPath = FileUtils.normalizeAbsolutePath(folderPath);
        if (!Files.exists(normalizedPath) || !Files.isDirectory(normalizedPath)) {
            throw new RuntimeException("Folder does not exist or is not a directory");
        }

        return normalizedPath;
    }

    private static boolean isAudioFile(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase();
        return lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".m4b") || lower.endsWith(".opus");
    }
}
