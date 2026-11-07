package org.booklore.service.file;

import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.monitoring.MonitoringRegistrationService;
import org.booklore.util.PathPatternResolver;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@AllArgsConstructor
public class FileMoveHelper {

    private final MonitoringRegistrationService monitoringRegistrationService;
    private final AppSettingService appSettingService;

    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 100;
    private static final Set<Class<? extends Exception>> RETRYABLE_EXCEPTIONS = Set.of(
            NoSuchFileException.class,
            AccessDeniedException.class,
            FileSystemException.class,
            DirectoryNotEmptyException.class
    );
    private static final Set<String> IGNORED_FILENAMES = Set.of(".DS_Store", "Thumbs.db");
    public static final String TEMP_SUFFIX = ".tmp_move";

    public boolean waitForFileAccessible(Path path) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            if (Files.exists(path) && Files.isReadable(path)) {
                return true;
            }
            if (attempt < MAX_ATTEMPTS) {
                log.debug("File not accessible on attempt {}/{}, waiting {}ms: {}",
                        attempt, MAX_ATTEMPTS, RETRY_DELAY_MS, path);
                waitBeforeRetry();
            }
        }
        return false;
    }

    public boolean validateSourceExists(Path sourcePath, boolean isFolderBased) {
        if (isFolderBased) {
            return Files.isDirectory(sourcePath);
        }
        return Files.exists(sourcePath);
    }

    /**
     * Verifies that every planned move can be performed without overwriting anything.
     * Fails if two sources resolve to the same target, if a target is already occupied
     * by a file other than its own source (a case-only rename of the same file is allowed),
     * or if an existing source still has a {@value #TEMP_SUFFIX} sibling left by an earlier failed
     * move (that leftover may be the only copy of a file, so it is never overwritten).
     * Also fails if a reserved slot is occupied by a file other than one of the sources being moved.
     * Must be called before any file is touched.
     *
     * @param sourceToTarget planned moves, keyed by source path
     * @param reservedSlots  paths nothing is moved into, but that the moved book will use afterwards
     *                       (e.g. its sidecar slots), so they must not belong to another file
     * @throws FileAlreadyExistsException if a move would overwrite an existing file
     */
    public void ensureTargetsAvailable(Map<Path, Path> sourceToTarget, Set<Path> reservedSlots) throws IOException {
        Map<Path, Path> claimedTargets = new HashMap<>();
        for (Map.Entry<Path, Path> entry : sourceToTarget.entrySet()) {
            Path source = entry.getKey();
            Path target = entry.getValue();
            Path normalizedTarget = target.toAbsolutePath().normalize();

            Path previousSource = claimedTargets.putIfAbsent(normalizedTarget, source);
            if (previousSource != null) {
                throw new FileAlreadyExistsException(target.toString(), null,
                        "Both " + previousSource + " and " + source + " would be moved to this path");
            }

            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !isSameFile(source, target)) {
                throw new FileAlreadyExistsException(target.toString(), null,
                        "Target is already occupied by another file; refusing to overwrite it while moving " + source);
            }

            Path staleTemp = tempPathFor(source);
            if (Files.exists(source, LinkOption.NOFOLLOW_LINKS) && Files.exists(staleTemp, LinkOption.NOFOLLOW_LINKS)) {
                throw new FileAlreadyExistsException(staleTemp.toString(), null,
                        "Leftover temporary file from an earlier failed move; it may be the only copy of a file and must be checked manually");
            }
        }

        for (Path slot : reservedSlots) {
            if (Files.exists(slot, LinkOption.NOFOLLOW_LINKS)
                    && sourceToTarget.keySet().stream().noneMatch(source -> isSameFile(source, slot))) {
                throw new FileAlreadyExistsException(slot.toString(), null,
                        "Slot the moved book will use is already occupied by another file");
            }
        }
    }

    public static Path tempPathFor(Path source) {
        return source.resolveSibling(source.getFileName().toString() + TEMP_SUFFIX);
    }

    public static boolean isTempPath(String path) {
        return path != null && path.endsWith(TEMP_SUFFIX);
    }

    private boolean isSameFile(Path a, Path b) {
        try {
            return Files.isSameFile(a, b);
        } catch (IOException e) {
            return false;
        }
    }

    public void moveFile(Path source, Path target) throws IOException {
        if (!waitForFileAccessible(source)) {
            throw new NoSuchFileException(source.toString(), null, "Source file not accessible after retries");
        }

        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }

        log.info("Moving file from {} to {}", source, target);
        executeWithRetry(() -> Files.move(source, target));
    }

    public Path moveFileWithBackup(Path source) throws IOException {
        if (!waitForFileAccessible(source)) {
            throw new NoSuchFileException(source.toString(), null, "Source file not accessible after retries");
        }

        Path tempPath = tempPathFor(source);
        log.info("Moving file from {} to temporary location {}", source, tempPath);
        executeWithRetry(() -> Files.move(source, tempPath));
        return tempPath;
    }

    public void commitMove(Path tempPath, Path target) throws IOException {
        if (!waitForFileAccessible(tempPath)) {
            throw new NoSuchFileException(tempPath.toString(), null, "Temporary file not accessible before commit");
        }

        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        log.info("Committing move from temporary location {} to {}", tempPath, target);
        executeWithRetry(() -> Files.move(tempPath, target));
    }

    public void rollbackMove(Path tempPath, Path originalSource) {
        if (!Files.exists(tempPath)) {
            return;
        }
        
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                log.info("Rolling back move from {} to {}", tempPath, originalSource);
                Files.move(tempPath, originalSource);
                return;
            } catch (IOException e) {
                // In both cases the temp file still holds the file's content: it must be recovered, not deleted.
                if (e instanceof FileAlreadyExistsException) {
                    log.error("Cannot roll back file move: original path {} is now occupied by another file. " +
                            "Temp file {} may be the only copy of the file and needs manual recovery.",
                            originalSource, tempPath, e);
                    return;
                }
                if (attempt == MAX_ATTEMPTS) {
                    log.error("Failed to roll back file move from {} to {} after {} attempts. " +
                            "Temp file {} may be the only copy of the file and needs manual recovery.",
                            tempPath, originalSource, MAX_ATTEMPTS, tempPath, e);
                    return;
                }
                log.warn("Rollback attempt {}/{} failed, retrying: {}", attempt, MAX_ATTEMPTS, e.getMessage());
                waitBeforeRetry();
            }
        }
    }

    public String extractSubPath(Path filePath, LibraryPathEntity libraryPathEntity) {
        Path libraryRoot = Paths.get(libraryPathEntity.getPath()).toAbsolutePath().normalize();
        Path parentDir = filePath.getParent().toAbsolutePath().normalize();
        Path relativeSubPath = libraryRoot.relativize(parentDir);
        return relativeSubPath.toString().replace('\\', '/');
    }

    public void unregisterLibrary(Long libraryId) {
        monitoringRegistrationService.unregisterLibrary(libraryId);
    }

    public void registerLibraryPaths(Long libraryId, Path libraryRoot) {
        log.debug("Registering library paths for library {} with root {}", libraryId, libraryRoot);
        monitoringRegistrationService.registerLibraryPaths(libraryId, libraryRoot);
    }

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

    public Path generateNewFilePath(BookEntity book, LibraryPathEntity libraryPathEntity, String pattern) {
        String newRelativePathStr = PathPatternResolver.resolvePattern(book, pattern);
        if (newRelativePathStr.startsWith("/") || newRelativePathStr.startsWith("\\")) {
            newRelativePathStr = newRelativePathStr.substring(1);
        }
        String path = libraryPathEntity.getPath();
        return Paths.get(path, newRelativePathStr);
    }

    public Path generateNewFilePath(BookEntity book, BookFileEntity bookFile, LibraryPathEntity libraryPathEntity, String pattern) {
        String newRelativePathStr = PathPatternResolver.resolvePattern(book, bookFile, pattern, bookFile.isFolderBased());
        if (newRelativePathStr.startsWith("/") || newRelativePathStr.startsWith("\\")) {
            newRelativePathStr = newRelativePathStr.substring(1);
        }
        String path = libraryPathEntity.getPath();
        return Paths.get(path, newRelativePathStr);
    }

    public void deleteEmptyParentDirsUpToLibraryFolders(Path currentDir, Set<Path> libraryRoots) {
        Path dir = currentDir.toAbsolutePath().normalize();
        Set<Path> normalizedRoots = new HashSet<>();
        for (Path root : libraryRoots) {
            normalizedRoots.add(root.toAbsolutePath().normalize());
        }
        while (dir != null) {
            if (isLibraryRoot(dir, normalizedRoots)) {
                break;
            }
            if (deleteIfEffectivelyEmpty(dir, normalizedRoots)) {
                dir = dir.getParent();
            } else {
                break;
            }
        }
    }

    private boolean deleteIfEffectivelyEmpty(Path dir, Set<Path> libraryRoots) {
        if (isLibraryRoot(dir, libraryRoots)) {
            return false;
        }

        File[] contents = dir.toFile().listFiles();
        if (contents == null) {
            log.warn("Cannot read directory: {}. Stopping cleanup.", dir);
            return false;
        }

        boolean deletedAnySubdirectory = recursivelyDeleteEmptySubdirectories(contents, libraryRoots);
        
        if (deletedAnySubdirectory) {
            waitBeforeRetry();
        }

        File[] remainingContents = dir.toFile().listFiles();
        if (remainingContents == null) {
            log.warn("Cannot read directory after subdirectory cleanup: {}", dir);
            return false;
        }

        if (isSafeToDelete(remainingContents)) {
            deleteIgnoredFilesAndDirectory(remainingContents, dir);
            return true;
        }

        return false;
    }

    private boolean recursivelyDeleteEmptySubdirectories(File[] contents, Set<Path> libraryRoots) {
        boolean deletedAny = false;
        for (File file : contents) {
            if (isNonSymlinkDirectory(file)) {
                if (deleteIfEffectivelyEmpty(file.toPath(), libraryRoots)) {
                    deletedAny = true;
                }
            }
        }
        return deletedAny;
    }

    private boolean isNonSymlinkDirectory(File file) {
        return file.isDirectory() && !Files.isSymbolicLink(file.toPath());
    }

    private boolean isLibraryRoot(Path currentDir, Set<Path> normalizedRoots) {
        for (Path root : normalizedRoots) {
            try {
                if (Files.isSameFile(root, currentDir)) {
                    return true;
                }
            } catch (IOException e) {
                log.warn("Failed to compare paths: {} and {}", root, currentDir);
            }
        }
        return false;
    }

    private boolean isSafeToDelete(File[] files) {
        for (File file : files) {
            if (Files.isSymbolicLink(file.toPath()) 
                    || file.isDirectory() 
                    || !IGNORED_FILENAMES.contains(file.getName())) {
                return false;
            }
        }
        return true;
    }

    private void deleteIgnoredFilesAndDirectory(File[] files, Path currentDir) {
        for (File file : files) {
            try {
                Files.delete(file.toPath());
                log.info("Deleted ignored file: {}", file.getAbsolutePath());
            } catch (IOException e) {
                log.warn("Failed to delete ignored file: {}", file.getAbsolutePath());
            }
        }
        try {
            executeWithRetry(() -> Files.delete(currentDir));
            log.info("Deleted empty directory: {}", currentDir);
        } catch (IOException e) {
            log.warn("Failed to delete directory: {}", currentDir, e);
        }
    }

    @FunctionalInterface
    private interface FileOperation {
        void execute() throws IOException;
    }

    private void executeWithRetry(FileOperation operation) throws IOException {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                operation.execute();
                return;
            } catch (IOException e) {
                if (!isRetryableException(e) || attempt == MAX_ATTEMPTS) {
                    throw e;
                }

                log.warn("File operation failed (attempt {}/{}), retrying in {}ms: {}",
                        attempt, MAX_ATTEMPTS, RETRY_DELAY_MS, e.getMessage());

                waitBeforeRetry();
            }
        }
    }

    private boolean isRetryableException(IOException e) {
        // FileAlreadyExistsException is a FileSystemException, but retrying cannot make the target disappear
        if (e instanceof FileAlreadyExistsException) {
            return false;
        }
        return RETRYABLE_EXCEPTIONS.stream().anyMatch(type -> type.isInstance(e));
    }

    private void waitBeforeRetry() {
        try {
            Thread.sleep(RETRY_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
