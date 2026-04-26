package org.grimmory.service.library;

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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
        Set<String> currentFileKeys = libraryFiles.stream()
                .map(this::generateUniqueKey)
                .collect(Collectors.toSet());

        return allAdditionalFiles.stream()
                .filter(BookFileEntity::isBookFormat)
                .filter(additionalFile -> !currentFileKeys.contains(generateUniqueKey(additionalFile)))
                .map(BookFileEntity::getId)
                .collect(Collectors.toList());
    }

    public List<LibraryFile> detectNewBookPaths(List<LibraryFile> libraryFiles, List<BookEntity> books, List<BookFileEntity> allAdditionalFiles) {
        Set<String> existingKeys = books.stream()
                .filter(book -> book.getBookFiles() != null && !book.getBookFiles().isEmpty())
                .map(this::generateUniqueKey)
                .collect(Collectors.toSet());

        Set<String> additionalFileKeys = allAdditionalFiles.stream()
                .map(this::generateUniqueKey)
                .collect(Collectors.toSet());

        existingKeys.addAll(additionalFileKeys);

        return libraryFiles.stream()
                .filter(file -> !existingKeys.contains(generateUniqueKey(file)))
                .collect(Collectors.toList());
    }

    private String generateUniqueKey(BookEntity book) {
        BookFileEntity primaryFile = book.getPrimaryBookFile();
        if (primaryFile == null) {
            // Fileless book - use a unique key that won't match any file
            return "fileless:" + book.getId();
        }
        return generateKey(book.getLibraryPath().getId(), primaryFile.getFileSubPath(), primaryFile.getFileName());
    }

    private String generateUniqueKey(BookFileEntity file) {
        return generateKey(file.getBook().getLibraryPath().getId(), file.getFileSubPath(), file.getFileName());
    }

    private String generateUniqueKey(LibraryFile file) {
        return generateKey(file.getLibraryPathEntity().getId(), file.getFileSubPath(), file.getFileName());
    }

    private String generateKey(Long libraryPathId, String subPath, String fileName) {
        String safeSubPath = (subPath == null) ? "" : subPath;
        return libraryPathId + ":" + safeSubPath + ":" + fileName;
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
        Set<Path> processedAsFolderAudiobook = new HashSet<>();

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

                    // Add non-audio files immediately
                    libraryFiles.add(LibraryFile.builder()
                            .libraryEntity(libraryEntity)
                            .libraryPathEntity(pathEntity)
                            .fileSubPath(FileUtils.getRelativeSubPath(pathEntity.getPath(), file))
                            .fileName(fileName)
                            .bookFileType(fileType)
                            .build());
                }

                return FileVisitResult.CONTINUE;
            }

            @Override
            @NonNull
            public FileVisitResult postVisitDirectory(@NonNull Path dir, IOException exc) throws IOException {
                // Check if this directory should be treated as a folder-based audiobook
                List<Path> audioFiles = dirAudioFiles.get(dir);
                boolean hasNonAudioBooks = dirHasNonAudioBooks.getOrDefault(dir, false);

                if (audioFiles != null && audioFiles.size() >= MIN_AUDIO_FILES_FOR_FOLDER_AUDIOBOOK && !hasNonAudioBooks) {
                    // Don't treat library root as audiobook folder
                    if (!dir.equals(libraryPath)) {
                        if (FileUtils.isSeriesFolder(audioFiles)) {
                            log.info("Detected series folder: {} ({} audio files with distinct titles)", dir.getFileName(), audioFiles.size());
                            addIndividualAudioFiles(audioFiles, libraryEntity, pathEntity, libraryFiles);
                        } else {
                            log.info("Detected folder-based audiobook: {} ({} audio files)", dir.getFileName(), audioFiles.size());

                            processedAsFolderAudiobook.add(dir);

                            libraryFiles.add(LibraryFile.builder()
                                    .libraryEntity(libraryEntity)
                                    .libraryPathEntity(pathEntity)
                                    .fileSubPath(FileUtils.getRelativeSubPath(pathEntity.getPath(), dir))
                                    .fileName(dir.getFileName().toString())
                                    .bookFileType(BookFileType.AUDIOBOOK)
                                    .folderBased(true)
                                    .build());
                        }
                    } else {
                        // Library root - add individual audio files
                        addIndividualAudioFiles(audioFiles, libraryEntity, pathEntity, libraryFiles);
                    }
                } else if (audioFiles != null) {
                    // Not a folder-based audiobook - add individual audio files
                    // But skip if parent was already processed as folder-based audiobook
                    boolean parentIsAudiobookFolder = false;
                    Path parent = dir.getParent();
                    while (parent != null && parent.startsWith(libraryPath)) {
                        if (processedAsFolderAudiobook.contains(parent)) {
                            parentIsAudiobookFolder = true;
                            break;
                        }
                        parent = parent.getParent();
                    }

                    if (!parentIsAudiobookFolder) {
                        addIndividualAudioFiles(audioFiles, libraryEntity, pathEntity, libraryFiles);
                    }
                }

                return FileVisitResult.CONTINUE;
            }

            private void addIndividualAudioFiles(List<Path> audioFiles, LibraryEntity libraryEntity,
                    LibraryPathEntity pathEntity, List<LibraryFile> libraryFiles) {
                for (Path audioFile : audioFiles) {
                    String fileName = audioFile.getFileName().toString();
                    Optional<BookFileExtension> ext = BookFileExtension.fromFileName(fileName);

                    libraryFiles.add(LibraryFile.builder()
                            .libraryEntity(libraryEntity)
                            .libraryPathEntity(pathEntity)
                            .fileSubPath(FileUtils.getRelativeSubPath(pathEntity.getPath(), audioFile))
                            .fileName(fileName)
                            .bookFileType(ext.map(BookFileExtension::getType).orElse(BookFileType.AUDIOBOOK))
                            .build());
                }
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
