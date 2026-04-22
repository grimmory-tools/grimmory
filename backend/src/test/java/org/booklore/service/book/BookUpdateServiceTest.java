package org.booklore.service.book;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.APIException;
import org.booklore.mapper.BookMapper;
import org.booklore.model.dto.*;
import org.booklore.model.dto.response.BookStatusUpdateResponse;
import org.booklore.model.dto.response.PersonalRatingUpdateResponse;
import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.ReadStatus;
import org.booklore.repository.*;
import org.booklore.service.progress.ReadingProgressService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookUpdateServiceTest {

    @Mock
    private BookRepository bookRepository;
    @Mock
    private PdfViewerPreferencesRepository pdfViewerPreferencesRepository;
    @Mock
    private CbxViewerPreferencesRepository cbxViewerPreferencesRepository;
    @Mock
    private NewPdfViewerPreferencesRepository newPdfViewerPreferencesRepository;
    @Mock
    private ShelfRepository shelfRepository;
    @Mock
    private BookMapper bookMapper;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserBookProgressRepository userBookProgressRepository;
    @Mock
    private AuthenticationService authenticationService;
    @Mock
    private BookQueryService bookQueryService;
    @Mock
    private ReadingProgressService readingProgressService;
    @Mock
    private EbookViewerPreferenceRepository ebookViewerPreferenceRepository;

    @InjectMocks
    private BookUpdateService bookUpdateService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        bookUpdateService = new BookUpdateService(
                bookRepository,
                pdfViewerPreferencesRepository,
                cbxViewerPreferencesRepository,
                newPdfViewerPreferencesRepository,
                shelfRepository,
                bookMapper,
                userRepository,
                userBookProgressRepository,
                authenticationService,
                bookQueryService,
                readingProgressService,
                ebookViewerPreferenceRepository
        );
    }

    @Test
    void updateBookViewerSetting_pdf_shouldUpdatePdfPrefs() {
        long bookId = 1L;
        BookEntity book = new BookEntity();
        book.setId(bookId);
        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setBook(book);
        primaryFile.setBookType(BookFileType.PDF);
        book.setBookFiles(List.of(primaryFile));
        BookLoreUser user = mock(BookLoreUser.class);
        when(bookRepository.findByIdWithBookFiles(bookId)).thenReturn(Optional.of(book));
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(user.getId()).thenReturn(2L);

        PdfViewerPreferencesEntity pdfPrefs = new PdfViewerPreferencesEntity();
        when(pdfViewerPreferencesRepository.findByBookIdAndUserId(bookId, 2L)).thenReturn(Optional.of(pdfPrefs));
        BookViewerSettings settings = BookViewerSettings.builder()
                .pdfSettings(PdfViewerPreferences.builder()
                        .zoom("1.5")
                        .spread("spread")
                        .build())
                .build();

        bookUpdateService.updateBookViewerSetting(bookId, settings);

        verify(pdfViewerPreferencesRepository).save(pdfPrefs);
        assertEquals("1.5", pdfPrefs.getZoom());
        assertEquals("spread", pdfPrefs.getSpread());
    }

    @Test
    void updateBookViewerSetting_epub_shouldUpdateEpubPrefsV2() {
        long bookId = 1L;
        BookEntity book = new BookEntity();
        book.setId(bookId);
        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setBook(book);
        primaryFile.setBookType(BookFileType.EPUB);
        book.setBookFiles(List.of(primaryFile));
        BookLoreUser user = mock(BookLoreUser.class);
        when(bookRepository.findByIdWithBookFiles(bookId)).thenReturn(Optional.of(book));
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(user.getId()).thenReturn(2L);

        EbookViewerPreferenceEntity epubPrefs = new EbookViewerPreferenceEntity();
        when(ebookViewerPreferenceRepository.findByBookIdAndUserId(bookId, 2L)).thenReturn(Optional.of(epubPrefs));
        BookViewerSettings settings = BookViewerSettings.builder()
                .ebookSettings(EbookViewerPreferences.builder()
                        .fontFamily("serif")
                        .fontSize(18)
                        .gap(0.1f)
                        .hyphenate(true)
                        .isDark(true)
                        .justify(true)
                        .lineHeight(1.7f)
                        .maxBlockSize(800)
                        .maxColumnCount(3)
                        .maxInlineSize(1200)
                        .theme("dark")
                        .flow("paginated")
                        .build())
                .build();

        bookUpdateService.updateBookViewerSetting(bookId, settings);

        verify(ebookViewerPreferenceRepository).save(epubPrefs);
        assertEquals("serif", epubPrefs.getFontFamily());
        assertEquals(18, epubPrefs.getFontSize());
        assertEquals(0.1f, epubPrefs.getGap());
        assertTrue(epubPrefs.getHyphenate());
        assertTrue(epubPrefs.getIsDark());
        assertTrue(epubPrefs.getJustify());
        assertEquals(1.7f, epubPrefs.getLineHeight());
        assertEquals(800, epubPrefs.getMaxBlockSize());
        assertEquals(3, epubPrefs.getMaxColumnCount());
        assertEquals(1200, epubPrefs.getMaxInlineSize());
        assertEquals("dark", epubPrefs.getTheme());
        assertEquals("paginated", epubPrefs.getFlow());
    }

    @Test
    void updateBookViewerSetting_unsupportedType_shouldThrow() {
        long bookId = 1L;
        BookEntity book = new BookEntity();
        book.setId(bookId);
        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setBook(book);
        primaryFile.setBookType(null);
        book.setBookFiles(List.of(primaryFile));
        when(bookRepository.findByIdWithBookFiles(bookId)).thenReturn(Optional.of(book));
        when(authenticationService.getAuthenticatedUser()).thenReturn(mock(BookLoreUser.class));
        BookViewerSettings settings = BookViewerSettings.builder().build();

        assertThrows(APIException.class, () -> bookUpdateService.updateBookViewerSetting(bookId, settings));
    }

    @Test
    void updateReadStatus_shouldUpdateExistingAndCreateNew() {
        BookLoreUser user = mock(BookLoreUser.class);
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(user.getId()).thenReturn(1L);

        BookLoreUser.UserPermissions permissions = mock(BookLoreUser.UserPermissions.class);
        when(user.getPermissions()).thenReturn(permissions);
        when(permissions.isCanBulkResetBookReadStatus()).thenReturn(true);

        List<Long> bookIds = Arrays.asList(1L, 2L, 3L);
        when(bookRepository.countByIdIn(bookIds)).thenReturn(3L);
        Set<Long> existing = new HashSet<>(Arrays.asList(1L, 2L));
        when(userBookProgressRepository.findExistingProgressBookIds(1L, new HashSet<>(bookIds))).thenReturn(existing);

        BookLoreUserEntity userEntity = new BookLoreUserEntity();
        when(userRepository.findById(1L)).thenReturn(Optional.of(userEntity));

        List<BookStatusUpdateResponse> result = bookUpdateService.updateReadStatus(bookIds, "READ");
        verify(userBookProgressRepository).bulkUpdateReadStatus(eq(1L), eq(new ArrayList<>(existing)), eq(ReadStatus.READ), any(), any());
        verify(userBookProgressRepository).saveAll(anyList());
        assertEquals(3, result.size());
        assertEquals(ReadStatus.READ, result.getFirst().getReadStatus());
    }

    @Test
    void updateReadStatus_shouldThrowIfNoBulkPermission() {
        BookLoreUser user = mock(BookLoreUser.class);
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(user.getId()).thenReturn(1L);

        BookLoreUser.UserPermissions permissions = mock(BookLoreUser.UserPermissions.class);
        when(user.getPermissions()).thenReturn(permissions);
        when(permissions.isCanBulkResetBookReadStatus()).thenReturn(false);

        List<Long> bookIds = Arrays.asList(1L, 2L, 3L);
        when(bookRepository.countByIdIn(bookIds)).thenReturn(3L);

        assertThrows(APIException.class, () -> bookUpdateService.updateReadStatus(bookIds, "READ"));
    }

    @Test
    void updateReadStatus_shouldAllowSingleBookWithoutPermission() {
        BookLoreUser user = mock(BookLoreUser.class);
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(user.getId()).thenReturn(1L);

        BookLoreUser.UserPermissions permissions = mock(BookLoreUser.UserPermissions.class);
        when(user.getPermissions()).thenReturn(permissions);
        when(permissions.isCanBulkResetBookReadStatus()).thenReturn(false);

        List<Long> bookIds = Collections.singletonList(1L);
        when(bookRepository.countByIdIn(bookIds)).thenReturn(1L);
        Set<Long> existing = new HashSet<>(bookIds);
        when(userBookProgressRepository.findExistingProgressBookIds(1L, new HashSet<>(bookIds))).thenReturn(existing);

        BookLoreUserEntity userEntity = new BookLoreUserEntity();
        when(userRepository.findById(1L)).thenReturn(Optional.of(userEntity));

        assertDoesNotThrow(() -> bookUpdateService.updateReadStatus(bookIds, "READ"));
    }

    @Test
    void updatePersonalRating_shouldUpdateAndCreate() {
        BookLoreUser user = mock(BookLoreUser.class);
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(user.getId()).thenReturn(1L);

        List<Long> bookIds = Arrays.asList(1L, 2L, 3L);
        when(bookRepository.countByIdIn(bookIds)).thenReturn(3L);
        Set<Long> existing = new HashSet<>(Arrays.asList(1L, 2L));
        when(userBookProgressRepository.findExistingProgressBookIds(1L, new HashSet<>(bookIds))).thenReturn(existing);

        BookLoreUserEntity userEntity = new BookLoreUserEntity();
        when(userRepository.findById(1L)).thenReturn(Optional.of(userEntity));

        List<PersonalRatingUpdateResponse> result = bookUpdateService.updatePersonalRating(bookIds, 5);
        verify(userBookProgressRepository).bulkUpdatePersonalRating(eq(1L), eq(new ArrayList<>(existing)), eq(5));
        verify(userBookProgressRepository).saveAll(anyList());
        assertEquals(3, result.size());
        assertEquals(5, result.getFirst().getPersonalRating());
    }

    @Test
    void resetPersonalRating_shouldUpdateExisting() {
        BookLoreUser user = mock(BookLoreUser.class);
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(user.getId()).thenReturn(1L);

        List<Long> bookIds = Arrays.asList(1L, 2L);
        when(bookRepository.countByIdIn(bookIds)).thenReturn(2L);
        Set<Long> existing = new HashSet<>(bookIds);
        when(userBookProgressRepository.findExistingProgressBookIds(1L, new HashSet<>(bookIds))).thenReturn(existing);

        List<PersonalRatingUpdateResponse> result = bookUpdateService.resetPersonalRating(bookIds);
        verify(userBookProgressRepository).bulkUpdatePersonalRating(eq(1L), eq(new ArrayList<>(bookIds)), isNull());
        assertEquals(2, result.size());
        assertNull(result.getFirst().getPersonalRating());
    }

    @Test
    void assignShelvesToBooks_shouldAssignAndReturnBooks() {
        BookLoreUser user = mock(BookLoreUser.class);
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(user.getId()).thenReturn(1L);

        BookLoreUserEntity userEntity = new BookLoreUserEntity();
        ShelfEntity shelf1 = new ShelfEntity();
        shelf1.setId(10L);
        ShelfEntity shelf2 = new ShelfEntity();
        shelf2.setId(20L);
        userEntity.setShelves(new HashSet<>(Arrays.asList(shelf1, shelf2)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(userEntity));

        Set<Long> bookIds = new HashSet<>(Arrays.asList(1L, 2L));
        Set<Long> assignIds = new HashSet<>(Collections.singletonList(10L));
        Set<Long> unassignIds = new HashSet<>(Collections.singletonList(20L));

        BookEntity bookEntity1 = spy(new BookEntity());
        bookEntity1.setId(1L);
        bookEntity1.setShelves(new HashSet<>());
        LibraryPathEntity libraryPath1 = new LibraryPathEntity();
        libraryPath1.setPath("/mock/path/1");
        doReturn(libraryPath1).when(bookEntity1).getLibraryPath();
        BookFileEntity bookFileEntity1 = new BookFileEntity();
        bookFileEntity1.setBook(bookEntity1);
        bookFileEntity1.setFileSubPath("sub1");
        bookFileEntity1.setFileName("file1.pdf");
        bookEntity1.setBookFiles(List.of(bookFileEntity1));

        BookEntity bookEntity2 = spy(new BookEntity());
        bookEntity2.setId(2L);
        bookEntity2.setShelves(new HashSet<>());
        LibraryPathEntity libraryPath2 = new LibraryPathEntity();
        libraryPath2.setPath("/mock/path/2");
        doReturn(libraryPath2).when(bookEntity2).getLibraryPath();
        BookFileEntity bookFileEntity2 = new BookFileEntity();
        bookFileEntity2.setBook(bookEntity2);
        bookFileEntity2.setFileSubPath("sub2");
        bookFileEntity2.setFileName("file2.pdf");
        bookEntity2.setBookFiles(List.of(bookFileEntity2));

        when(bookQueryService.findAllWithMetadataByIds(bookIds)).thenReturn(Arrays.asList(bookEntity1, bookEntity2));
        ShelfEntity assignShelf = new ShelfEntity();
        assignShelf.setId(10L);
        when(shelfRepository.findAllById(assignIds)).thenReturn(Collections.singletonList(assignShelf));
        Book mockBook = mock(Book.class);
        when(bookMapper.toBook(any())).thenReturn(mockBook);

        when(readingProgressService.fetchUserProgress(eq(1L), anySet())).thenReturn(Collections.emptyMap());
        when(readingProgressService.fetchUserFileProgress(eq(1L), anySet())).thenReturn(Collections.emptyMap());

        List<Book> result = bookUpdateService.assignShelvesToBooks(bookIds, assignIds, unassignIds);
        verify(bookRepository).saveAll(anyList());
        assertEquals(2, result.size());
    }

    @Test
    void assignShelvesToBooks_shouldThrowIfUnauthorized() {
        BookLoreUser user = mock(BookLoreUser.class);
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(user.getId()).thenReturn(1L);

        BookLoreUserEntity userEntity = new BookLoreUserEntity();
        ShelfEntity shelf1 = new ShelfEntity();
        shelf1.setId(10L);
        userEntity.setShelves(new HashSet<>(Collections.singletonList(shelf1)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(userEntity));

        Set<Long> bookIds = new HashSet<>(Collections.singletonList(1L));
        Set<Long> assignIds = new HashSet<>(Collections.singletonList(99L));
        Set<Long> unassignIds = new HashSet<>();

        assertThrows(APIException.class, () -> bookUpdateService.assignShelvesToBooks(bookIds, assignIds, unassignIds));
    }
}
