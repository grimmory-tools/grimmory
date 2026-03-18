package org.booklore.service.book;

import jakarta.persistence.EntityManager;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.APIException;
import org.booklore.mapper.BookMapper;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.response.AttachBookFileResponse;
import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.BookFileRepository;
import org.booklore.repository.BookRepository;
import org.booklore.repository.UserBookProgressRepository;
import org.booklore.service.file.FileMoveHelper;
import org.booklore.service.monitoring.MonitoringRegistrationService;
import org.booklore.service.progress.ReadingProgressService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookFileAttachmentServiceTest {

    @Mock private BookRepository bookRepository;
    @Mock private BookFileRepository bookFileRepository;
    @Mock private UserBookProgressRepository userBookProgressRepository;
    @Mock private AuthenticationService authenticationService;
    @Mock private ReadingProgressService readingProgressService;
    @Mock private MonitoringRegistrationService monitoringRegistrationService;
    @Mock private FileMoveHelper fileMoveHelper;
    @Mock private BookMapper bookMapper;
    @Mock private BookService bookService;
    @Mock private EntityManager entityManager;

    @InjectMocks
    private BookFileAttachmentService service;

    @TempDir
    Path rawTempDir;

    private Path tempDir;
    private LibraryEntity library;
    private LibraryPathEntity libraryPath;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = rawTempDir.toRealPath();

        library = LibraryEntity.builder()
                .id(1L)
                .name("Test Library")
                .build();

        libraryPath = LibraryPathEntity.builder()
                .id(1L)
                .library(library)
                .path(tempDir.toString())
                .build();

        library.setLibraryPaths(new ArrayList<>(List.of(libraryPath)));
    }

    private BookEntity createBook(Long id) {
        BookEntity book = BookEntity.builder()
                .id(id)
                .library(library)
                .libraryPath(libraryPath)
                .build();
        book.setBookFiles(new ArrayList<>());
        return book;
    }

    private BookFileEntity createBookFile(Long id, BookEntity book, String fileName, String subPath,
                                          boolean isBookFormat, boolean folderBased) throws IOException {
        BookFileEntity file = BookFileEntity.builder()
                .id(id)
                .book(book)
                .fileName(fileName)
                .fileSubPath(subPath)
                .isBookFormat(isBookFormat)
                .folderBased(folderBased)
                .bookType(BookFileType.EPUB)
                .build();

        Path dir = tempDir.resolve(subPath);
        Files.createDirectories(dir);
        Files.createFile(dir.resolve(fileName));

        return file;
    }

    private BookFileEntity createBookFileNoPhysicalFile(Long id, BookEntity book, String fileName,
                                                        String subPath, boolean isBookFormat) {
        BookFileEntity file = BookFileEntity.builder()
                .id(id)
                .book(book)
                .fileName(fileName)
                .fileSubPath(subPath)
                .isBookFormat(isBookFormat)
                .folderBased(false)
                .bookType(BookFileType.EPUB)
                .build();
        return file;
    }

    private void setupGetUpdatedBookMocks(Long bookId, BookEntity bookEntity) {
        BookLoreUser user = BookLoreUser.builder().id(1L).username("testuser").build();
        lenient().when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        lenient().when(bookRepository.findByIdWithBookFiles(bookId))
                .thenReturn(Optional.of(bookEntity));
        lenient().when(userBookProgressRepository.findByUserIdAndBookId(1L, bookId))
                .thenReturn(Optional.empty());
        lenient().when(readingProgressService.fetchUserFileProgress(eq(1L), anySet()))
                .thenReturn(Map.of());
        Book dto = Book.builder().id(bookId).build();
        lenient().when(bookMapper.toBook(bookEntity)).thenReturn(dto);
        lenient().when(bookService.filterShelvesByUserId(any(), eq(1L))).thenReturn(Set.of());
    }

    @Nested
    @DisplayName("Validation: Target Book")
    class TargetBookValidation {

        @Test
        @DisplayName("Throws when target book does not exist")
        void attachBookFiles_targetNotFound_throws() {
            when(bookRepository.findByIdWithBookFiles(999L)).thenReturn(Optional.empty());

            APIException ex = assertThrows(APIException.class,
                    () -> service.attachBookFiles(999L, List.of(2L), false));
            assertTrue(ex.getMessage().contains("999"));
        }

        @Test
        @DisplayName("Throws when target book has no book format files")
        void attachBookFiles_targetNoPrimaryFile_throws() throws IOException {
            BookEntity target = createBook(1L);
            createBookFile(10L, target, "notes.txt", "sub", false, false);

            BookEntity source = createBook(2L);
            createBookFile(20L, source, "book.epub", "sub2", true, false);

            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(target));
            when(bookRepository.findByIdWithBookFiles(2L)).thenReturn(Optional.of(source));

            APIException ex = assertThrows(APIException.class,
                    () -> service.attachBookFiles(1L, List.of(2L), false));
            assertTrue(ex.getMessage().contains("no primary file"));
        }
    }

    @Nested
    @DisplayName("Validation: Source Books")
    class SourceBookValidation {

        @Test
        @DisplayName("Throws when attaching a book to itself")
        void attachBookFiles_selfAttach_throws() {
            BookEntity target = createBook(1L);
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(target));

            APIException ex = assertThrows(APIException.class,
                    () -> service.attachBookFiles(1L, List.of(1L), false));
            assertTrue(ex.getMessage().contains("Cannot attach a book to itself"));
        }

        @Test
        @DisplayName("Throws when source book does not exist")
        void attachBookFiles_sourceNotFound_throws() throws IOException {
            BookEntity target = createBook(1L);
            createBookFile(10L, target, "target.epub", "sub", true, false);

            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(target));
            when(bookRepository.findByIdWithBookFiles(999L)).thenReturn(Optional.empty());

            APIException ex = assertThrows(APIException.class,
                    () -> service.attachBookFiles(1L, List.of(999L), false));
            assertTrue(ex.getMessage().contains("999"));
        }

        @Test
        @DisplayName("Throws when source book is in a different library")
        void attachBookFiles_differentLibrary_throws() throws IOException {
            BookEntity target = createBook(1L);
            createBookFile(10L, target, "target.epub", "sub", true, false);

            LibraryEntity otherLibrary = LibraryEntity.builder().id(2L).name("Other").build();
            BookEntity source = BookEntity.builder()
                    .id(2L)
                    .library(otherLibrary)
                    .libraryPath(libraryPath)
                    .bookFiles(new ArrayList<>())
                    .build();
            createBookFile(20L, source, "source.epub", "sub2", true, false);

            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(target));
            when(bookRepository.findByIdWithBookFiles(2L)).thenReturn(Optional.of(source));

            APIException ex = assertThrows(APIException.class,
                    () -> service.attachBookFiles(1L, List.of(2L), false));
            assertTrue(ex.getMessage().contains("same library"));
        }

        @Test
        @DisplayName("Throws when source book has no book format files")
        void attachBookFiles_sourceNoBookFiles_throws() throws IOException {
            BookEntity target = createBook(1L);
            createBookFile(10L, target, "target.epub", "sub", true, false);

            BookEntity source = createBook(2L);
            createBookFile(20L, source, "notes.txt", "sub2", false, false);

            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(target));
            when(bookRepository.findByIdWithBookFiles(2L)).thenReturn(Optional.of(source));

            APIException ex = assertThrows(APIException.class,
                    () -> service.attachBookFiles(1L, List.of(2L), false));
            assertTrue(ex.getMessage().contains("no book format files"));
        }

        @Test
        @DisplayName("Throws when source book contains folder-based audiobook")
        void attachBookFiles_folderBasedSource_throws() throws IOException {
            BookEntity target = createBook(1L);
            createBookFile(10L, target, "target.epub", "sub", true, false);

            BookEntity source = createBook(2L);
            createBookFile(20L, source, "audiobook", "sub2", true, true);

            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(target));
            when(bookRepository.findByIdWithBookFiles(2L)).thenReturn(Optional.of(source));

            APIException ex = assertThrows(APIException.class,
                    () -> service.attachBookFiles(1L, List.of(2L), false));
            assertTrue(ex.getMessage().contains("folder-based"));
        }

        @Test
        @DisplayName("Throws when source file does not exist on disk")
        void attachBookFiles_sourceFileMissing_throws() {
            BookEntity target = createBook(1L);
            createBookFileNoPhysicalFile(10L, target, "target.epub", "sub", true);
            // Create the target file physically so target validation passes
            try {
                Files.createDirectories(tempDir.resolve("sub"));
                Files.createFile(tempDir.resolve("sub/target.epub"));
            } catch (IOException e) {
                fail("Setup failed");
            }

            BookEntity source = createBook(2L);
            createBookFileNoPhysicalFile(20L, source, "missing.epub", "sub2", true);

            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(target));
            when(bookRepository.findByIdWithBookFiles(2L)).thenReturn(Optional.of(source));

            APIException ex = assertThrows(APIException.class,
                    () -> service.attachBookFiles(1L, List.of(2L), false));
            assertTrue(ex.getMessage().contains("Source file not found"));
        }

        @Test
        @DisplayName("Deduplicates source book IDs")
        void attachBookFiles_duplicateSourceIds_deduplicates() throws IOException {
            BookEntity target = createBook(1L);
            createBookFile(10L, target, "target.epub", "sub", true, false);

            BookEntity source = createBook(2L);
            createBookFile(20L, source, "source.epub", "sub2", true, false);

            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(target));
            when(bookRepository.findByIdWithBookFiles(2L)).thenReturn(Optional.of(source));

            setupGetUpdatedBookMocks(1L, target);

            service.attachBookFiles(1L, List.of(2L, 2L, 2L), false);

            // findByIdWithBookFiles for source should be called only once (deduplicated)
            verify(bookRepository, times(1)).findByIdWithBookFiles(2L);
        }
    }

    @Nested
    @DisplayName("Attach Without File Move")
    class AttachWithoutFileMove {

        @Test
        @DisplayName("Reassigns book files via JPQL and deletes source book")
        void attachWithoutFileMove_singleSource_reassignsAndDeletes() throws IOException {
            BookEntity target = createBook(1L);
            createBookFile(10L, target, "target.epub", "sub", true, false);

            BookEntity source = createBook(2L);
            createBookFile(20L, source, "source.epub", "sub2", true, false);

            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(target));
            when(bookRepository.findByIdWithBookFiles(2L)).thenReturn(Optional.of(source));

            when(bookRepository.findAllById(List.of(2L))).thenReturn(List.of(source));
            setupGetUpdatedBookMocks(1L, target);

            AttachBookFileResponse result = service.attachBookFiles(1L, List.of(2L), false);

            assertNotNull(result);
            assertNotNull(result.updatedBook());
            assertEquals(List.of(2L), result.deletedSourceBookIds());
            verify(bookFileRepository).reassignFilesToBook(eq(1L), anyList());
            verify(entityManager).flush();
            verify(entityManager).clear();
            verify(bookRepository).deleteAll(List.of(source));
        }

        @Test
        @DisplayName("Handles multiple source books")
        void attachWithoutFileMove_multipleSources_reassignsAll() throws IOException {
            BookEntity target = createBook(1L);
            createBookFile(10L, target, "target.epub", "sub", true, false);

            BookEntity source1 = createBook(2L);
            createBookFile(20L, source1, "source1.epub", "sub2", true, false);

            BookEntity source2 = createBook(3L);
            createBookFile(30L, source2, "source2.epub", "sub3", true, false);

            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(target));
            when(bookRepository.findByIdWithBookFiles(2L)).thenReturn(Optional.of(source1));
            when(bookRepository.findByIdWithBookFiles(3L)).thenReturn(Optional.of(source2));

            when(bookRepository.findAllById(anyList())).thenReturn(List.of(source1, source2));
            setupGetUpdatedBookMocks(1L, target);

            service.attachBookFiles(1L, List.of(2L, 3L), false);

            verify(bookFileRepository, times(2)).reassignFilesToBook(eq(1L), anyList());
            verify(bookRepository).deleteAll(anyList());
        }

        @Test
        @DisplayName("Does not delete source book if it has remaining non-book files")
        void attachWithoutFileMove_sourceHasRemainingFiles_noDelete() throws IOException {
            BookEntity target = createBook(1L);
            createBookFile(10L, target, "target.epub", "sub", true, false);

            BookEntity source = createBook(2L);
            createBookFile(20L, source, "source.epub", "sub2", true, false);
            createBookFile(21L, source, "cover.jpg", "sub2", false, false);

            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(target));
            when(bookRepository.findByIdWithBookFiles(2L)).thenReturn(Optional.of(source));

            setupGetUpdatedBookMocks(1L, target);

            service.attachBookFiles(1L, List.of(2L), false);

            verify(bookRepository, never()).deleteAll(anyList());
        }

        @Test
        @DisplayName("Does not physically move files")
        void attachWithoutFileMove_filesStayInPlace() throws IOException {
            BookEntity target = createBook(1L);
            createBookFile(10L, target, "target.epub", "sub", true, false);

            BookEntity source = createBook(2L);
            Path sourceFile = createBookFile(20L, source, "source.epub", "sub2", true, false)
                    .getFullFilePath();

            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(target));
            when(bookRepository.findByIdWithBookFiles(2L)).thenReturn(Optional.of(source));

            when(bookRepository.findAllById(List.of(2L))).thenReturn(List.of(source));
            setupGetUpdatedBookMocks(1L, target);

            service.attachBookFiles(1L, List.of(2L), false);

            assertTrue(Files.exists(sourceFile), "Source file should remain at original location");
        }
    }

    private void setupFileMoveStubs(BookEntity target, String pattern) {
        lenient().when(fileMoveHelper.getFileNamingPattern(target.getLibrary())).thenReturn(pattern);
    }

    @Nested
    @DisplayName("Attach With File Move")
    class AttachWithFileMove {

        @Test
        @DisplayName("Moves source file to target directory and renames")
        void attachWithFileMove_singleSource_movesFile() throws IOException {
            BookEntity target = createBook(1L);
            createBookFile(10L, target, "target.epub", "target_dir", true, false);

            BookEntity source = createBook(2L);
            createBookFile(20L, source, "source.pdf", "source_dir", true, false);
            source.getBookFiles().getFirst().setBookType(BookFileType.PDF);

            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(target));
            when(bookRepository.findByIdWithBookFiles(2L)).thenReturn(Optional.of(source));
            setupGetUpdatedBookMocks(1L, target);
            setupFileMoveStubs(target, "target_dir/{title}");

            service.attachBookFiles(1L, List.of(2L), true);

            assertFalse(Files.exists(tempDir.resolve("source_dir/source.pdf")), "Source file should no longer be at original location");
            assertTrue(Files.exists(tempDir.resolve("target_dir/target.pdf")),
                    "Source file should be moved to target directory with base name");
        }

        @Test
        @DisplayName("Adds suffix when same extension already exists")
        void attachWithFileMove_sameExtension_addsSuffix() throws IOException {
            BookEntity target = createBook(1L);
            createBookFile(10L, target, "target.epub", "target_dir", true, false);

            BookEntity source = createBook(2L);
            createBookFile(20L, source, "source.epub", "source_dir", true, false);

            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(target));
            when(bookRepository.findByIdWithBookFiles(2L)).thenReturn(Optional.of(source));
            setupGetUpdatedBookMocks(1L, target);
            setupFileMoveStubs(target, "target_dir/{title}");

            service.attachBookFiles(1L, List.of(2L), true);

            Path movedFile = tempDir.resolve("target_dir/target_1.epub");
            assertTrue(Files.exists(movedFile), "Source file should be moved with _1 suffix");
        }

        @Test
        @DisplayName("Organizes target files when primary not at pattern location")
        void attachWithFileMove_primaryNotAtPattern_organizesFirst() throws IOException {
            BookEntity target = createBook(1L);
            target.setMetadata(BookMetadataEntity.builder().bookId(1L).title("new_name").build());
            createBookFile(10L, target, "old_name.epub", "old_dir", true, false);

            BookEntity source = createBook(2L);
            createBookFile(20L, source, "source.pdf", "source_dir", true, false);
            source.getBookFiles().getFirst().setBookType(BookFileType.PDF);

            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(target));
            when(bookRepository.findByIdWithBookFiles(2L)).thenReturn(Optional.of(source));
            setupGetUpdatedBookMocks(1L, target);
            setupFileMoveStubs(target, "new_dir/{title}");

            service.attachBookFiles(1L, List.of(2L), true);

            assertTrue(Files.exists(tempDir.resolve("new_dir/new_name.epub")),
                    "Target file should be organized to pattern location");
            assertTrue(Files.exists(tempDir.resolve("new_dir/new_name.pdf")),
                    "Source file should be moved to new target directory");
        }

        @Test
        @DisplayName("Skips organizing if primary file is already at pattern location")
        void attachWithFileMove_primaryAtPattern_skipsOrganization() throws IOException {
            BookEntity target = createBook(1L);
            createBookFile(10L, target, "target.epub", "target_dir", true, false);

            BookEntity source = createBook(2L);
            createBookFile(20L, source, "source.pdf", "source_dir", true, false);
            source.getBookFiles().getFirst().setBookType(BookFileType.PDF);

            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(target));
            when(bookRepository.findByIdWithBookFiles(2L)).thenReturn(Optional.of(source));
            setupGetUpdatedBookMocks(1L, target);
            setupFileMoveStubs(target, "target_dir/{title}");

            service.attachBookFiles(1L, List.of(2L), true);

            assertTrue(Files.exists(tempDir.resolve("target_dir/target.epub")),
                    "Target file should remain at pattern location");
            assertTrue(Files.exists(tempDir.resolve("target_dir/target.pdf")),
                    "Source file should be moved to target directory");
        }

        @Test
        @DisplayName("Deletes source book when all book files are moved")
        void attachWithFileMove_allFilesMoved_deletesSourceBook() throws IOException {
            BookEntity target = createBook(1L);
            createBookFile(10L, target, "target.epub", "target_dir", true, false);

            BookEntity source = createBook(2L);
            createBookFile(20L, source, "source.pdf", "source_dir", true, false);
            source.getBookFiles().getFirst().setBookType(BookFileType.PDF);

            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(target));
            when(bookRepository.findByIdWithBookFiles(2L)).thenReturn(Optional.of(source));
            setupGetUpdatedBookMocks(1L, target);
            setupFileMoveStubs(target, "target_dir/{title}");

            service.attachBookFiles(1L, List.of(2L), true);

            verify(bookRepository).deleteAll(anyList());
        }

        @Test
        @DisplayName("Throws when target primary file missing and not at pattern")
        void attachWithFileMove_targetPrimaryMissing_throws() throws IOException {
            BookEntity target = createBook(1L);
            target.setMetadata(BookMetadataEntity.builder().bookId(1L).title("other").build());
            createBookFileNoPhysicalFile(10L, target, "missing.epub", "sub", true);

            BookEntity source = createBook(2L);
            createBookFile(20L, source, "source.epub", "sub2", true, false);

            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(target));
            when(bookRepository.findByIdWithBookFiles(2L)).thenReturn(Optional.of(source));
            setupFileMoveStubs(target, "other_dir/{title}");

            APIException ex = assertThrows(APIException.class,
                    () -> service.attachBookFiles(1L, List.of(2L), true));
            assertTrue(ex.getMessage().contains("primary file not found"));
        }

        @Test
        @DisplayName("Re-registers monitoring paths in finally block")
        void attachWithFileMove_reregistersMonitoring() throws IOException {
            BookEntity target = createBook(1L);
            createBookFile(10L, target, "target.epub", "target_dir", true, false);

            BookEntity source = createBook(2L);
            createBookFile(20L, source, "source.pdf", "source_dir", true, false);
            source.getBookFiles().getFirst().setBookType(BookFileType.PDF);

            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(target));
            when(bookRepository.findByIdWithBookFiles(2L)).thenReturn(Optional.of(source));
            setupGetUpdatedBookMocks(1L, target);
            setupFileMoveStubs(target, "target_dir/{title}");

            service.attachBookFiles(1L, List.of(2L), true);

            verify(monitoringRegistrationService, atLeastOnce()).registerSpecificPath(any(Path.class), eq(1L));
        }

        @Test
        @DisplayName("Monitoring unregister failure does not block operation")
        void attachWithFileMove_monitoringFailure_continues() throws IOException {
            BookEntity target = createBook(1L);
            createBookFile(10L, target, "target.epub", "target_dir", true, false);

            BookEntity source = createBook(2L);
            createBookFile(20L, source, "source.pdf", "source_dir", true, false);
            source.getBookFiles().getFirst().setBookType(BookFileType.PDF);

            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(target));
            when(bookRepository.findByIdWithBookFiles(2L)).thenReturn(Optional.of(source));
            setupGetUpdatedBookMocks(1L, target);
            setupFileMoveStubs(target, "target_dir/{title}");

            doThrow(new RuntimeException("monitoring error"))
                    .when(monitoringRegistrationService).unregisterSpecificPath(any(Path.class));

            assertDoesNotThrow(() -> service.attachBookFiles(1L, List.of(2L), true));
        }

        @Test
        @DisplayName("Cleans up empty source directories")
        void attachWithFileMove_cleansUpEmptyDirs() throws IOException {
            BookEntity target = createBook(1L);
            createBookFile(10L, target, "target.epub", "target_dir", true, false);

            BookEntity source = createBook(2L);
            createBookFile(20L, source, "source.pdf", "source_dir", true, false);
            source.getBookFiles().getFirst().setBookType(BookFileType.PDF);

            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(target));
            when(bookRepository.findByIdWithBookFiles(2L)).thenReturn(Optional.of(source));
            setupGetUpdatedBookMocks(1L, target);
            setupFileMoveStubs(target, "target_dir/{title}");

            service.attachBookFiles(1L, List.of(2L), true);

            verify(bookService, atLeastOnce()).deleteEmptyParentDirsUpToLibraryFolders(any(Path.class), anySet());
        }

        @Test
        @DisplayName("Handles multiple source books with different extensions")
        void attachWithFileMove_multipleSourcesDifferentExtensions() throws IOException {
            BookEntity target = createBook(1L);
            createBookFile(10L, target, "target.epub", "target_dir", true, false);

            BookEntity source1 = createBook(2L);
            createBookFile(20L, source1, "source1.pdf", "source_dir1", true, false);
            source1.getBookFiles().getFirst().setBookType(BookFileType.PDF);

            BookEntity source2 = createBook(3L);
            createBookFile(30L, source2, "source2.mobi", "source_dir2", true, false);
            source2.getBookFiles().getFirst().setBookType(BookFileType.MOBI);

            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(target));
            when(bookRepository.findByIdWithBookFiles(2L)).thenReturn(Optional.of(source1));
            when(bookRepository.findByIdWithBookFiles(3L)).thenReturn(Optional.of(source2));
            setupGetUpdatedBookMocks(1L, target);
            setupFileMoveStubs(target, "target_dir/{title}");

            service.attachBookFiles(1L, List.of(2L, 3L), true);

            assertTrue(Files.exists(tempDir.resolve("target_dir/target.pdf")));
            assertTrue(Files.exists(tempDir.resolve("target_dir/target.mobi")));
        }

        @Test
        @DisplayName("Handles multiple source books with same extension using incrementing suffix")
        void attachWithFileMove_multipleSourcesSameExtension_incrementsSuffix() throws IOException {
            BookEntity target = createBook(1L);
            createBookFile(10L, target, "target.epub", "target_dir", true, false);

            BookEntity source1 = createBook(2L);
            createBookFile(20L, source1, "source1.epub", "source_dir1", true, false);

            BookEntity source2 = createBook(3L);
            createBookFile(30L, source2, "source2.epub", "source_dir2", true, false);

            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(target));
            when(bookRepository.findByIdWithBookFiles(2L)).thenReturn(Optional.of(source1));
            when(bookRepository.findByIdWithBookFiles(3L)).thenReturn(Optional.of(source2));
            setupGetUpdatedBookMocks(1L, target);
            setupFileMoveStubs(target, "target_dir/{title}");

            service.attachBookFiles(1L, List.of(2L, 3L), true);

            assertTrue(Files.exists(tempDir.resolve("target_dir/target.epub")), "Original target file stays");
            assertTrue(Files.exists(tempDir.resolve("target_dir/target_1.epub")), "First source gets _1 suffix");
            assertTrue(Files.exists(tempDir.resolve("target_dir/target_2.epub")), "Second source gets _2 suffix");
        }
    }

    @Nested
    @DisplayName("Result Enrichment")
    class ResultEnrichment {

        @Test
        @DisplayName("Returns enriched Book DTO with progress and shelves")
        void getUpdatedBook_returnsEnrichedDto() throws IOException {
            BookEntity target = createBook(1L);
            createBookFile(10L, target, "target.epub", "sub", true, false);

            BookEntity source = createBook(2L);
            createBookFile(20L, source, "source.epub", "sub2", true, false);

            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(target));
            when(bookRepository.findByIdWithBookFiles(2L)).thenReturn(Optional.of(source));

            when(bookRepository.findAllById(List.of(2L))).thenReturn(List.of(source));

            BookLoreUser user = BookLoreUser.builder().id(1L).username("testuser").build();
            when(authenticationService.getAuthenticatedUser()).thenReturn(user);
            when(userBookProgressRepository.findByUserIdAndBookId(1L, 1L))
                    .thenReturn(Optional.empty());
            when(readingProgressService.fetchUserFileProgress(eq(1L), anySet()))
                    .thenReturn(Map.of());

            Book dto = Book.builder().id(1L).build();
            when(bookMapper.toBook(target)).thenReturn(dto);
            when(bookService.filterShelvesByUserId(any(), eq(1L))).thenReturn(Set.of());

            AttachBookFileResponse result = service.attachBookFiles(1L, List.of(2L), false);

            assertNotNull(result);
            assertEquals(1L, result.updatedBook().getId());
            assertEquals(List.of(2L), result.deletedSourceBookIds());
            verify(readingProgressService).enrichBookWithProgress(eq(dto), any(), any());
            verify(bookService).filterShelvesByUserId(any(), eq(1L));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Source book with mixed book and non-book files: only book files reassigned")
        void attachWithoutFileMove_mixedFiles_onlyBookFormatReassigned() throws IOException {
            BookEntity target = createBook(1L);
            createBookFile(10L, target, "target.epub", "sub", true, false);

            BookEntity source = createBook(2L);
            createBookFile(20L, source, "source.epub", "sub2", true, false);
            createBookFile(21L, source, "cover.jpg", "sub2", false, false);
            createBookFile(22L, source, "metadata.xml", "sub2", false, false);

            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(target));
            when(bookRepository.findByIdWithBookFiles(2L)).thenReturn(Optional.of(source));

            setupGetUpdatedBookMocks(1L, target);

            AttachBookFileResponse result = service.attachBookFiles(1L, List.of(2L), false);

            // Should not delete source since it has remaining non-book files
            verify(bookRepository, never()).deleteAll(anyList());
            assertTrue(result.deletedSourceBookIds().isEmpty());
        }

        @Test
        @DisplayName("Source book with multiple book format files: all reassigned")
        void attachWithoutFileMove_multipleBookFiles_allReassigned() throws IOException {
            BookEntity target = createBook(1L);
            createBookFile(10L, target, "target.epub", "sub", true, false);

            BookEntity source = createBook(2L);
            createBookFile(20L, source, "source.epub", "sub2", true, false);
            createBookFile(21L, source, "source.pdf", "sub2", true, false);

            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(target));
            when(bookRepository.findByIdWithBookFiles(2L)).thenReturn(Optional.of(source));

            when(bookRepository.findAllById(List.of(2L))).thenReturn(List.of(source));
            setupGetUpdatedBookMocks(1L, target);

            service.attachBookFiles(1L, List.of(2L), false);

            verify(bookFileRepository).reassignFilesToBook(eq(1L), anyList());
            verify(bookRepository).deleteAll(List.of(source));
        }

        @Test
        @DisplayName("Empty source book IDs list after filtering self-references")
        void attachBookFiles_emptySourceAfterFilter_noopValidation() {
            BookEntity target = createBook(1L);
            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(target));

            APIException ex = assertThrows(APIException.class,
                    () -> service.attachBookFiles(1L, List.of(1L), false));
            assertTrue(ex.getMessage().contains("Cannot attach a book to itself"));
        }

        @Test
        @DisplayName("Pattern resolves to file at library root (no subdirectory)")
        void attachWithFileMove_patternAtLibraryRoot() throws IOException {
            BookEntity target = createBook(1L);
            createBookFile(10L, target, "target.epub", "", true, false);

            BookEntity source = createBook(2L);
            createBookFile(20L, source, "source.pdf", "source_dir", true, false);
            source.getBookFiles().getFirst().setBookType(BookFileType.PDF);

            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(target));
            when(bookRepository.findByIdWithBookFiles(2L)).thenReturn(Optional.of(source));
            setupGetUpdatedBookMocks(1L, target);
            setupFileMoveStubs(target, "{title}");

            service.attachBookFiles(1L, List.of(2L), true);

            assertTrue(Files.exists(tempDir.resolve("target.pdf")));
        }

        @Test
        @DisplayName("Attach without move keeps file in place and recalculates fileSubPath for different library paths")
        void attachBookFiles_differentLibraryPaths_noMoveRecalculatesSubPath() throws IOException {
            LibraryPathEntity otherLibraryPath = LibraryPathEntity.builder()
                    .id(2L)
                    .library(library)
                    .path(tempDir.resolve("other_root").toString())
                    .build();
            library.setLibraryPaths(new ArrayList<>(List.of(libraryPath, otherLibraryPath)));

            Files.createDirectories(tempDir.resolve("other_root"));

            BookEntity target = createBook(1L);
            createBookFile(10L, target, "target.epub", "target_dir", true, false);

            BookEntity source = BookEntity.builder()
                    .id(2L)
                    .library(library)
                    .libraryPath(otherLibraryPath)
                    .build();
            source.setBookFiles(new ArrayList<>());
            BookFileEntity sourceFile = BookFileEntity.builder()
                    .id(20L)
                    .book(source)
                    .fileName("source.pdf")
                    .fileSubPath("source_dir")
                    .isBookFormat(true)
                    .folderBased(false)
                    .bookType(BookFileType.PDF)
                    .build();
            Path sourceDir = tempDir.resolve("other_root/source_dir");
            Files.createDirectories(sourceDir);
            Files.createFile(sourceDir.resolve("source.pdf"));

            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(target));
            when(bookRepository.findByIdWithBookFiles(2L)).thenReturn(Optional.of(source));
            setupGetUpdatedBookMocks(1L, target);

            AttachBookFileResponse result = service.attachBookFiles(1L, List.of(2L), false);

            assertTrue(Files.exists(sourceDir.resolve("source.pdf")),
                    "Source file should remain at original location");
            verify(bookFileRepository).reassignFileToBookWithPath(eq(1L), anyString(), eq(20L));
            assertEquals(List.of(2L), result.deletedSourceBookIds());
        }

        @Test
        @DisplayName("File without extension handled correctly")
        void attachWithFileMove_fileWithoutExtension() throws IOException {
            BookEntity target = createBook(1L);
            createBookFile(10L, target, "target.epub", "target_dir", true, false);

            BookEntity source = createBook(2L);
            BookFileEntity sourceFile = BookFileEntity.builder()
                    .id(20L)
                    .book(source)
                    .fileName("noextension")
                    .fileSubPath("source_dir")
                    .isBookFormat(true)
                    .folderBased(false)
                    .bookType(BookFileType.EPUB)
                    .build();
            Files.createDirectories(tempDir.resolve("source_dir"));
            Files.createFile(tempDir.resolve("source_dir/noextension"));

            when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(target));
            when(bookRepository.findByIdWithBookFiles(2L)).thenReturn(Optional.of(source));
            setupGetUpdatedBookMocks(1L, target);
            setupFileMoveStubs(target, "target_dir/{title}");

            service.attachBookFiles(1L, List.of(2L), true);

            assertTrue(Files.exists(tempDir.resolve("target_dir/target")),
                    "File without extension should use base name");
        }
    }
}
