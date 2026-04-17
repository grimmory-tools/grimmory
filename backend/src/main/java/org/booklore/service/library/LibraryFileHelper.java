package org.booklore.service.library;

import org.booklore.model.dto.settings.LibraryFile;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.enums.BookFileExtension;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.LibraryOrganizationMode;
import org.booklore.util.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Slf4j
public class LibraryFileHelper {

    private static final int MIN_AUDIO_FILES_FOR_FOLDER_AUDIOBOOK = 2;

    public List<Long> detectDeletedBookIds(List<LibraryFile> libraryFiles, List<BookEntity> books) {
        Set<Path> currentFullPaths = libraryFiles.stream()
                .map(LibraryFile::getFullPath)
                .collect(Collectors.toSet());

        return books.stream()
                .filter(book -> (book.getDeleted() == null || !book.getDeleted()))
                .filter(book -> {
                    // Don't mark fileless books as deleted - they're intentionally without files
                    if (!book.hasFiles()) {
                        return false;
                    }
                    return !currentFullPaths.contains(book.getFullFilePath());
                })
                .map(BookEntity::getId)
                .collect(Collectors.toList());
    }

    public List<Long> detectDeletedAdditionalFiles(List<LibraryFile> libraryFiles, List<BookFileEntity> allAdditionalFiles) {
        Set<Path> currentFileKeys = libraryFiles.stream()
                .map(LibraryFile::getFullPath)
                .collect(Collectors.toSet());

        return allAdditionalFiles.stream()
                .filter(BookFileEntity::isBookFormat)
                .filter(additionalFile -> !currentFileKeys.contains(additionalFile.getFullFilePath()))
                .map(BookFileEntity::getId)
                .collect(Collectors.toList());
    }

    public List<LibraryFile> detectNewBookPaths(List<LibraryFile> libraryFiles, List<BookEntity> books, List<BookFileEntity> allAdditionalFiles) {
        Set<Path> existingFilePaths = books.stream()
                .map(BookEntity::getPrimaryBookFile)
                .filter(Objects::nonNull)
                .filter(b -> !b.isFolderBased())
                .map(BookFileEntity::getFullFilePath)
                .collect(Collectors.toSet());

        existingFilePaths.addAll(
                allAdditionalFiles.stream()
                        .filter(b -> !b.isFolderBased())
                        .map(BookFileEntity::getFullFilePath)
                        .collect(Collectors.toSet())
        );

        Set<Path> existingFolderPaths = books.stream()
                .map(BookEntity::getPrimaryBookFile)
                .filter(Objects::nonNull)
                .filter(BookFileEntity::isFolderBased)
                .map(BookFileEntity::getFullFilePath)
                .collect(Collectors.toSet());

        existingFolderPaths.addAll(
                allAdditionalFiles.stream()
                        .filter(BookFileEntity::isFolderBased)
                        .map(BookFileEntity::getFullFilePath)
                        .collect(Collectors.toSet())
        );

        return libraryFiles.stream()
                .filter(file -> {
                    Path filePath = file.getFullPath();

                    if (file.isFolderBased()) {
                        // If there's an exact match for existing folder paths bail early.
                        if (existingFolderPaths.contains(filePath)) {
                            return false;
                        }

                        // Next check if there are any book existing files which are
                        // contained by this folder.

                        // Next check if there are any book folders which contain this file.
                        for (Path existingFile : existingFilePaths) {
                            if (existingFile.startsWith(filePath)) {
                                return false;
                            }
                        }

                        // If none of that matches then this is a new folder.
                        return true;
                    }

                    // If there's an exact match for existing file paths bail early.
                    if (existingFilePaths.contains(filePath)) {
                        return false;
                    }

                    // Next check if there are any book folders which contain this file.
                    for (Path existingFolder : existingFolderPaths) {
                        if (filePath.startsWith(existingFolder)) {
                            return false;
                        }
                    }

                    // If none of that matches then this is a new file.
                    return true;
                })
                .collect(Collectors.toList());
    }

    public List<LibraryFile> getAllLibraryFiles(LibraryEntity libraryEntity) throws IOException {
        List<LibraryFile> allFiles = new ArrayList<>();
        for (LibraryPathEntity pathEntity : libraryEntity.getLibraryPaths()) {
            allFiles.addAll(findLibraryFiles(pathEntity, libraryEntity));
        }
        return allFiles;
    }

    public List<LibraryFile> getLibraryFiles(LibraryEntity libraryEntity) throws IOException {
        LibraryOrganizationMode mode = libraryEntity.getOrganizationMode() != null
                ? libraryEntity.getOrganizationMode() : LibraryOrganizationMode.AUTO_DETECT;

        List<LibraryFile> allFiles = switch (mode) {
            case BOOK_PER_FILE, BOOK_PER_FOLDER -> getAllLibraryFilesFlat(libraryEntity);
            case AUTO_DETECT -> getAllLibraryFiles(libraryEntity);
        };
        return filterByAllowedFormats(allFiles, libraryEntity.getAllowedFormats());
    }

    public List<LibraryFile> getAllLibraryFilesFlat(LibraryEntity libraryEntity) throws IOException {
        List<LibraryFile> allFiles = new ArrayList<>();
        for (LibraryPathEntity pathEntity : libraryEntity.getLibraryPaths()) {
            allFiles.addAll(findLibraryFilesFlat(pathEntity, libraryEntity));
        }
        return allFiles;
    }

    private List<LibraryFile> findLibraryFilesFlat(LibraryPathEntity pathEntity, LibraryEntity libraryEntity) throws IOException {
        Path libraryPath = Path.of(pathEntity.getPath());
        List<LibraryFile> libraryFiles = new ArrayList<>();
        Map<Path, List<Path>> dirAudioFiles = new HashMap<>();

        Files.walkFileTree(libraryPath, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
            @Override
            @NonNull
            public FileVisitResult visitFile(@NonNull Path file, @NonNull BasicFileAttributes attrs) {
                if (FileUtils.shouldIgnore(file) || !Files.isReadable(file) || !Files.isRegularFile(file) || attrs.size() == 0) {
                    return FileVisitResult.CONTINUE;
                }

                String fileName = file.getFileName().toString();
                Optional<BookFileExtension> bookExtension = BookFileExtension.fromFileName(fileName);

                if (bookExtension.isEmpty()) {
                    return FileVisitResult.CONTINUE;
                }

                if (bookExtension.get().getType() == BookFileType.AUDIOBOOK) {
                    dirAudioFiles.computeIfAbsent(file.getParent(), k -> new ArrayList<>()).add(file);
                } else {
                    libraryFiles.add(LibraryFile.builder()
                            .libraryEntity(libraryEntity)
                            .libraryPathEntity(pathEntity)
                            .fileSubPath(FileUtils.getRelativeSubPath(pathEntity.getPath(), file))
                            .fileName(fileName)
                            .bookFileType(bookExtension.get().getType())
                            .build());
                }

                return FileVisitResult.CONTINUE;
            }

            @Override
            @NonNull
            public FileVisitResult visitFileFailed(@NonNull Path file, IOException e) {
                log.error("Failed read path [{}]: {}", file, e.getMessage(), e);
                return FileVisitResult.CONTINUE;
            }

            @Override
            @NonNull
            public FileVisitResult preVisitDirectory(@NonNull Path dir, @NonNull BasicFileAttributes attrs) throws IOException {
                if (FileUtils.shouldIgnore(dir) || !Files.isReadable(dir)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                if (!dir.equals(libraryPath) && Files.exists(dir.resolve(".ignore"))) {
                    log.debug("Skipping directory with .ignore file: {}", dir);
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return super.preVisitDirectory(dir, attrs);
            }
        });

        // Collapse audio files: 2+ audio files in same directory become a single folder-based audiobook.
        // A single audio file (e.g. standalone m4b) stays as an individual file entry.
        // Audio files at the library root are always added individually (no folder to name the audiobook after).
        for (var entry : dirAudioFiles.entrySet()) {
            Path dir = entry.getKey();
            List<Path> audioFiles = entry.getValue();

            if (dir.equals(libraryPath) || audioFiles.size() < MIN_AUDIO_FILES_FOR_FOLDER_AUDIOBOOK) {
                for (Path audioFile : audioFiles) {
                    libraryFiles.add(LibraryFile.builder()
                            .libraryEntity(libraryEntity)
                            .libraryPathEntity(pathEntity)
                            .fileSubPath(FileUtils.getRelativeSubPath(pathEntity.getPath(), audioFile))
                            .fileName(audioFile.getFileName().toString())
                            .bookFileType(BookFileType.AUDIOBOOK)
                            .build());
                }
            } else {
                libraryFiles.add(LibraryFile.builder()
                        .libraryEntity(libraryEntity)
                        .libraryPathEntity(pathEntity)
                        .fileSubPath(FileUtils.getRelativeSubPath(pathEntity.getPath(), dir))
                        .fileName(dir.getFileName().toString())
                        .bookFileType(BookFileType.AUDIOBOOK)
                        .folderBased(true)
                        .build());
            }
        }

        return libraryFiles;
    }

    List<LibraryFile> filterByAllowedFormats(List<LibraryFile> files, List<BookFileType> allowedFormats) {
        if (allowedFormats == null || allowedFormats.isEmpty()) {
            return files;
        }
        Set<BookFileType> allowed = EnumSet.copyOf(allowedFormats);
        return files.stream()
                .filter(file -> allowed.contains(file.getBookFileType()))
                .collect(java.util.stream.Collectors.toList());
    }

    private List<LibraryFile> findLibraryFiles(LibraryPathEntity pathEntity, LibraryEntity libraryEntity) throws IOException {
        Path libraryPath = Path.of(pathEntity.getPath());
        List<LibraryFile> libraryFiles = new ArrayList<>();

        // Track directories that contain audio files for folder-based audiobook detection
        Map<Path, List<Path>> dirAudioFiles = new HashMap<>();
        Map<Path, Boolean> dirHasNonAudioBooks = new HashMap<>();

        Files.walkFileTree(libraryPath, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
            @Override
            @NonNull
            public FileVisitResult visitFile(@NonNull Path file, @NonNull BasicFileAttributes attrs) {
                if (FileUtils.shouldIgnore(file) || !Files.isReadable(file) || !Files.isRegularFile(file) || attrs.size() == 0) {
                    return FileVisitResult.CONTINUE;
                }

                String fileName = file.getFileName().toString();
                Optional<BookFileExtension> bookExtension = BookFileExtension.fromFileName(fileName);

                if (bookExtension.isEmpty()) {
                    return FileVisitResult.CONTINUE;
                }

                Path parentDir = file.getParent();
                BookFileType fileType = bookExtension.get().getType();

                if (fileType == BookFileType.AUDIOBOOK) {
                    // Track audio files per directory for folder-based detection
                    dirAudioFiles.computeIfAbsent(parentDir, k -> new ArrayList<>()).add(file);
                } else {
                    // Track that this directory has non-audio book files
                    dirHasNonAudioBooks.put(parentDir, true);
                }

                libraryFiles.add(LibraryFile.builder()
                        .libraryEntity(libraryEntity)
                        .libraryPathEntity(pathEntity)
                        .fileSubPath(FileUtils.getRelativeSubPath(pathEntity.getPath(), file))
                        .fileName(fileName)
                        .bookFileType(fileType)
                        .build());

                return FileVisitResult.CONTINUE;
            }

            @Override
            @NonNull
            public FileVisitResult postVisitDirectory(@NonNull Path dir, IOException exc) throws IOException {
                // Check if this directory should be treated as a folder-based audiobook
                List<Path> audioFiles = dirAudioFiles.get(dir);
                boolean hasNonAudioBooks = dirHasNonAudioBooks.getOrDefault(dir, false);

                if (audioFiles == null) {
                    return FileVisitResult.CONTINUE;
                }

                if (audioFiles.size() >= MIN_AUDIO_FILES_FOR_FOLDER_AUDIOBOOK && !hasNonAudioBooks) {
                    // Don't treat library root as audiobook folder
                    if (!dir.equals(libraryPath)) {
                        if (FileUtils.isSeriesFolder(audioFiles)) {
                            log.info("Detected series folder: {} ({} audio files with distinct titles)", dir.getFileName(), audioFiles.size());
                        } else {
                            log.info("Detected folder-based audiobook: {} ({} audio files)", dir.getFileName(), audioFiles.size());

                            libraryFiles.add(LibraryFile.builder()
                                    .libraryEntity(libraryEntity)
                                    .libraryPathEntity(pathEntity)
                                    .fileSubPath(FileUtils.getRelativeSubPath(pathEntity.getPath(), dir))
                                    .fileName(dir.getFileName().toString())
                                    .bookFileType(BookFileType.AUDIOBOOK)
                                    .folderBased(true)
                                    .build());
                        }
                    }
                }

                return FileVisitResult.CONTINUE;
            }

            @Override
            @NonNull
            public FileVisitResult visitFileFailed(@NonNull Path file, IOException e) {
                log.error("Failed read path [{}]: {}", file, e.getMessage(), e);
                return FileVisitResult.CONTINUE;
            }

            @Override
            @NonNull
            public FileVisitResult preVisitDirectory(@NonNull Path dir, @NonNull BasicFileAttributes attrs) throws IOException {
                if (FileUtils.shouldIgnore(dir) || !Files.isReadable(dir)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                if (!dir.equals(libraryPath) && Files.exists(dir.resolve(".ignore"))) {
                    log.debug("Skipping directory with .ignore file: {}", dir);
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return super.preVisitDirectory(dir, attrs);
            }
        });
        return libraryFiles;
    }
}
