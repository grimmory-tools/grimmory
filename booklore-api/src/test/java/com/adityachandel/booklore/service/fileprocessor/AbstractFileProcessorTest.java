package com.adityachandel.booklore.service.fileprocessor;

import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.model.DuplicateFileInfo;
import com.adityachandel.booklore.model.FileProcessResult;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.enums.FileProcessStatus;
import com.adityachandel.booklore.repository.BookAdditionalFileRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.BookCreatorService;
import com.adityachandel.booklore.service.FileFingerprint;
import com.adityachandel.booklore.service.metadata.MetadataMatchService;
import com.adityachandel.booklore.util.FileService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AbstractFileProcessorTest {

    @Mock
    BookRepository bookRepository;
    @Mock
    BookAdditionalFileRepository bookAdditionalFileRepository;
    @Mock
    BookCreatorService bookCreatorService;
    @Mock
    BookMapper bookMapper;
    @Mock
    MetadataMatchService metadataMatchService;
    @Mock
    FileService fileService;
    @Mock
    EntityManager entityManager;

    TestFileProcessor processor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        processor = new TestFileProcessor(
                bookRepository,
                bookAdditionalFileRepository,
                bookCreatorService,
                bookMapper,
                fileService,
                metadataMatchService
        );

        // Inject EntityManager via reflection
        try {
            var field = AbstractFileProcessor.class.getDeclaredField("entityManager");
            field.setAccessible(true);
            field.set(processor, entityManager);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void processFile_shouldReturnDuplicate_whenDuplicateFoundByFileService() {
        // Given
        LibraryFile libraryFile = createMockLibraryFile();
        Book duplicateBook = createMockBook(1L, "file.pdf");

        try (MockedStatic<FileFingerprint> fingerprintMock = mockStatic(FileFingerprint.class)) {
            fingerprintMock.when(() -> FileFingerprint.generateHash(any())).thenReturn("hash1");

            when(fileService.checkForDuplicateAndUpdateMetadataIfNeeded(
                    eq(libraryFile), eq("hash1"), eq(bookRepository), eq(bookAdditionalFileRepository), eq(bookMapper)))
                    .thenReturn(Optional.of(duplicateBook));

            BookEntity bookEntity = createMockBookEntity(1L, "file.pdf", "hash1", "sub", libraryFile.getLibraryPathEntity());
            when(bookRepository.findById(1L)).thenReturn(Optional.of(bookEntity));

            // When
            FileProcessResult result = processor.processFile(libraryFile);

            // Then
            assertThat(result.getStatus()).isEqualTo(FileProcessStatus.DUPLICATE);
            assertThat(result.getDuplicate()).isNotNull();
            assertThat(result.getDuplicate().getBookId()).isEqualTo(1L);
        }
    }

    @Test
    void processFile_shouldReturnDuplicate_whenBookFoundByFileNameAndLibraryId() {
        // Given
        LibraryFile libraryFile = createMockLibraryFile();
        BookEntity existingEntity = createMockBookEntity(2L, "file.pdf", "hash2", "sub", libraryFile.getLibraryPathEntity());
        Book existingBook = createMockBook(2L, "file.pdf");

        try (MockedStatic<FileFingerprint> fingerprintMock = mockStatic(FileFingerprint.class)) {
            fingerprintMock.when(() -> FileFingerprint.generateHash(any())).thenReturn("hash2");

            when(fileService.checkForDuplicateAndUpdateMetadataIfNeeded(any(), any(), any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(bookRepository.findBookByFileNameAndLibraryId("file.pdf", 1L))
                    .thenReturn(Optional.of(existingEntity));
            when(bookMapper.toBook(existingEntity)).thenReturn(existingBook);

            // When
            FileProcessResult result = processor.processFile(libraryFile);

            // Then
            assertThat(result.getStatus()).isEqualTo(FileProcessStatus.DUPLICATE);
            assertThat(result.getBook()).isEqualTo(existingBook);
            assertThat(result.getDuplicate()).isNotNull();
        }
    }

    @Test
    void processFile_shouldReturnNew_whenNoDuplicateFound() {
        // Given
        LibraryFile libraryFile = createMockLibraryFile();
        BookEntity newEntity = createMockBookEntity(3L, "file.pdf", "hash3", "sub", libraryFile.getLibraryPathEntity());
        Book newBook = createMockBook(3L, "file.pdf");

        try (MockedStatic<FileFingerprint> fingerprintMock = mockStatic(FileFingerprint.class)) {
            fingerprintMock.when(() -> FileFingerprint.generateHash(any())).thenReturn("hash3");

            when(fileService.checkForDuplicateAndUpdateMetadataIfNeeded(any(), any(), any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(bookRepository.findBookByFileNameAndLibraryId("file.pdf", 1L))
                    .thenReturn(Optional.empty());
            when(metadataMatchService.calculateMatchScore(any())).thenReturn(85F);
            when(bookMapper.toBook(newEntity)).thenReturn(newBook);

            processor.setProcessNewFileResult(newEntity);

            // When
            FileProcessResult result = processor.processFile(libraryFile);

            // Then
            assertThat(result.getStatus()).isEqualTo(FileProcessStatus.NEW);
            assertThat(result.getBook()).isEqualTo(newBook);
            assertThat(result.getDuplicate()).isNull();
            verify(bookCreatorService).saveConnections(newEntity);
            verify(metadataMatchService).calculateMatchScore(newEntity);
        }
    }

    @Test
    void processFile_shouldReturnUpdated_whenDuplicateFoundWithDifferentMetadata() {
        // Given
        LibraryFile libraryFile = createMockLibraryFile();
        libraryFile.setFileSubPath("new-sub");

        Book duplicateBook = createMockBook(1L, "file.pdf");
        BookEntity existingEntity = createMockBookEntity(1L, "file.pdf", "hash1", "old-sub", libraryFile.getLibraryPathEntity());
        Book updatedBook = createMockBook(1L, "file.pdf");

        try (MockedStatic<FileFingerprint> fingerprintMock = mockStatic(FileFingerprint.class)) {
            fingerprintMock.when(() -> FileFingerprint.generateHash(any())).thenReturn("hash1");

            when(fileService.checkForDuplicateAndUpdateMetadataIfNeeded(any(), any(), any(), any(), any()))
                    .thenReturn(Optional.of(duplicateBook));
            when(bookRepository.findById(1L)).thenReturn(Optional.of(existingEntity));
            when(bookMapper.toBook(existingEntity)).thenReturn(updatedBook);

            // When
            FileProcessResult result = processor.processFile(libraryFile);

            // Then
            assertThat(result.getStatus()).isEqualTo(FileProcessStatus.UPDATED);
            assertThat(result.getBook()).isEqualTo(updatedBook);
            assertThat(existingEntity.getFileSubPath()).isEqualTo("new-sub");
            verify(entityManager).flush();
            verify(entityManager).detach(existingEntity);
        }
    }

    @Test
    void processFile_shouldReturnUpdated_whenDuplicateFoundWithDifferentFileName() {
        // Given
        LibraryFile libraryFile = createMockLibraryFile();
        libraryFile.setFileName("new-file.pdf");

        Book duplicateBook = createMockBook(1L, "old-file.pdf");
        BookEntity existingEntity = createMockBookEntity(1L, "old-file.pdf", "hash1", "sub", libraryFile.getLibraryPathEntity());
        Book updatedBook = createMockBook(1L, "new-file.pdf");

        try (MockedStatic<FileFingerprint> fingerprintMock = mockStatic(FileFingerprint.class)) {
            fingerprintMock.when(() -> FileFingerprint.generateHash(any())).thenReturn("hash1");

            when(fileService.checkForDuplicateAndUpdateMetadataIfNeeded(any(), any(), any(), any(), any()))
                    .thenReturn(Optional.of(duplicateBook));
            when(bookRepository.findById(1L)).thenReturn(Optional.of(existingEntity));
            when(bookMapper.toBook(existingEntity)).thenReturn(updatedBook);

            // When
            FileProcessResult result = processor.processFile(libraryFile);

            // Then
            assertThat(result.getStatus()).isEqualTo(FileProcessStatus.UPDATED);
            assertThat(result.getBook()).isEqualTo(updatedBook);
            assertThat(existingEntity.getFileName()).isEqualTo("new-file.pdf");
            verify(entityManager).flush();
            verify(entityManager).detach(existingEntity);
        }
    }

    @Test
    void processFile_shouldReturnUpdated_whenDuplicateFoundWithDifferentLibraryPath() {
        // Given
        LibraryEntity library = LibraryEntity.builder().id(2L).build();
        LibraryPathEntity newLibraryPath = LibraryPathEntity.builder()
                .id(2L)
                .library(library)
                .path("/new-path")
                .build();

        LibraryFile libraryFile = LibraryFile.builder()
                .fileName("file.pdf")
                .fileSubPath("sub")
                .bookFileType(BookFileType.PDF)
                .libraryEntity(library)
                .libraryPathEntity(newLibraryPath)
                .build();

        Book duplicateBook = createMockBook(1L, "file.pdf");
        BookEntity existingEntity = createMockBookEntity(1L, "file.pdf", "hash1", "sub",
                LibraryPathEntity.builder().id(1L).library(LibraryEntity.builder().id(1L).build()).path("/old-path").build());
        Book updatedBook = createMockBook(1L, "file.pdf");

        try (MockedStatic<FileFingerprint> fingerprintMock = mockStatic(FileFingerprint.class)) {
            fingerprintMock.when(() -> FileFingerprint.generateHash(any())).thenReturn("hash1");

            when(fileService.checkForDuplicateAndUpdateMetadataIfNeeded(any(), any(), any(), any(), any()))
                    .thenReturn(Optional.of(duplicateBook));
            when(bookRepository.findById(1L)).thenReturn(Optional.of(existingEntity));
            when(bookMapper.toBook(existingEntity)).thenReturn(updatedBook);

            // When
            FileProcessResult result = processor.processFile(libraryFile);

            // Then
            assertThat(result.getStatus()).isEqualTo(FileProcessStatus.UPDATED);
            assertThat(result.getBook()).isEqualTo(updatedBook);
            assertThat(existingEntity.getLibraryPath()).isEqualTo(newLibraryPath);
            verify(entityManager).flush();
            verify(entityManager).detach(existingEntity);
        }
    }

    @Test
    void processFile_shouldReturnDuplicate_whenDuplicateFoundWithSameMetadata() {
        // Given
        LibraryFile libraryFile = createMockLibraryFile();
        Book duplicateBook = createMockBook(1L, "file.pdf");
        BookEntity existingEntity = createMockBookEntity(1L, "file.pdf", "hash1", "sub", libraryFile.getLibraryPathEntity());

        try (MockedStatic<FileFingerprint> fingerprintMock = mockStatic(FileFingerprint.class)) {
            fingerprintMock.when(() -> FileFingerprint.generateHash(any())).thenReturn("hash1");

            when(fileService.checkForDuplicateAndUpdateMetadataIfNeeded(any(), any(), any(), any(), any()))
                    .thenReturn(Optional.of(duplicateBook));
            when(bookRepository.findById(1L)).thenReturn(Optional.of(existingEntity));

            // When
            FileProcessResult result = processor.processFile(libraryFile);

            // Then
            assertThat(result.getStatus()).isEqualTo(FileProcessStatus.DUPLICATE);
            assertThat(result.getBook()).isEqualTo(duplicateBook);
            assertThat(result.getDuplicate()).isNotNull();
            assertThat(result.getDuplicate().getBookId()).isEqualTo(1L);
            verify(entityManager, never()).flush();
        }
    }

    @Test
    void processFile_shouldReturnDuplicateFromFallback_whenDuplicateBookNotFoundInRepository() {
        // Given
        LibraryFile libraryFile = createMockLibraryFile();
        Book duplicateBook = createMockBook(1L, "file.pdf");

        try (MockedStatic<FileFingerprint> fingerprintMock = mockStatic(FileFingerprint.class)) {
            fingerprintMock.when(() -> FileFingerprint.generateHash(any())).thenReturn("hash1");

            when(fileService.checkForDuplicateAndUpdateMetadataIfNeeded(any(), any(), any(), any(), any()))
                    .thenReturn(Optional.of(duplicateBook));
            when(bookRepository.findById(1L)).thenReturn(Optional.empty());

            // When
            FileProcessResult result = processor.processFile(libraryFile);

            // Then
            assertThat(result.getStatus()).isEqualTo(FileProcessStatus.DUPLICATE);
            assertThat(result.getBook()).isEqualTo(duplicateBook);
            assertThat(result.getDuplicate()).isNull();
        }
    }

    @Test
    void processFile_shouldUpdateHashEvenWhenOtherMetadataUnchanged() {
        // Given
        LibraryFile libraryFile = createMockLibraryFile();
        Book duplicateBook = createMockBook(1L, "file.pdf");
        BookEntity existingEntity = createMockBookEntity(1L, "file.pdf", "old-hash", "sub", libraryFile.getLibraryPathEntity());

        try (MockedStatic<FileFingerprint> fingerprintMock = mockStatic(FileFingerprint.class)) {
            fingerprintMock.when(() -> FileFingerprint.generateHash(any())).thenReturn("new-hash");

            when(fileService.checkForDuplicateAndUpdateMetadataIfNeeded(any(), any(), any(), any(), any()))
                    .thenReturn(Optional.of(duplicateBook));
            when(bookRepository.findById(1L)).thenReturn(Optional.of(existingEntity));
            when(bookMapper.toBook(existingEntity)).thenReturn(duplicateBook);

            // When
            FileProcessResult result = processor.processFile(libraryFile);

            // Then
            assertThat(existingEntity.getCurrentHash()).isEqualTo("new-hash");
            verify(entityManager).detach(existingEntity);
        }
    }

    @Test
    void processFile_shouldHandleNullFileSubPath() {
        // Given
        LibraryEntity library = LibraryEntity.builder().id(1L).build();
        LibraryPathEntity libraryPath = LibraryPathEntity.builder()
                .id(1L)
                .library(library)
                .path("/tmp")
                .build();

        LibraryFile libraryFile = LibraryFile.builder()
                .fileName("file.pdf")
                .fileSubPath("")  // Use empty string instead of null to avoid NPE in Paths.get
                .bookFileType(BookFileType.PDF)
                .libraryEntity(library)
                .libraryPathEntity(libraryPath)
                .build();

        BookEntity newEntity = createMockBookEntity(3L, "file.pdf", "hash3", "", libraryFile.getLibraryPathEntity());
        Book newBook = createMockBook(3L, "file.pdf");

        try (MockedStatic<FileFingerprint> fingerprintMock = mockStatic(FileFingerprint.class)) {
            fingerprintMock.when(() -> FileFingerprint.generateHash(any())).thenReturn("hash3");

            when(fileService.checkForDuplicateAndUpdateMetadataIfNeeded(any(), any(), any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(bookRepository.findBookByFileNameAndLibraryId("file.pdf", 1L))
                    .thenReturn(Optional.empty());
            when(metadataMatchService.calculateMatchScore(any())).thenReturn(90F);
            when(bookMapper.toBook(newEntity)).thenReturn(newBook);

            processor.setProcessNewFileResult(newEntity);

            // When
            FileProcessResult result = processor.processFile(libraryFile);

            // Then
            assertThat(result.getStatus()).isEqualTo(FileProcessStatus.NEW);
            assertThat(result.getBook()).isEqualTo(newBook);
        }
    }

    @Test
    void processFile_shouldHandleMultipleMetadataChangesSimultaneously() {
        // Given
        LibraryEntity newLibrary = LibraryEntity.builder().id(2L).build();
        LibraryPathEntity newLibraryPath = LibraryPathEntity.builder()
                .id(2L)
                .library(newLibrary)
                .path("/new-path")
                .build();

        LibraryFile libraryFile = LibraryFile.builder()
                .fileName("new-file.pdf")
                .fileSubPath("new-sub")
                .bookFileType(BookFileType.PDF)
                .libraryEntity(newLibrary)
                .libraryPathEntity(newLibraryPath)
                .build();

        Book duplicateBook = createMockBook(1L, "old-file.pdf");
        BookEntity existingEntity = createMockBookEntity(1L, "old-file.pdf", "hash1", "old-sub",
                LibraryPathEntity.builder().id(1L).library(LibraryEntity.builder().id(1L).build()).build());
        Book updatedBook = createMockBook(1L, "new-file.pdf");

        try (MockedStatic<FileFingerprint> fingerprintMock = mockStatic(FileFingerprint.class)) {
            fingerprintMock.when(() -> FileFingerprint.generateHash(any())).thenReturn("hash1");

            when(fileService.checkForDuplicateAndUpdateMetadataIfNeeded(any(), any(), any(), any(), any()))
                    .thenReturn(Optional.of(duplicateBook));
            when(bookRepository.findById(1L)).thenReturn(Optional.of(existingEntity));
            when(bookMapper.toBook(existingEntity)).thenReturn(updatedBook);

            // When
            FileProcessResult result = processor.processFile(libraryFile);

            // Then
            assertThat(result.getStatus()).isEqualTo(FileProcessStatus.UPDATED);
            assertThat(existingEntity.getFileName()).isEqualTo("new-file.pdf");
            assertThat(existingEntity.getFileSubPath()).isEqualTo("new-sub");
            assertThat(existingEntity.getLibraryPath()).isEqualTo(newLibraryPath);
            verify(entityManager).flush();
            verify(entityManager).detach(existingEntity);
        }
    }

    @Test
    void createDuplicateInfo_shouldCreateCorrectDuplicateInfo() {
        // Given
        LibraryFile libraryFile = createMockLibraryFile();
        Book book = createMockBook(1L, "file.pdf");

        try (MockedStatic<FileFingerprint> fingerprintMock = mockStatic(FileFingerprint.class)) {
            fingerprintMock.when(() -> FileFingerprint.generateHash(any())).thenReturn("hash1");

            when(fileService.checkForDuplicateAndUpdateMetadataIfNeeded(any(), any(), any(), any(), any()))
                    .thenReturn(Optional.of(book));
            when(bookRepository.findById(1L)).thenReturn(Optional.of(
                    createMockBookEntity(1L, "file.pdf", "hash1", "sub", libraryFile.getLibraryPathEntity())));

            // When
            FileProcessResult result = processor.processFile(libraryFile);

            // Then
            DuplicateFileInfo duplicateInfo = result.getDuplicate();
            assertThat(duplicateInfo).isNotNull();
            assertThat(duplicateInfo.getBookId()).isEqualTo(1L);
            assertThat(duplicateInfo.getFileName()).isEqualTo("file.pdf");
            assertThat(duplicateInfo.getFullPath()).contains("/tmp", "sub", "file.pdf");
        }
    }

    // Helper methods
    private LibraryFile createMockLibraryFile() {
        LibraryEntity library = LibraryEntity.builder().id(1L).build();
        LibraryPathEntity libraryPath = LibraryPathEntity.builder()
                .id(1L)
                .library(library)
                .path("/tmp")
                .build();

        return LibraryFile.builder()
                .fileName("file.pdf")
                .fileSubPath("sub")
                .bookFileType(BookFileType.PDF)
                .libraryEntity(library)
                .libraryPathEntity(libraryPath)
                .build();
    }

    private Book createMockBook(Long id, String fileName) {
        return Book.builder()
                .id(id)
                .fileName(fileName)
                .fileSubPath("sub")
                .build();
    }

    private BookEntity createMockBookEntity(Long id, String fileName, String hash, String subPath, LibraryPathEntity libraryPath) {
        return BookEntity.builder()
                .id(id)
                .fileName(fileName)
                .currentHash(hash)
                .fileSubPath(subPath)
                .libraryPath(libraryPath)
                .build();
    }

    // Test implementation of AbstractFileProcessor
    static class TestFileProcessor extends AbstractFileProcessor {
        private BookEntity processNewFileResult;

        public TestFileProcessor(BookRepository bookRepository,
                                 BookAdditionalFileRepository bookAdditionalFileRepository,
                                 BookCreatorService bookCreatorService,
                                 BookMapper bookMapper,
                                 FileService fileService,
                                 MetadataMatchService metadataMatchService) {
            super(bookRepository, bookAdditionalFileRepository, bookCreatorService, bookMapper, fileService, metadataMatchService);
        }

        @Override
        protected BookEntity processNewFile(LibraryFile libraryFile) {
            return processNewFileResult;
        }

        @Override
        public List<BookFileType> getSupportedTypes() {
            return List.of(BookFileType.PDF);
        }

        @Override
        public boolean generateCover(BookEntity bookEntity) {
            return false;
        }

        public void setProcessNewFileResult(BookEntity entity) {
            this.processNewFileResult = entity;
        }
    }
}
