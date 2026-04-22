package org.booklore.util.builder;

import org.booklore.mapper.BookMapper;
import org.booklore.mapper.BookMapperImpl;
import org.booklore.model.FileProcessResult;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.settings.LibraryFile;
import org.booklore.model.entity.*;
import org.booklore.model.enums.*;
import org.booklore.repository.BookAdditionalFileRepository;
import org.booklore.repository.BookRepository;
import org.booklore.service.file.FileFingerprint;
import org.booklore.service.fileprocessor.BookFileProcessor;
import org.booklore.service.fileprocessor.BookFileProcessorRegistry;
import org.booklore.util.FileUtils;
import jakarta.validation.constraints.NotNull;
import org.apache.commons.io.FilenameUtils;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Test builder for creating Library-related test objects.
 * Provides fluent API for creating LibraryEntity and Library DTO objects with sensible defaults.
 */
public class LibraryTestBuilder {

    public static final String DEFAULT_LIBRARY_NAME = "Test Library";
    public static final String DEFAULT_LIBRARY_PATH = "/library/books";

    private static final BookMapper BOOK_MAPPER = new BookMapperImpl();

    private Long libraryId = 1L;

    private final List<LibraryEntity> libraries = new ArrayList<>();
    private final Map<String, LibraryEntity> libraryMap = new HashMap<>();

    // DTO-specific fields
    private final List<LibraryFile> libraryFiles = new ArrayList<>();
    private final Map<Path, String> libraryFileHashes = new HashMap<>();
    private final Map<Long, BookEntity> bookRepository = new HashMap<>();
    private final Map<String, BookEntity> bookMap = new HashMap<>();
    private final Map<Long, BookFileEntity> bookAdditionalFileRepository = new HashMap<>();

    public LibraryTestBuilder(MockedStatic<FileUtils> fileUtilsMock,
                              MockedStatic<FileFingerprint> fileFingerprintMock,
                              BookFileProcessorRegistry bookFileProcessorRegistry,
                              BookFileProcessor bookFileProcessorMock,
                              BookRepository bookRepositoryMock,
                              BookAdditionalFileRepository bookAdditionalFileRepositoryMock) {
        fileUtilsMock.when(() -> FileUtils.getFileSizeInKb(any(Path.class))).thenReturn(100L);
        fileFingerprintMock.when(() -> FileFingerprint.generateHash(any(Path.class)))
                .then(invocation -> {
                    Path path = invocation.getArgument(0);
                    return computeFileHash(path);
                });

        lenient().when(bookFileProcessorRegistry.getProcessorOrThrow(any(BookFileType.class)))
                .thenReturn(bookFileProcessorMock);
        lenient().when(bookFileProcessorMock.processFile(any(LibraryFile.class)))
                .then(invocation -> {
                    LibraryFile libraryFile = invocation.getArgument(0);
                    return processFileResult(libraryFile);
                });
        lenient().when(bookRepositoryMock.getReferenceById(anyLong()))
                .thenAnswer(invocation -> {
                    Long bookId = invocation.getArgument(0);
                    return getBookById(bookId);
                });
        when(bookRepositoryMock.findAllByLibraryPathIdAndFileSubPathStartingWith(anyLong(), any(String.class)))
                .thenAnswer(invocation -> {
                    Long libraryPathId = invocation.getArgument(0);
                    String fileSubPath = invocation.getArgument(1);
                    return bookRepository.values()
                            .stream()
                            .filter(book -> book.getLibraryPath().getId().equals(libraryPathId) &&
                                    book.getPrimaryBookFile().getFileSubPath().startsWith(fileSubPath))
                            .toList();
                });

        // lenient is used to avoid strict stubbing issues,
        // the builder does not know when the save method will be called
        lenient().when(bookAdditionalFileRepositoryMock.save(any(BookFileEntity.class)))
                .thenAnswer(invocation -> {
                    BookFileEntity additionalFile = invocation.getArgument(0);
                    return saveBookAdditionalFile(additionalFile);
                });
    }

    /**
     * Creates a default library with a single path.
     */
    public LibraryTestBuilder addDefaultLibrary() {
        return addLibrary(DEFAULT_LIBRARY_NAME)
                .addPath(DEFAULT_LIBRARY_PATH);
    }

    public LibraryTestBuilder addLibrary(String name) {
        LibraryEntity library = new LibraryEntity();
        library.setId(libraryId++);
        library.setName(name);
        library.setFormatPriority(List.of(BookFileType.EPUB));
        library.setLibraryPaths(new ArrayList<>());

        libraries.add(library);
        libraryMap.put(name, library);
        return this;
    }

    public LibraryEntity getLibraryEntity() {
        if (libraries.isEmpty()) {
            throw new IllegalStateException("No library available. Please add a library first.");
        }
        return getLibraryEntity(DEFAULT_LIBRARY_NAME);
    }

    public LibraryEntity getLibraryEntity(String libraryName) {
        return libraryMap.get(libraryName);
    }

    public List<LibraryFile> getLibraryFiles() {
        if (libraryFiles.isEmpty()) {
            throw new IllegalStateException("No library files available. Please add a library file first.");
        }
        return List.copyOf(libraryFiles);
    }

    public List<BookEntity> getBookEntities() {
        if (bookRepository.isEmpty()) {
            throw new IllegalStateException("No book entities available. Please process a file first.");
        }
        return new ArrayList<>(bookRepository.values());
    }

    public BookEntity getBookEntity(String bookTitle) {
        if (bookMap.isEmpty()) {
            throw new IllegalStateException("No book entities available. Please process a file first.");
        }
        if (!bookMap.containsKey(bookTitle)) {
            throw new IllegalStateException("No book found with title: " + bookTitle);
        }
        return bookMap.get(bookTitle);
    }

    public List<BookFileEntity> getBookAdditionalFiles() {
        return new ArrayList<>(bookAdditionalFileRepository.values());
    }

    public LibraryTestBuilder addPath(String path) {
        if (libraries.isEmpty()) {
            throw new IllegalStateException("No library available to add a path. Please add a library first.");
        }

        var library = libraries.getLast();
        var id = libraries
                .stream()
                .map(l -> l.getLibraryPaths()
                        .stream()
                        .map(LibraryPathEntity::getId)
                        .max(Long::compareTo)
                        .orElse(0L))
                .max(Long::compareTo)
                .orElse(0L)+ 1;
        LibraryPathEntity libraryPath = LibraryPathEntity.builder()
                .id(id)
                .path(path)
                .build();
        library.getLibraryPaths().add(libraryPath);

        return this;
    }

    public LibraryTestBuilder addBook(String fileSubPath, String fileName) {
        String subPath = removeLeadingSlash(fileSubPath);

        long id = bookRepository.size() + 1L;
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title(FilenameUtils.removeExtension(fileName))
                .bookId(id)
                .build();

        String hash = computeFileHash(Path.of(subPath, fileName));

        BookEntity bookEntity = BookEntity.builder()
                .id(id)
                .library(getLibraryEntity())
                .libraryPath(getLibraryEntity().getLibraryPaths().getLast())
                .addedOn(java.time.Instant.now())
                .metadata(metadata)
                .bookFiles(new ArrayList<>())
                .build();

        BookFileEntity primaryFile = BookFileEntity.builder()
                .book(bookEntity)
                .fileName(fileName)
                .fileSubPath(subPath)
                .isBookFormat(true)
                .bookType(getBookFileType(fileName))
                .fileSizeKb(1024L)
                .initialHash(hash)
                .currentHash(hash)
                .addedOn(java.time.Instant.now())
                .build();
        bookEntity.getBookFiles().add(primaryFile);

        bookRepository.put(bookEntity.getId(), bookEntity);
        bookMap.put(metadata.getTitle(), bookEntity);

        return this;
    }

    public LibraryTestBuilder addLibraryFile(String fileSubPath, String fileName, String hash) {
        addLibraryFile(fileSubPath, fileName);

        var lastLibraryFiles = libraryFiles.getLast();

        Path filePath = Path.of(lastLibraryFiles.getLibraryPathEntity().getPath(), fileSubPath, fileName);
        if (libraryFileHashes.containsKey(filePath)) {
            throw new IllegalArgumentException("File with the same path and name already exists: " + fileSubPath + "/" + fileName);
        }
        libraryFileHashes.put(filePath, hash);

        return this;
    }

    public LibraryTestBuilder addLibraryFile(String fileSubPath, String fileName) {
        if (libraries.isEmpty()) {
            throw new IllegalStateException("No library available to add a book. Please add a library first.");
        }

        var library = libraries.getLast();
        if (library.getLibraryPaths().isEmpty()) {
            throw new IllegalStateException("No library path available to add a book. Please add a path first.");
        }
        var libraryPath = library.getLibraryPaths().getLast();

        // Really don't want to check if there is subpath in library paths
        var libraryFile = LibraryFile.builder()
                .libraryPathEntity(libraryPath)
                .fileSubPath(fileSubPath)
                .fileName(fileName)
                .bookFileType(getBookFileType(fileName))
                .build();

        libraryFiles.add(libraryFile);

        return this;
    }

    private @NotNull String computeFileHash(Path path) {
        if (libraryFileHashes.containsKey(path)) {
            return libraryFileHashes.get(path);
        }

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        byte[] hash = digest.digest(path.getFileName().toString().getBytes());
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private FileProcessResult processFileResult(LibraryFile libraryFile) {
        var book = processBook(libraryFile);
        return FileProcessResult.builder()
                .book(book)
                .status(FileProcessStatus.NEW)
                .build();
    }

    private Book processBook(LibraryFile libraryFile) {
        var hash = computeFileHash(libraryFile.getFullPath());

        long id = libraryFiles.indexOf(libraryFile) + 1L;
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title(FilenameUtils.removeExtension(libraryFile.getFileName()))
                .bookId(id)
                .build();

        BookEntity bookEntity = BookEntity.builder()
                .id(id) // Simple ID generation based on index
                .library(libraryFile.getLibraryPathEntity().getLibrary())
                .libraryPath(libraryFile.getLibraryPathEntity())
                .addedOn(java.time.Instant.now())
                .metadata(metadata)
                .bookFiles(new ArrayList<>())
                .build();

        BookFileEntity primaryFile = BookFileEntity.builder()
                .book(bookEntity)
                .fileName(libraryFile.getFileName())
                .fileSubPath(libraryFile.getFileSubPath())
                .isBookFormat(true)
                .bookType(libraryFile.getBookFileType())
                .fileSizeKb(1024L)
                .initialHash(hash)
                .currentHash(hash)
                .addedOn(java.time.Instant.now())
                .build();
        bookEntity.getBookFiles().add(primaryFile);

        bookRepository.put(bookEntity.getId(), bookEntity);
        bookMap.put(metadata.getTitle(), bookEntity);

        return BOOK_MAPPER.toBook(bookEntity);
    }

    private BookEntity getBookById(Long bookId) {
        if (!bookRepository.containsKey(bookId)) {
            throw new IllegalStateException("No book found with ID: " + bookId);
        }
        return bookRepository.get(bookId);
    }

    private @NotNull BookFileEntity saveBookAdditionalFile(BookFileEntity additionalFile) {
        if (additionalFile.getId() != null) {
            throw new IllegalArgumentException("ID must be null for new additional files");
        }

        // Do not allow files with duplicate hashes for alternative formats only
        if (additionalFile.isBookFormat() &&
                bookAdditionalFileRepository.values()
                        .stream()
                        .anyMatch(existingFile -> existingFile.getCurrentHash()
                                .equals(additionalFile.getCurrentHash()))) {
            throw new IllegalArgumentException("File with the same hash already exists: " + additionalFile.getCurrentHash());
        }

        additionalFile.setId((long) bookAdditionalFileRepository.size() + 1);
        bookAdditionalFileRepository.put(additionalFile.getId(), additionalFile);
        return additionalFile;
    }

    private static BookFileType getBookFileType(String fileName) {
        var extension = BookFileExtension.fromFileName(fileName);
        return extension.map(BookFileExtension::getType).orElse(null);
    }

    private static String removeLeadingSlash(String path) {
        return path.startsWith("/") ? path.substring(1) : path;
    }
}
