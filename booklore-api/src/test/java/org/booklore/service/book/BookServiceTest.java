package org.booklore.service.book;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.APIException;
import org.booklore.mapper.BookMapper;
import org.booklore.model.dto.*;
import org.booklore.model.dto.request.ReadProgressRequest;
import org.booklore.model.dto.response.BookDeletionResponse;
import org.booklore.model.dto.response.BookStatusUpdateResponse;
import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.*;
import org.booklore.service.audit.AuditService;
import org.booklore.service.monitoring.MonitoringRegistrationService;
import org.booklore.service.progress.ReadingProgressService;
import org.booklore.util.FileService;
import org.booklore.util.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock
    private BookRepository bookRepository;
    @Mock
    private PdfViewerPreferencesRepository pdfViewerPreferencesRepository;
    @Mock
    private EbookViewerPreferenceRepository ebookViewerPreferenceRepository;
    @Mock
    private CbxViewerPreferencesRepository cbxViewerPreferencesRepository;
    @Mock
    private NewPdfViewerPreferencesRepository newPdfViewerPreferencesRepository;
    @Mock
    private FileService fileService;
    @Mock
    private BookMapper bookMapper;
    @Mock
    private UserBookProgressRepository userBookProgressRepository;
    @Mock
    private AuthenticationService authenticationService;
    @Mock
    private BookQueryService bookQueryService;
    @Mock
    private ReadingProgressService readingProgressService;
    @Mock
    private BookDownloadService bookDownloadService;
    @Mock
    private MonitoringRegistrationService monitoringRegistrationService;
    @Mock
    private BookUpdateService bookUpdateService;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private BookService bookService;

    private BookLoreUser testUser;

    @BeforeEach
    void setUp() {
        BookLoreUser.UserPermissions perms = new BookLoreUser.UserPermissions();
        perms.setAdmin(true);
        testUser = BookLoreUser.builder()
                .id(1L)
                .permissions(perms)
                .assignedLibraries(List.of())
                .isDefaultPassword(false).build();
    }

    @Test
    void getBookDTOs_adminUser_returnsBooksWithProgress() {
        Book book = Book.builder().id(1L).primaryFile(BookFile.builder().bookType(BookFileType.PDF).build()).shelves(Set.of()).build();
        when(bookQueryService.getAllBooks(anyBoolean())).thenReturn(List.of(book));
        when(readingProgressService.fetchUserProgress(anyLong(), anySet())).thenReturn(Map.of(1L, new UserBookProgressEntity()));
        when(authenticationService.getAuthenticatedUser()).thenReturn(testUser);

        List<Book> result = bookService.getBookDTOs(true);

        assertEquals(1, result.size());
        verify(bookQueryService).getAllBooks(true);
    }

    @Test
    void getBooksByIds_returnsMappedBooksWithProgress() {
        BookEntity entity = new BookEntity();
        entity.setId(2L);
        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setBook(entity);
        primaryFile.setBookType(BookFileType.EPUB);
        entity.setBookFiles(List.of(primaryFile));
        LibraryPathEntity libPath = new LibraryPathEntity();
        libPath.setPath("/tmp/library");
        LibraryEntity library = new LibraryEntity();
        library.setLibraryPaths(List.of(libPath));
        entity.setLibrary(library);
        when(bookQueryService.findAllWithMetadataByIds(anySet())).thenReturn(List.of(entity));
        when(readingProgressService.fetchUserProgress(anyLong(), anySet())).thenReturn(Map.of(2L, new UserBookProgressEntity()));
        Book mappedBook = Book.builder().id(2L).primaryFile(BookFile.builder().bookType(BookFileType.EPUB).build()).metadata(BookMetadata.builder().build()).build();
        when(bookMapper.toBook(entity)).thenReturn(mappedBook);
        when(authenticationService.getAuthenticatedUser()).thenReturn(testUser);

        try (MockedStatic<FileUtils> fileUtilsMock = mockStatic(FileUtils.class)) {
            fileUtilsMock.when(() -> FileUtils.getBookFullPath(entity)).thenReturn("/tmp/library/book.epub");
            List<Book> result = bookService.getBooksByIds(Set.of(2L), false);

            assertEquals(1, result.size());
            assertEquals(2L, result.getFirst().getId());
        }
    }

    @Test
    void getBook_existingBook_returnsBookWithProgress() {
        BookEntity entity = new BookEntity();
        entity.setId(3L);
        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setBook(entity);
        primaryFile.setBookType(BookFileType.PDF);
        entity.setBookFiles(List.of(primaryFile));
        LibraryPathEntity libPath = new LibraryPathEntity();
        libPath.setPath("/tmp/library");
        LibraryEntity library = new LibraryEntity();
        library.setLibraryPaths(List.of(libPath));
        entity.setLibrary(library);
        when(bookRepository.findByIdWithBookFiles(3L)).thenReturn(Optional.of(entity));
        when(userBookProgressRepository.findByUserIdAndBookId(anyLong(), eq(3L))).thenReturn(Optional.of(new UserBookProgressEntity()));
        Book mappedBook = Book.builder().id(3L).primaryFile(BookFile.builder().bookType(BookFileType.PDF).build()).metadata(BookMetadata.builder().build()).shelves(Set.of()).build();
        when(bookMapper.toBook(entity)).thenReturn(mappedBook);
        when(authenticationService.getAuthenticatedUser()).thenReturn(testUser);

        try (MockedStatic<FileUtils> fileUtilsMock = mockStatic(FileUtils.class)) {
            fileUtilsMock.when(() -> FileUtils.getBookFullPath(entity)).thenReturn("/tmp/library/book.pdf");
            Book result = bookService.getBook(3L, true);
            assertEquals(3L, result.getId());
            verify(bookRepository).findByIdWithBookFiles(3L);
        }
    }

    @Test
    void getBook_notFound_throwsException() {
        when(bookRepository.findByIdWithBookFiles(99L)).thenReturn(Optional.empty());
        when(authenticationService.getAuthenticatedUser()).thenReturn(testUser);
        assertThrows(APIException.class, () -> bookService.getBook(99L, true));
    }

    @Test
    void getBookViewerSetting_epub_returnsEpubSettings() {
        BookEntity entity = new BookEntity();
        entity.setId(4L);
        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setId(1L);
        primaryFile.setBook(entity);
        primaryFile.setBookType(BookFileType.EPUB);
        entity.setBookFiles(List.of(primaryFile));
        when(bookRepository.findByIdWithBookFiles(4L)).thenReturn(Optional.of(entity));
        EbookViewerPreferenceEntity epubPref = new EbookViewerPreferenceEntity();
        epubPref.setFontFamily("Arial");
        epubPref.setFontSize(16);
        epubPref.setGap(0.2f);
        epubPref.setHyphenate(true);
        epubPref.setIsDark(false);
        epubPref.setJustify(true);
        epubPref.setLineHeight(1.5f);
        epubPref.setMaxBlockSize(800);
        epubPref.setMaxColumnCount(2);
        epubPref.setMaxInlineSize(1200);
        epubPref.setTheme("light");
        when(ebookViewerPreferenceRepository.findByBookIdAndUserId(4L, testUser.getId())).thenReturn(Optional.of(epubPref));
        when(authenticationService.getAuthenticatedUser()).thenReturn(testUser);

        BookViewerSettings settings = bookService.getBookViewerSetting(4L, 1L);

        assertNotNull(settings.getEbookSettings());
        assertEquals("Arial", settings.getEbookSettings().getFontFamily());
        assertEquals(16, settings.getEbookSettings().getFontSize());
        assertEquals(0.2f, settings.getEbookSettings().getGap());
        assertTrue(settings.getEbookSettings().getHyphenate());
        assertFalse(settings.getEbookSettings().getIsDark());
        assertTrue(settings.getEbookSettings().getJustify());
        assertEquals(1.5f, settings.getEbookSettings().getLineHeight());
        assertEquals(800, settings.getEbookSettings().getMaxBlockSize());
        assertEquals(2, settings.getEbookSettings().getMaxColumnCount());
        assertEquals(1200, settings.getEbookSettings().getMaxInlineSize());
        assertEquals("light", settings.getEbookSettings().getTheme());
    }

    @Test
    void getBookViewerSetting_unsupportedType_throwsException() {
        BookEntity entity = new BookEntity();
        entity.setId(5L);
        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setId(1L);
        primaryFile.setBook(entity);
        primaryFile.setBookType(null);
        entity.setBookFiles(List.of(primaryFile));
        when(bookRepository.findByIdWithBookFiles(5L)).thenReturn(Optional.of(entity));
        when(authenticationService.getAuthenticatedUser()).thenReturn(testUser);
        assertThrows(APIException.class, () -> bookService.getBookViewerSetting(5L, 1L));
    }

    @Test
    void updateBookViewerSetting_delegatesToUpdateService() {
        BookViewerSettings settings = BookViewerSettings.builder().build();
        bookService.updateBookViewerSetting(1L, settings);
        verify(bookUpdateService).updateBookViewerSetting(1L, settings);
    }

    @Test
    void updateReadProgress_delegatesToReadingProgressService() {
        ReadProgressRequest req = new ReadProgressRequest();
        bookService.updateReadProgress(req);
        verify(readingProgressService).updateReadProgress(req);
    }

    @Test
    void updateReadStatus_delegatesToUpdateService() {
        List<Long> ids = List.of(1L, 2L);
        List<BookStatusUpdateResponse> responses = List.of(new BookStatusUpdateResponse());
        when(bookUpdateService.updateReadStatus(ids, "READ")).thenReturn(responses);

        List<BookStatusUpdateResponse> result = bookService.updateReadStatus(ids, "READ");

        assertEquals(responses, result);
    }

    @Test
    void assignShelvesToBooks_delegatesToUpdateService() {
        Set<Long> bookIds = Set.of(1L);
        Set<Long> assign = Set.of(2L);
        Set<Long> unassign = Set.of(3L);
        List<Book> books = List.of(Book.builder().id(1L).build());
        when(bookUpdateService.assignShelvesToBooks(bookIds, assign, unassign)).thenReturn(books);

        List<Book> result = bookService.assignShelvesToBooks(bookIds, assign, unassign);

        assertEquals(books, result);
    }

    @Test
    void getBookThumbnail_fileExists_returnsUrlResource() throws Exception {
        when(fileService.getThumbnailFile(1L)).thenReturn("/tmp/cover.jpg");
        Path path = Paths.get("/tmp/cover.jpg");
        Files.createFile(path);
        try {
            Resource res = bookService.getBookThumbnail(1L);
            assertTrue(res instanceof UrlResource);
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    void getBookThumbnail_fileMissing_returnsDefault() {
        when(fileService.getThumbnailFile(1L)).thenReturn("/tmp/nonexistent.jpg");
        Resource res = bookService.getBookThumbnail(1L);
    }

    @Test
    void getBookThumbnail_malformedPath_throwsRuntimeException() {
        when(fileService.getThumbnailFile(123L)).thenReturn("\0illegal:path");
        assertThrows(RuntimeException.class, () -> bookService.getBookThumbnail(123L));
    }

    @Test
    void getBookCover_fileExists_returnsUrlResource() throws Exception {
        when(fileService.getCoverFile(1L)).thenReturn("/tmp/cover2.jpg");
        Path path = Paths.get("/tmp/cover2.jpg");
        Files.createFile(path);
        try {
            Resource res = bookService.getBookCover(1L);
            assertTrue(res instanceof UrlResource);
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    void getBookCover_fileMissing_returnsByteArrayResource() {
        when(fileService.getCoverFile(1L)).thenReturn("/tmp/nonexistent2.jpg");
        Resource res = bookService.getBookCover(1L);
        assertTrue(res instanceof ByteArrayResource);
    }

    @Test
    void getBookCover_malformedPath_throwsRuntimeException() {
        when(fileService.getCoverFile(123L)).thenReturn("\0illegal:path");
        assertThrows(RuntimeException.class, () -> bookService.getBookCover(123L));
    }

    @Test
    void downloadBook_delegatesToDownloadService() {
        ResponseEntity<Resource> response = ResponseEntity.ok(mock(Resource.class));
        when(bookDownloadService.downloadBook(1L)).thenReturn(response);

        ResponseEntity<Resource> result = bookService.downloadBook(1L);

        assertEquals(response, result);
    }

    @Test
    void getBookContent_returnsResource() throws Exception {
        BookEntity entity = new BookEntity();
        entity.setId(10L);
        when(bookRepository.findByIdWithBookFiles(10L)).thenReturn(Optional.of(entity));
        Path path = Paths.get("/tmp/bookcontent.txt");
        Files.write(path, "hello".getBytes());
        try (MockedStatic<FileUtils> fileUtilsMock = mockStatic(FileUtils.class)) {
            fileUtilsMock.when(() -> FileUtils.getBookFullPath(entity)).thenReturn(path.toString());
            ResponseEntity<Resource> response = bookService.getBookContent(10L);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertArrayEquals("hello".getBytes(), response.getBody().getInputStream().readAllBytes());
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    void getBookContent_bookNotFound_throwsException() {
        when(bookRepository.findByIdWithBookFiles(404L)).thenReturn(Optional.empty());
        assertThrows(APIException.class, () -> bookService.getBookContent(404L));
    }

    @Test
    void getBookContent_fileNotFound_throwsException() {
        BookEntity entity = new BookEntity();
        entity.setId(12L);
        when(bookRepository.findByIdWithBookFiles(12L)).thenReturn(Optional.of(entity));
        try (MockedStatic<FileUtils> fileUtilsMock = mockStatic(FileUtils.class)) {
            fileUtilsMock.when(() -> FileUtils.getBookFullPath(entity)).thenReturn("/tmp/nonexistentfile.txt");
            assertThrows(APIException.class, () -> bookService.getBookContent(12L));
        }
    }

    @Test
    void deleteBooks_deletesFilesAndEntities() throws Exception {
        BookEntity entity = new BookEntity();
        entity.setId(11L);
        LibraryEntity library = new LibraryEntity();
        library.setId(42L);
        LibraryPathEntity libPath = new LibraryPathEntity();
        libPath.setPath("/tmp");
        library.setLibraryPaths(List.of(libPath));
        entity.setLibrary(library);
        entity.setLibraryPath(libPath);
        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setBook(entity);
        primaryFile.setFileSubPath("");
        primaryFile.setFileName("bookfile.txt");
        entity.setBookFiles(List.of(primaryFile));

        Path filePath = Paths.get("/tmp/bookfile.txt");
        Files.createDirectories(filePath.getParent());
        Files.write(filePath, "abc".getBytes());

        doNothing().when(bookRepository).deleteAllInBatch(anyList());
        when(bookQueryService.findAllWithMetadataByIds(Set.of(11L))).thenReturn(List.of(entity));
        when(authenticationService.getAuthenticatedUser()).thenReturn(testUser);

        BookDeletionResponse response = bookService.deleteBooks(Set.of(11L)).getBody();

        assertNotNull(response);
        assertTrue(response.getFailedFileDeletions().isEmpty());
        assertEquals(Set.of(11L), response.getDeleted());
        Files.deleteIfExists(filePath);
    }

    @Test
    void deleteBooks_fileDoesNotExist_deletesEntityOnly() {
        BookEntity entity = new BookEntity();
        entity.setId(13L);
        LibraryEntity library = new LibraryEntity();
        library.setId(42L);
        LibraryPathEntity libPath = new LibraryPathEntity();
        libPath.setPath("/tmp");
        library.setLibraryPaths(List.of(libPath));
        entity.setLibrary(library);
        entity.setLibraryPath(libPath);
        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setBook(entity);
        primaryFile.setFileSubPath("");
        primaryFile.setFileName("nonexistentfile.txt");
        entity.setBookFiles(List.of(primaryFile));

        when(bookQueryService.findAllWithMetadataByIds(Set.of(13L))).thenReturn(List.of(entity));
        when(authenticationService.getAuthenticatedUser()).thenReturn(testUser);
        doNothing().when(bookRepository).deleteAllInBatch(anyList());

        BookDeletionResponse response = bookService.deleteBooks(Set.of(13L)).getBody();

        assertNotNull(response);
        assertTrue(response.getFailedFileDeletions().isEmpty());
        assertEquals(Set.of(13L), response.getDeleted());
    }

    @Test
    void deleteEmptyParentDirsUpToLibraryFolders_deletesEmptyDirs() throws Exception {
        Path root = Files.createTempDirectory("libroot");
        Path subdir = Files.createDirectory(root.resolve("subdir"));
        Path file = subdir.resolve(".DS_Store");
        Files.createFile(file);

        Set<Path> roots = Set.of(root);
        bookService.deleteEmptyParentDirsUpToLibraryFolders(subdir, roots);

        assertFalse(Files.exists(subdir));
        Files.deleteIfExists(root);
    }

    @Test
    void filterShelvesByUserId_returnsOnlyUserShelves() {
        Shelf shelf1 = Shelf.builder().id(1L).userId(1L).build();
        Shelf shelf2 = Shelf.builder().id(2L).userId(2L).build();
        Set<Shelf> shelves = Set.of(shelf1, shelf2);

        Set<Shelf> result = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                bookService, "filterShelvesByUserId", shelves, 1L);

        assertEquals(1, result.size());
        assertTrue(result.contains(shelf1));
    }
    @Mock
    private org.booklore.service.FileStreamingService fileStreamingService;

    @Test
    void streamBookContent_delegatesToStreamingService() throws Exception {
        BookEntity entity = new BookEntity();
        entity.setId(15L);
        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setBook(entity);
        primaryFile.setBookType(BookFileType.EPUB);
        primaryFile.setFileSubPath("");
        primaryFile.setFileName("book.epub");
        // Ensure bookFiles list is populated to avoid IndexOutOfBounds or similar if logic accesses it
        entity.setBookFiles(List.of(primaryFile));

        when(bookRepository.findByIdWithBookFiles(15L)).thenReturn(Optional.of(entity));

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        // We don't need to mock FileUtils static method if we set up the entity correctly
        // But streamBookContent calls FileUtils.getBookFullPath(bookEntity) if bookType is null.
        // Let's passed bookType="EPUB" to hit the specific branch, or null to hit default.
        // The code uses:
        // if (bookType != null) { ... } else { filePath = FileUtils.getBookFullPath(bookEntity); }
        // Let's test the specific type path first as it isolates logic better,
        // OR test the null path which is likely what the browser uses.
        // The original error was "Cannot lazily initialize collection... getPrimaryBookFile... FileUtils.getBookFullPath"
        // So I should test the `bookType = null` case which triggers `FileUtils.getBookFullPath`.

        // However, `FileUtils.getBookFullPath` is static. I may need to mock it or ensure it works with the entity.
        // `FileUtils.getBookFullPath` calls `book.getPrimaryBookFile()`.

        try (MockedStatic<FileUtils> fileUtilsMock = mockStatic(FileUtils.class)) {
            fileUtilsMock.when(() -> FileUtils.getBookFullPath(entity)).thenReturn("/tmp/library/book.epub");

            bookService.streamBookContent(15L, null, request, response);

            verify(bookRepository).findByIdWithBookFiles(15L);
            verify(fileStreamingService).streamWithRangeSupport(eq(Paths.get("/tmp/library/book.epub")), eq("application/epub+zip"), eq(request), eq(response));
        }
    }

}
