package com.adityachandel.booklore.service.library;

import com.adityachandel.booklore.model.FileProcessResult;
import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.BookAdditionalFileEntity;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.enums.AdditionalFileType;
import com.adityachandel.booklore.model.enums.BookFileExtension;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.enums.LibraryScanMode;
import com.adityachandel.booklore.repository.BookAdditionalFileRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.FileFingerprint;
import com.adityachandel.booklore.service.event.AdminEventBroadcaster;
import com.adityachandel.booklore.service.event.BookEventBroadcaster;
import com.adityachandel.booklore.service.fileprocessor.BookFileProcessor;
import com.adityachandel.booklore.service.fileprocessor.BookFileProcessorRegistry;
import com.adityachandel.booklore.util.FileUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
@Slf4j
public class FolderAsBookFileProcessor implements LibraryFileProcessor {

    private final BookRepository bookRepository;
    private final BookAdditionalFileRepository bookAdditionalFileRepository;
    private final BookEventBroadcaster bookEventBroadcaster;
    private final AdminEventBroadcaster adminEventBroadcaster;
    private final BookFileProcessorRegistry bookFileProcessorRegistry;

    @Override
    public LibraryScanMode getScanMode() {
        return LibraryScanMode.FOLDER_AS_BOOK;
    }

    @Override
    public boolean supportsSupplementaryFiles() {
        return true;
    }

    @Override
    public void processLibraryFiles(List<LibraryFile> libraryFiles, LibraryEntity libraryEntity) {
        Map<Path, List<LibraryFile>> filesByDirectory = libraryFiles.stream()
                .collect(Collectors.groupingBy(libraryFile -> libraryFile.getFullPath().getParent()));

        log.info("Processing {} directories with {} total files for library: {}",
                filesByDirectory.size(), libraryFiles.size(), libraryEntity.getName());

        var sortedDirectories = filesByDirectory.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();
        for (Map.Entry<Path, List<LibraryFile>> entry : sortedDirectories) {
            Path directoryPath = entry.getKey();
            List<LibraryFile> filesInDirectory = entry.getValue();

            log.debug("Processing directory: {} with {} files", directoryPath, filesInDirectory.size());
            processDirectory(directoryPath, filesInDirectory, libraryEntity);
        }
    }

    private void processDirectory(Path directoryPath, List<LibraryFile> filesInDirectory, LibraryEntity libraryEntity) {
        var bookCreationResult = getOrCreateBookInDirectory(directoryPath, filesInDirectory, libraryEntity);
        if (bookCreationResult.bookEntity.isEmpty()) {
            log.warn("No book created for directory: {}", directoryPath);
            return;
        }

        processAdditionalFiles(bookCreationResult.bookEntity.get(), bookCreationResult.remainingFiles);
    }

    private GetOrCreateBookResult getOrCreateBookInDirectory(Path directoryPath, List<LibraryFile> filesInDirectory, LibraryEntity libraryEntity) {
        var existingBook = findExistingBookInDirectory(directoryPath, libraryEntity);
        if (existingBook.isPresent()) {
            log.debug("Found existing book in directory {}: {}", directoryPath, existingBook.get().getFileName());
            return new GetOrCreateBookResult(existingBook, filesInDirectory);
        }

        Optional<BookEntity> parentBook = findBookInParentDirectories(directoryPath, libraryEntity);
        if (parentBook.isPresent()) {
            log.debug("Found parent book for directory {}: {}", directoryPath, parentBook.get().getFileName());
            return new GetOrCreateBookResult(parentBook, filesInDirectory);
        }

        log.debug("No existing book found, creating new book from directory: {}", directoryPath);
        Optional<CreateBookResult> newBook = createNewBookFromDirectory(directoryPath, filesInDirectory, libraryEntity);
        if (newBook.isPresent()) {
            log.info("Created new book: {}", newBook.get().bookEntity.getFileName());
            var remainingFiles = filesInDirectory.stream()
                    .filter(file -> !file.equals(newBook.get().libraryFile))
                    .toList();
            return new GetOrCreateBookResult(Optional.of(newBook.get().bookEntity), remainingFiles);
        } else {
            log.warn("Failed to create book from directory: {}", directoryPath);
            return new GetOrCreateBookResult(Optional.empty(), filesInDirectory);
        }
    }

    private Optional<BookEntity> findExistingBookInDirectory(Path directoryPath, LibraryEntity libraryEntity) {
        return libraryEntity.getLibraryPaths().stream()
                .flatMap(libPath -> {
                    String filesSearchPath = Path.of(libPath.getPath())
                            .relativize(directoryPath)
                            .toString()
                            .replace("\\", "/");
                    return bookRepository
                            .findAllByLibraryPathIdAndFileSubPathStartingWith(libPath.getId(), filesSearchPath)
                            .stream();
                })
                .filter(book -> book.getFullFilePath().getParent().equals(directoryPath))
                .findFirst();
    }

    private Optional<BookEntity> findBookInParentDirectories(Path directoryPath, LibraryEntity libraryEntity) {
        Path parent = directoryPath.getParent();
        LibraryPathEntity directoryLibraryPathEntity = libraryEntity.getLibraryPaths().stream()
                .filter(libPath -> directoryPath.startsWith(libPath.getPath()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No library path found for directory: " + directoryPath));
        Path directoryLibraryPath = Path.of(directoryLibraryPathEntity.getPath());

        while (parent != null) {
            final String parentPath = directoryLibraryPath
                    .relativize(parent)
                    .toString()
                    .replace("\\", "/");

            Optional<BookEntity> parentBook =
                    bookRepository.findAllByLibraryPathIdAndFileSubPathStartingWith(
                                    directoryLibraryPathEntity.getId(), parentPath).stream()
                            .filter(book -> book.getFileSubPath().equals(parentPath))
                            .findFirst();
            if (parentBook.isPresent()) {
                return parentBook;
            }
            parent = parent.getParent();
        }

        return Optional.empty();
    }

    private Optional<CreateBookResult> createNewBookFromDirectory(Path directoryPath, List<LibraryFile> filesInDirectory, LibraryEntity libraryEntity) {
        Optional<LibraryFile> mainBookFile = findBestMainBookFile(filesInDirectory, libraryEntity);

        if (mainBookFile.isEmpty()) {
            log.debug("No suitable book file found in directory: {}", directoryPath);
            return Optional.empty();
        }

        LibraryFile bookFile = mainBookFile.get();

        try {
            log.info("Creating new book from file: {}", bookFile.getFileName());

            BookFileProcessor processor = bookFileProcessorRegistry.getProcessorOrThrow(bookFile.getBookFileType());
            FileProcessResult result = processor.processFile(bookFile);

            if (result.getBook() != null) {
                bookEventBroadcaster.broadcastBookAddEvent(result.getBook());

                BookEntity bookEntity = bookRepository.getReferenceById(result.getBook().getId());
                if (bookEntity.getFullFilePath().equals(bookFile.getFullPath())) {
                    log.info("Successfully created new book: {}", bookEntity.getFileName());
                } else {
                    log.warn("Found duplicate book with different path: {} vs {}", bookEntity.getFullFilePath(), bookFile.getFullPath());
                }

                return Optional.of(new CreateBookResult(bookEntity, bookFile));
            } else {
                log.warn("Book processor returned null for file: {}", bookFile.getFileName());
                adminEventBroadcaster.broadcastAdminEvent("Failed to create book from file: " + bookFile.getFileName());
                return Optional.empty();
            }
        } catch (Exception e) {
            log.error("Error processing book file {}: {}", bookFile.getFileName(), e.getMessage(), e);
            adminEventBroadcaster.broadcastAdminEvent("Error processing book file: " + bookFile.getFileName() + " - " + e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<LibraryFile> findBestMainBookFile(List<LibraryFile> filesInDirectory, LibraryEntity libraryEntity) {
        var defaultBookFormat = libraryEntity.getDefaultBookFormat();
        return filesInDirectory.stream()
                .filter(f -> f.getBookFileType() != null)
                .min(Comparator.comparingInt(f -> {
                    BookFileType bookFileType = f.getBookFileType();
                    return bookFileType == defaultBookFormat
                            ? -1
                            : bookFileType.ordinal();
                }));
    }

    private void processAdditionalFiles(BookEntity existingBook, List<LibraryFile> filesInDirectory) {
        for (LibraryFile file : filesInDirectory) {
            Optional<BookFileExtension> extension = BookFileExtension.fromFileName(file.getFileName());
            AdditionalFileType fileType = extension.isPresent() ?
                    AdditionalFileType.ALTERNATIVE_FORMAT : AdditionalFileType.SUPPLEMENTARY;

            createAdditionalFileIfNotExists(existingBook, file, fileType);
        }
    }

    private void createAdditionalFileIfNotExists(BookEntity bookEntity, LibraryFile file, AdditionalFileType fileType) {
        Optional<BookAdditionalFileEntity> existingFile = bookAdditionalFileRepository
                .findByLibraryPath_IdAndFileSubPathAndFileName(
                        file.getLibraryPathEntity().getId(), file.getFileSubPath(), file.getFileName());

        if (existingFile.isPresent()) {
            log.debug("Additional file already exists: {}", file.getFileName());
            return;
        }

        String hash = FileFingerprint.generateHash(file.getFullPath());
        BookAdditionalFileEntity additionalFile = BookAdditionalFileEntity.builder()
                .book(bookEntity)
                .fileName(file.getFileName())
                .fileSubPath(file.getFileSubPath())
                .additionalFileType(fileType)
                .fileSizeKb(FileUtils.getFileSizeInKb(file.getFullPath()))
                .initialHash(hash)
                .currentHash(hash)
                .addedOn(java.time.Instant.now())
                .build();

        try {
            log.debug("Creating additional file: {} (type: {})", file.getFileName(), fileType);
            bookAdditionalFileRepository.save(additionalFile);

            log.debug("Successfully created additional file: {}", file.getFileName());
        } catch (Exception e) {
            bookEntity.getAdditionalFiles().removeIf(a -> a.equals(additionalFile));

            log.error("Error creating additional file {}: {}", file.getFileName(), e.getMessage(), e);
        }
    }

    public record GetOrCreateBookResult(Optional<BookEntity> bookEntity, List<LibraryFile> remainingFiles) {
    }

    public record CreateBookResult(BookEntity bookEntity, LibraryFile libraryFile) {
    }

}
