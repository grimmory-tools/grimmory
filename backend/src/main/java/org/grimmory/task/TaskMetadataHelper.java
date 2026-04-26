package org.booklore.task;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

@Slf4j
public class TaskMetadataHelper {

    public static String getCacheSizeString(String cachePath) {
        try {
            Path path = Paths.get(cachePath);

            if (Files.exists(path) && Files.isDirectory(path)) {
                long sizeInBytes = calculateDirectorySize(path);
                return "Current cache size: " + formatBytes(sizeInBytes);
            } else {
                return "Current cache size: 0 B";
            }
        } catch (Exception e) {
            log.error("Error calculating cache size for path: {}", cachePath, e);
            return "Current cache size: Unknown";
        }
    }

    private static long calculateDirectorySize(Path directory) throws IOException {
        try (Stream<Path> walk = Files.walk(directory)) {
            return walk
                    .filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException e) {
                            log.warn("Could not get size of file: {}", path, e);
                            return 0L;
                        }
                    })
                    .sum();
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.2f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
