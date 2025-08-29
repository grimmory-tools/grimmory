package com.adityachandel.booklore.service.library;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.BookAdditionalFileEntity;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.enums.AdditionalFileType;
import com.adityachandel.booklore.model.enums.BookFileExtension;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.BookAdditionalFileRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.util.FileService;
import com.adityachandel.booklore.util.FileUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.adityachandel.booklore.model.websocket.LogNotification.createLogNotification;

@AllArgsConstructor
@Service
@Slf4j
public class LibraryProcessingService {

    private final LibraryRepository libraryRepository;
    private final NotificationService notificationService;
    private final BookRepository bookRepository;
    private final BookAdditionalFileRepository bookAdditionalFileRepository;
    private final FileService fileService;
    private final BookMapper bookMapper;
    private final LibraryFileProcessorRegistry fileProcessorRegistry;

    @Transactional
    public void processLibrary(long libraryId) throws IOException {
        LibraryEntity libraryEntity = libraryRepository.findById(libraryId).orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(libraryId));
        notificationService.sendMessage(Topic.LOG, createLogNotification("Started processing library: " + libraryEntity.getName()));
        LibraryFileProcessor processor = fileProcessorRegistry.getProcessor(libraryEntity);
        List<LibraryFile> libraryFiles = getLibraryFiles(libraryEntity, processor);
        processor.processLibraryFiles(libraryFiles, libraryEntity);
        notificationService.sendMessage(Topic.LOG, createLogNotification("Finished processing library: " + libraryEntity.getName()));
    }

    @Transactional
    public void rescanLibrary(long libraryId) throws IOException {
        LibraryEntity libraryEntity = libraryRepository.findById(libraryId).orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(libraryId));
        notificationService.sendMessage(Topic.LOG, createLogNotification("Started refreshing library: " + libraryEntity.getName()));
        LibraryFileProcessor processor = fileProcessorRegistry.getProcessor(libraryEntity);
        List<LibraryFile> libraryFiles = getLibraryFiles(libraryEntity, processor);
        List<Long> additionalFileIds = detectDeletedAdditionalFiles(libraryFiles, libraryEntity);
        if (!additionalFileIds.isEmpty()) {
            log.info("Detected {} removed additional files in library: {}", additionalFileIds.size(), libraryEntity.getName());
            deleteRemovedAdditionalFiles(additionalFileIds);
        }
        List<Long> bookIds = detectDeletedBookIds(libraryFiles, libraryEntity);
        if (!bookIds.isEmpty()) {
            log.info("Detected {} removed books in library: {}", bookIds.size(), libraryEntity.getName());
            processDeletedLibraryFiles(bookIds, libraryFiles);
        }
        restoreDeletedBooks(libraryFiles);
        processor.processLibraryFiles(detectNewBookPaths(libraryFiles, libraryEntity), libraryEntity);
        notificationService.sendMessage(Topic.LOG, createLogNotification("Finished refreshing library: " + libraryEntity.getName()));
    }

    private void restoreDeletedBooks(List<LibraryFile> libraryFiles) {
        if (libraryFiles.isEmpty()) return;

        LibraryEntity libraryEntity = libraryFiles.get(0).getLibraryEntity();
        Set<Path> currentPaths = libraryFiles.stream()
                .map(LibraryFile::getFullPath)
                .collect(Collectors.toSet());

        List<BookEntity> toRestore = libraryEntity.getBookEntities().stream()
                .filter(book -> Boolean.TRUE.equals(book.getDeleted()))
                .filter(book -> currentPaths.contains(book.getFullFilePath()))
                .collect(Collectors.toList());

        if (toRestore.isEmpty()) return;

        toRestore.forEach(book -> {
            book.setDeleted(false);
            book.setDeletedAt(null);
            book.setAddedOn(Instant.now());
            notificationService.sendMessage(Topic.BOOK_ADD, bookMapper.toBookWithDescription(book, false));
        });
        bookRepository.saveAll(toRestore);

        List<Long> restoredIds = toRestore.stream()
                .map(BookEntity::getId)
                .toList();

        log.info("Restored {} books in library: {}", restoredIds.size(), libraryEntity.getName());
    }

    public void processLibraryFiles(List<LibraryFile> libraryFiles, LibraryEntity libraryEntity) {
        LibraryFileProcessor processor = fileProcessorRegistry.getProcessor(libraryEntity);
        processor.processLibraryFiles(libraryFiles, libraryEntity);
    }

    protected static List<Long> detectDeletedBookIds(List<LibraryFile> libraryFiles, LibraryEntity libraryEntity) {
        Set<Path> currentFullPaths = libraryFiles.stream()
                .map(LibraryFile::getFullPath)
                .collect(Collectors.toSet());

        return libraryEntity.getBookEntities().stream()
                .filter(book -> (book.getDeleted() == null || !book.getDeleted()))
                .filter(book -> !currentFullPaths.contains(book.getFullFilePath()))
                .map(BookEntity::getId)
                .collect(Collectors.toList());
    }

    protected List<LibraryFile> detectNewBookPaths(List<LibraryFile> libraryFiles, LibraryEntity libraryEntity) {
        Set<Path> existingFullPaths = libraryEntity.getBookEntities().stream()
                .map(BookEntity::getFullFilePath)
                .collect(Collectors.toSet());

        // Also collect paths from additional files using repository method
        Set<Path> additionalFilePaths = bookAdditionalFileRepository.findByLibraryId(libraryEntity.getId()).stream()
                .map(BookAdditionalFileEntity::getFullFilePath)
                .collect(Collectors.toSet());

        // Combine both sets of existing paths
        existingFullPaths.addAll(additionalFilePaths);

        return libraryFiles.stream()
                .filter(file -> !existingFullPaths.contains(file.getFullPath()))
                .collect(Collectors.toList());
    }

    protected List<Long> detectDeletedAdditionalFiles(List<LibraryFile> libraryFiles, LibraryEntity libraryEntity) {
        // Create a set of current file names for quick lookup
        Set<String> currentFileNames = libraryFiles.stream()
                .map(LibraryFile::getFileName)
                .collect(Collectors.toSet());

        // Get all additional files from the library
        List<BookAdditionalFileEntity> allAdditionalFiles = bookAdditionalFileRepository.findByLibraryId(libraryEntity.getId());

        // Find additional files that no longer exist in the file system
        return allAdditionalFiles.stream()
                .filter(additionalFile -> !currentFileNames.contains(additionalFile.getFileName()))
                .map(BookAdditionalFileEntity::getId)
                .collect(Collectors.toList());
    }

    @Transactional
    protected void deleteRemovedAdditionalFiles(List<Long> additionalFileIds) {
        if (additionalFileIds.isEmpty()) {
            return;
        }

        List<BookAdditionalFileEntity> additionalFiles = bookAdditionalFileRepository.findAllById(additionalFileIds);
        bookAdditionalFileRepository.deleteAll(additionalFiles);

        log.info("Deleted {} additional files from database", additionalFileIds.size());
    }

    @Transactional
    protected void processDeletedLibraryFiles(List<Long> deletedBookIds, List<LibraryFile> libraryFiles) {
        if (deletedBookIds.isEmpty()) {
            return;
        }

        List<BookEntity> books = bookRepository.findAllById(deletedBookIds);
        List<Long> booksToDelete = new ArrayList<>();

        for (BookEntity book : books) {
            if (!tryPromoteAlternativeFormatToBook(book, libraryFiles)) {
                booksToDelete.add(book.getId());
            }
        }

        if (!booksToDelete.isEmpty()) {
            deleteRemovedBooks(booksToDelete);
        }
    }

    protected boolean tryPromoteAlternativeFormatToBook(BookEntity book, List<LibraryFile> libraryFiles) {
        // Find existing alternative formats for this book
        List<BookAdditionalFileEntity> existingAlternativeFormats = findExistingAlternativeFormats(book, libraryFiles);

        if (existingAlternativeFormats.isEmpty()) {
            return false; // No alternative formats to promote
        }

        // Promote the first alternative format to become the main book
        BookAdditionalFileEntity promotedFormat = existingAlternativeFormats.getFirst();
        promoteAlternativeFormatToBook(book, promotedFormat);

        // Remove the promoted format from additional files
        bookAdditionalFileRepository.delete(promotedFormat);

        log.info("Promoted alternative format {} to main book for book ID {}", promotedFormat.getFileName(), book.getId());
        return true;
    }

    private List<BookAdditionalFileEntity> findExistingAlternativeFormats(BookEntity book, List<LibraryFile> libraryFiles) {
        Set<String> currentFileNames = libraryFiles.stream()
                .map(LibraryFile::getFileName)
                .collect(Collectors.toSet());

        if (book.getAdditionalFiles() == null) {
            return Collections.emptyList();
        }

        return book.getAdditionalFiles().stream()
                .filter(additionalFile -> AdditionalFileType.ALTERNATIVE_FORMAT.equals(additionalFile.getAdditionalFileType()))
                .filter(additionalFile -> currentFileNames.contains(additionalFile.getFileName()))
                .filter(additionalFile -> BookFileExtension.fromFileName(additionalFile.getFileName()).isPresent())
                .collect(Collectors.toList());
    }

    private void promoteAlternativeFormatToBook(BookEntity book, BookAdditionalFileEntity alternativeFormat) {
        book.setFileName(alternativeFormat.getFileName());
        book.setFileSubPath(alternativeFormat.getFileSubPath());
        BookFileExtension.fromFileName(alternativeFormat.getFileName())
                .ifPresent(ext -> book.setBookType(ext.getType()));

        book.setFileSizeKb(alternativeFormat.getFileSizeKb());
        book.setCurrentHash(alternativeFormat.getCurrentHash());
        book.setInitialHash(alternativeFormat.getInitialHash());

        bookRepository.save(book);
    }

    @Transactional
    protected void deleteRemovedBooks(List<Long> bookIds) {
        List<BookEntity> books = bookRepository.findAllById(bookIds);
        for (BookEntity book : books) {
            try {
                deleteDirectoryRecursively(Path.of(fileService.getImagesFolder(book.getId())));
                Path backupDir = Path.of(fileService.getBookMetadataBackupPath(book.getId()));
                if (Files.exists(backupDir)) {
                    deleteDirectoryRecursively(backupDir);
                }
            } catch (Exception e) {
                log.warn("Failed to clean up files for book ID {}: {}", book.getId(), e.getMessage());
            }
        }
        bookRepository.deleteAll(books);
        notificationService.sendMessage(Topic.BOOKS_REMOVE, bookIds);
        if (bookIds.size() > 1) log.info("Books removed: {}", bookIds);
    }

    private void deleteDirectoryRecursively(Path path) throws IOException {
        if (Files.exists(path)) {
            try (Stream<Path> walk = Files.walk(path)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        log.warn("Failed to delete file or directory: {}", p, e);
                    }
                });
            }
        }
    }


    private List<LibraryFile> getLibraryFiles(LibraryEntity libraryEntity, LibraryFileProcessor processor) throws IOException {
        List<LibraryFile> allFiles = new ArrayList<>();
        for (LibraryPathEntity pathEntity : libraryEntity.getLibraryPaths()) {
            allFiles.addAll(findLibraryFiles(pathEntity, libraryEntity, processor));
        }
        return allFiles;
    }

    private List<LibraryFile> findLibraryFiles(LibraryPathEntity pathEntity, LibraryEntity libraryEntity, LibraryFileProcessor processor) throws IOException {
        Path libraryPath = Path.of(pathEntity.getPath());
        boolean supportsSupplementaryFiles = processor.supportsSupplementaryFiles();

        try (Stream<Path> stream = Files.walk(libraryPath, FileVisitOption.FOLLOW_LINKS)) {
            return stream.filter(Files::isRegularFile)
                    .map(fullPath -> {
                        String fileName = fullPath.getFileName().toString();
                        Optional<BookFileExtension> bookExtension = BookFileExtension.fromFileName(fileName);

                        if (bookExtension.isEmpty() && !supportsSupplementaryFiles) {
                            // Skip files that are not recognized book files and supplementary files are not supported
                            return null;
                        }

                        return LibraryFile.builder()
                                .libraryEntity(libraryEntity)
                                .libraryPathEntity(pathEntity)
                                .fileSubPath(FileUtils.getRelativeSubPath(pathEntity.getPath(), fullPath))
                                .fileName(fileName)
                                .bookFileType(bookExtension.map(BookFileExtension::getType).orElse(null))
                                .build();
                    })
                    .filter(Objects::nonNull)
                    .filter(file -> !file.getFileName().startsWith("."))
                    .toList();
        }
    }
}
