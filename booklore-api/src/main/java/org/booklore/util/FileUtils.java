package org.booklore.util;

import org.booklore.model.dto.Book;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@UtilityClass
@Slf4j
public class FileUtils {

    private final String FILE_NOT_FOUND_MESSAGE = "File does not exist: ";

    public String getBookFullPath(BookEntity bookEntity) {
        BookFileEntity bookFile = bookEntity.getPrimaryBookFile();
        if (bookFile == null || bookEntity.getLibraryPath() == null) {
            return null;
        }

        return Path.of(bookEntity.getLibraryPath().getPath(), bookFile.getFileSubPath(), bookFile.getFileName())
                .normalize()
                .toString()
                .replace("\\", "/");
    }

    public String getBookFullPath(BookEntity bookEntity, BookFileEntity bookFile) {
        if (bookFile == null || bookEntity.getLibraryPath() == null) {
            return null;
        }

        return Path.of(bookEntity.getLibraryPath().getPath(), bookFile.getFileSubPath(), bookFile.getFileName())
                .normalize()
                .toString()
                .replace("\\", "/");
    }

    public String getBookFullPath(Book book) {
        return book.getPrimaryFile().getFilePath();
    }

    public String getRelativeSubPath(String basePath, Path fullFilePath) {
        return Optional.ofNullable(Path.of(basePath)
                        .relativize(fullFilePath)
                        .getParent())
                .map(path -> path.toString().replace("\\", "/"))
                .orElse("");
    }

    public Long getFileSizeInKb(BookEntity bookEntity) {
        Path filePath = Path.of(getBookFullPath(bookEntity));
        return getFileSizeInKb(filePath);
    }

    public Long getFileSizeInKb(Path filePath) {
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

    /**
     * Calculate total size of all files in a folder (for folder-based audiobooks).
     */
    public Long getFolderSizeInKb(Path folderPath) {
        try {
            if (!Files.exists(folderPath) || !Files.isDirectory(folderPath)) {
                log.warn("Folder does not exist or is not a directory: {}", folderPath.toAbsolutePath());
                return null;
            }
            long totalBytes = Files.walk(folderPath)
                    .filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (IOException e) {
                            return 0L;
                        }
                    })
                    .sum();
            return totalBytes / 1024;
        } catch (IOException e) {
            log.error("Failed to get folder size for path [{}]: {}", folderPath, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Get the first audio file in a folder (sorted alphabetically).
     */
    public Optional<Path> getFirstAudioFileInFolder(Path folderPath) {
        try {
            if (!Files.exists(folderPath) || !Files.isDirectory(folderPath)) {
                return Optional.empty();
            }
            return Files.list(folderPath)
                    .filter(Files::isRegularFile)
                    .filter(p -> isAudioFile(p.getFileName().toString()))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .findFirst();
        } catch (IOException e) {
            log.error("Failed to list folder [{}]: {}", folderPath, e.getMessage(), e);
            return Optional.empty();
        }
    }

    private static final List<String> COVER_IMAGE_BASENAMES = List.of("cover", "folder", "image");
    private static final List<String> IMAGE_EXTENSIONS = List.of("jpg", "jpeg", "png", "webp", "gif", "bmp");

    /**
     * Find a cover image file in a folder by looking for well-known filenames
     * (cover, folder, image) with common image extensions.
     */
    public Optional<Path> findCoverImageInFolder(Path folderPath) {
        try {
            if (folderPath == null || !Files.exists(folderPath) || !Files.isDirectory(folderPath)) {
                return Optional.empty();
            }
            for (String baseName : COVER_IMAGE_BASENAMES) {
                for (String ext : IMAGE_EXTENSIONS) {
                    Path candidate = folderPath.resolve(baseName + "." + ext);
                    if (Files.isRegularFile(candidate)) {
                        return Optional.of(candidate);
                    }
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to find cover image in folder [{}]: {}", folderPath, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Check if a filename is an audio file.
     */
    public boolean isAudioFile(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase();
        return lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".m4b") || lower.endsWith(".opus");
    }

    /**
     * List all audio files in a folder, sorted alphabetically.
     */
    public List<Path> listAudioFilesInFolder(Path folderPath) {
        try {
            if (!Files.exists(folderPath) || !Files.isDirectory(folderPath)) {
                return List.of();
            }
            return Files.list(folderPath)
                    .filter(Files::isRegularFile)
                    .filter(p -> isAudioFile(p.getFileName().toString()))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            log.error("Failed to list audio files in folder [{}]: {}", folderPath, e.getMessage(), e);
            return List.of();
        }
    }

    private static final Pattern LEADING_NUMBER_PREFIX = Pattern.compile("^\\d{1,3}(?:\\.|\\s*-)\\s*");
    private static final Pattern PART_DISC_INDICATOR = Pattern.compile(
            "\\s*[\\(\\[\\-]?\\s*(?:part|pt|dis[ck]|cd)\\s*\\d+\\s*[\\)\\]]?\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TRAILING_NUMBERS = Pattern.compile("\\s*\\d+\\s*$");
    private static final Set<String> GENERIC_AUDIO_TITLES = Set.of(
            "chapter", "track", "part", "disc", "disk", "cd", "side", "intro", "epilogue", "prologue", "outro"
    );

    private static final Set<String> SINGLE_FILE_AUDIOBOOK_EXTENSIONS = Set.of("m4b", "m4a");

    /**
     * Determines if a list of audio files represents a series folder (each file is a separate book)
     * rather than a multi-file audiobook (chapter files for one book).
     *
     * Uses two signals:
     * 1. File format: .m4b/.m4a files are typically complete audiobooks, so a folder of them
     *    is likely a series. .mp3 files are typically chapters/tracks of one audiobook.
     * 2. Title analysis (for .m4b/.m4a only): extracts base titles by stripping numbering and
     *    part indicators. If multiple distinct non-generic titles exist, it's a series.
     *
     * For .mp3 files (the vast majority of chapter-based audiobooks), this always returns false
     * regardless of naming convention, avoiding the impossible task of distinguishing descriptive
     * chapter names from book titles via pattern matching.
     */
    public boolean isSeriesFolder(List<Path> audioFiles) {
        if (audioFiles.size() < 2) {
            return false;
        }

        boolean allSingleFileFormats = audioFiles.stream().allMatch(f -> {
            String name = f.getFileName().toString().toLowerCase();
            int dot = name.lastIndexOf('.');
            return dot > 0 && SINGLE_FILE_AUDIOBOOK_EXTENSIONS.contains(name.substring(dot + 1));
        });

        if (!allSingleFileFormats) {
            return false;
        }

        Set<String> distinctTitles = new HashSet<>();
        for (Path file : audioFiles) {
            String title = extractBaseTitle(file.getFileName().toString());
            if (!title.isEmpty() && !GENERIC_AUDIO_TITLES.contains(title)) {
                distinctTitles.add(title);
            }
        }
        return distinctTitles.size() > 1;
    }

    private String extractBaseTitle(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        String baseName = lastDot > 0 ? fileName.substring(0, lastDot) : fileName;
        baseName = LEADING_NUMBER_PREFIX.matcher(baseName).replaceFirst("");
        baseName = PART_DISC_INDICATOR.matcher(baseName).replaceAll("");
        baseName = TRAILING_NUMBERS.matcher(baseName).replaceAll("");
        return baseName.toLowerCase().trim();
    }

    public void deleteDirectoryRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;

        try (var walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    public String getExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int i = fileName.lastIndexOf('.');
        if (i >= 0 && i < fileName.length() - 1) {
            return fileName.substring(i + 1);
        }
        return "";
    }

    final private List<String> systemDirs = Arrays.asList(
      // synology
      "#recycle",
      "@eaDir",
      // calibre
      ".caltrash"
    );

    private final Set<String> tempExtensions = Set.of(
            ".part", ".tmp", ".crdownload", ".download",
            ".bak", ".old", ".temp", ".tempfile"
    );

    public boolean shouldIgnore(Path path) {
        String fileName = path.getFileName().toString();
        if (!fileName.isEmpty() && fileName.charAt(0) == '.') {
            return true;
        }
        for (Path part : path) {
            if (systemDirs.contains(part.toString())) {
                return true;
            }
        }
        String lowerName = fileName.toLowerCase();
        for (String ext : tempExtensions) {
            if (lowerName.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }
}
