package com.adityachandel.booklore.service.file;

import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.request.FileMoveRequest;
import com.adityachandel.booklore.model.entity.BookAdditionalFileEntity;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.BookAdditionalFileRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.BookQueryService;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.service.library.LibraryService;
import com.adityachandel.booklore.service.monitoring.MonitoringService;
import com.adityachandel.booklore.util.PathPatternResolver;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@Slf4j
@Service
@AllArgsConstructor
public class FileMoveService {

    private final BookQueryService bookQueryService;
    private final BookRepository bookRepository;
    private final BookAdditionalFileRepository bookAdditionalFileRepository;
    private final BookMapper bookMapper;
    private final NotificationService notificationService;
    private final LibraryService libraryService;
    private final MonitoringService monitoringService;
    private final AppSettingService appSettingService;

    public void moveFiles(FileMoveRequest request) {
        Set<Long> bookIds = request.getBookIds();
        List<BookEntity> books = bookQueryService.findAllWithMetadataByIds(bookIds);
        String defaultPattern = appSettingService.getAppSettings().getUploadPattern();

        log.info("Starting file move for {} books", books.size());

        Set<Long> libraryIds = new HashSet<>();
        List<Book> updatedBooks = new ArrayList<>();
        boolean didPause = pauseMonitoringIfNeeded();

        try {
            for (BookEntity book : books) {
                processBookMove(book, defaultPattern, updatedBooks, libraryIds);
            }

            log.info("Completed file move for {} books.", books.size());
            sendUpdateNotifications(updatedBooks);
            rescanLibraries(libraryIds);

        } finally {
            resumeMonitoringWithDelay(didPause);
        }
    }

    private boolean pauseMonitoringIfNeeded() {
        if (!monitoringService.isPaused()) {
            monitoringService.pauseMonitoring();
            return true;
        }
        return false;
    }

    private void processBookMove(BookEntity book, String defaultPattern, List<Book> updatedBooks, Set<Long> libraryIds) {
        if (book.getMetadata() == null) return;

        String pattern = getFileNamingPattern(book, defaultPattern);
        if (pattern == null) return;

        if (!hasRequiredPathComponents(book)) return;

        Path oldFilePath = book.getFullFilePath();
        if (!Files.exists(oldFilePath)) {
            log.warn("File does not exist for book id {}: {}", book.getId(), oldFilePath);
            return;
        }

        log.info("Processing book id {}: '{}'", book.getId(), book.getMetadata().getTitle());

        Path newFilePath = generateNewFilePath(book, pattern);
        if (oldFilePath.equals(newFilePath)) {
            log.info("Source and destination paths are identical for book id {}. Skipping.", book.getId());
            return;
        }

        try {
            moveFileAndUpdateBook(book, oldFilePath, newFilePath, updatedBooks, libraryIds);

            // Move additional files if present
            if (book.getAdditionalFiles() != null && !book.getAdditionalFiles().isEmpty()) {
                moveAdditionalFiles(book, pattern);
            }
        } catch (IOException e) {
            log.error("Failed to move file for book id {}: {}", book.getId(), e.getMessage(), e);
        }
    }

    private String getFileNamingPattern(BookEntity book, String defaultPattern) {
        if (book.getLibraryPath() == null || book.getLibraryPath().getLibrary() == null) {
            log.error("Book id {} has no library associated. Skipping.", book.getId());
            return null;
        }
        LibraryEntity library = book.getLibraryPath().getLibrary();
        String pattern = library.getFileNamingPattern();
        if (pattern == null || pattern.trim().isEmpty()) {
            pattern = defaultPattern;
            log.info("Using default pattern for library {} as no custom pattern is set", library.getName());
        }
        if (pattern == null || pattern.trim().isEmpty()) {
            log.error("No file naming pattern available for book id {}. Skipping.", book.getId());
            return null;
        }

        return pattern;
    }

    private boolean hasRequiredPathComponents(BookEntity book) {
        if (book.getLibraryPath() == null || book.getLibraryPath().getPath() == null ||
                book.getFileSubPath() == null || book.getFileName() == null) {
            log.error("Missing required path components for book id {}. Skipping.", book.getId());
            return false;
        }
        return true;
    }

    private Path generateNewFilePath(BookEntity book, String pattern) {
        String newRelativePathStr = generatePathFromPattern(book, pattern);
        if (newRelativePathStr.startsWith("/") || newRelativePathStr.startsWith("\\")) {
            newRelativePathStr = newRelativePathStr.substring(1);
        }

        Path libraryRoot = Paths.get(book.getLibraryPath().getPath()).toAbsolutePath().normalize();
        return libraryRoot.resolve(newRelativePathStr).normalize();
    }

    private void moveFileAndUpdateBook(BookEntity book, Path oldFilePath, Path newFilePath,
                                       List<Book> updatedBooks, Set<Long> libraryIds) throws IOException {
        if (newFilePath.getParent() != null) {
            Files.createDirectories(newFilePath.getParent());
        }

        log.info("Moving file from {} to {}", oldFilePath, newFilePath);
        Files.move(oldFilePath, newFilePath, StandardCopyOption.REPLACE_EXISTING);

        updateBookPaths(book, newFilePath);
        bookRepository.save(book);
        updatedBooks.add(bookMapper.toBook(book));

        log.info("Updated book id {} with new path", book.getId());

        Path libraryRoot = Paths.get(book.getLibraryPath().getPath()).toAbsolutePath().normalize();
        deleteEmptyParentDirsUpToLibraryFolders(oldFilePath.getParent(), Set.of(libraryRoot));

        if (book.getLibraryPath().getLibrary().getId() != null) {
            libraryIds.add(book.getLibraryPath().getLibrary().getId());
        }
    }

    private void updateBookPaths(BookEntity book, Path newFilePath) {
        String newFileName = newFilePath.getFileName().toString();
        Path libraryRoot = Paths.get(book.getLibraryPath().getPath()).toAbsolutePath().normalize();
        Path newRelativeSubPath = libraryRoot.relativize(newFilePath.getParent());
        String newFileSubPath = newRelativeSubPath.toString().replace('\\', '/');

        book.setFileSubPath(newFileSubPath);
        book.setFileName(newFileName);
    }

    private void sendUpdateNotifications(List<Book> updatedBooks) {
        if (!updatedBooks.isEmpty()) {
            notificationService.sendMessage(Topic.BOOK_METADATA_BATCH_UPDATE, updatedBooks);
        }
    }

    private void rescanLibraries(Set<Long> libraryIds) {
        for (Long libraryId : libraryIds) {
            try {
                libraryService.rescanLibrary(libraryId);
                log.info("Rescanned library id {} after file move", libraryId);
            } catch (Exception e) {
                log.error("Failed to rescan library id {}: {}", libraryId, e.getMessage(), e);
            }
        }
    }

    private void resumeMonitoringWithDelay(boolean didPause) {
        if (didPause && !monitoringService.isPaused()) {
            log.info("Monitoring already resumed by another thread.");
        } else if (didPause) {
            Thread.startVirtualThread(() -> {
                try {
                    Thread.sleep(5_000);
                    monitoringService.resumeMonitoring();
                    log.info("Monitoring resumed after 5s delay");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Interrupted while delaying resume of monitoring");
                }
            });
        }
    }

    public String generatePathFromPattern(BookEntity book, String pattern) {
        return PathPatternResolver.resolvePattern(book, pattern);
    }

    private void moveAdditionalFiles(BookEntity book, String pattern) throws IOException {
        Map<String, Integer> fileNameCounter = new HashMap<>();

        for (BookAdditionalFileEntity additionalFile : book.getAdditionalFiles()) {
            Path oldAdditionalFilePath = additionalFile.getFullFilePath();
            if (!Files.exists(oldAdditionalFilePath)) {
                log.warn("Additional file does not exist for book id {}: {}", book.getId(), oldAdditionalFilePath);
                continue;
            }

            String newRelativePathStr = PathPatternResolver.resolvePattern(book.getMetadata(), pattern, additionalFile.getFileName());
            if (newRelativePathStr.startsWith("/") || newRelativePathStr.startsWith("\\")) {
                newRelativePathStr = newRelativePathStr.substring(1);
            }

            Path libraryRoot = Paths.get(book.getLibraryPath().getPath()).toAbsolutePath().normalize();
            Path newAdditionalFilePath = libraryRoot.resolve(newRelativePathStr).normalize();

            // Check for filename uniqueness and add index if necessary
            newAdditionalFilePath = ensureUniqueFilePath(newAdditionalFilePath, fileNameCounter);

            if (oldAdditionalFilePath.equals(newAdditionalFilePath)) {
                log.debug("Source and destination paths are identical for additional file id {}. Skipping.", additionalFile.getId());
                continue;
            }

            // Create parent directories if needed
            if (newAdditionalFilePath.getParent() != null) {
                Files.createDirectories(newAdditionalFilePath.getParent());
            }

            log.info("Moving additional file from {} to {}", oldAdditionalFilePath, newAdditionalFilePath);
            Files.move(oldAdditionalFilePath, newAdditionalFilePath, StandardCopyOption.REPLACE_EXISTING);

            // Update additional file paths
            updateAdditionalFilePaths(additionalFile, newAdditionalFilePath, libraryRoot);
            bookAdditionalFileRepository.save(additionalFile);

            log.info("Updated additional file id {} with new path", additionalFile.getId());
        }
    }

    private Path ensureUniqueFilePath(Path filePath, Map<String, Integer> fileNameCounter) {
        String fileName = filePath.getFileName().toString();
        String baseName = fileName;
        String extension = "";

        int lastDot = fileName.lastIndexOf(".");
        if (lastDot >= 0 && lastDot < fileName.length() - 1) {
            baseName = fileName.substring(0, lastDot);
            extension = fileName.substring(lastDot);
        }

        String fileKey = filePath.toString().toLowerCase();
        Integer count = fileNameCounter.get(fileKey);

        if (count == null) {
            fileNameCounter.put(fileKey, 1);
            return filePath;
        } else {
            // File name already exists, add index
            count++;
            fileNameCounter.put(fileKey, count);
            String newFileName = baseName + "_" + count + extension;
            return filePath.getParent().resolve(newFileName);
        }
    }

    private void updateAdditionalFilePaths(BookAdditionalFileEntity additionalFile, Path newFilePath, Path libraryRoot) {
        String newFileName = newFilePath.getFileName().toString();
        Path newRelativeSubPath = libraryRoot.relativize(newFilePath.getParent());
        String newFileSubPath = newRelativeSubPath.toString().replace('\\', '/');

        additionalFile.setFileSubPath(newFileSubPath);
        additionalFile.setFileName(newFileName);
    }

    public void deleteEmptyParentDirsUpToLibraryFolders(Path currentDir, Set<Path> libraryRoots) throws IOException {
        Set<String> ignoredFilenames = Set.of(".DS_Store", "Thumbs.db");
        currentDir = currentDir.toAbsolutePath().normalize();

        Set<Path> normalizedRoots = new HashSet<>();
        for (Path root : libraryRoots) {
            normalizedRoots.add(root.toAbsolutePath().normalize());
        }

        while (currentDir != null) {
            if (isLibraryRoot(currentDir, normalizedRoots)) {
                log.debug("Reached library root: {}. Stopping cleanup.", currentDir);
                break;
            }

            File[] files = currentDir.toFile().listFiles();
            if (files == null) {
                log.warn("Cannot read directory: {}. Stopping cleanup.", currentDir);
                break;
            }

            if (hasOnlyIgnoredFiles(files, ignoredFilenames)) {
                deleteIgnoredFilesAndDirectory(files, currentDir);
                currentDir = currentDir.getParent();
            } else {
                log.debug("Directory {} contains important files. Stopping cleanup.", currentDir);
                break;
            }
        }
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

    private boolean hasOnlyIgnoredFiles(File[] files, Set<String> ignoredFilenames) {
        for (File file : files) {
            if (!ignoredFilenames.contains(file.getName())) {
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
            Files.delete(currentDir);
            log.info("Deleted empty directory: {}", currentDir);
        } catch (IOException e) {
            log.warn("Failed to delete directory: {}", currentDir, e);
        }
    }
}
