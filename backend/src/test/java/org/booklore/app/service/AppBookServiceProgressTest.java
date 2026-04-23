package org.booklore.app.service;

import jakarta.persistence.EntityManager;
import org.booklore.app.dto.UpdateProgressRequest;
import org.booklore.app.mapper.AppBookMapper;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.APIException;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.Library;
import org.booklore.model.dto.request.BookFileProgress;
import org.booklore.model.dto.request.ReadProgressRequest;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.LibraryEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.booklore.repository.BookRepository;
import org.booklore.repository.LibraryRepository;
import org.booklore.repository.ShelfRepository;
import org.booklore.repository.UserBookFileProgressRepository;
import org.booklore.repository.UserBookProgressRepository;
import org.booklore.service.book.BookService;
import org.booklore.service.opds.MagicShelfBookService;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppBookServiceProgressTest {

    @Mock private BookRepository bookRepository;
    @Mock private LibraryRepository libraryRepository;
    @Mock private UserBookProgressRepository userBookProgressRepository;
    @Mock private UserBookFileProgressRepository userBookFileProgressRepository;
    @Mock private ShelfRepository shelfRepository;
    @Mock private AuthenticationService authenticationService;
    @Mock private AppBookMapper mobileBookMapper;
    @Mock private BookService bookService;
    @Mock private MagicShelfBookService magicShelfBookService;
    @Mock private EntityManager entityManager;

    private AppBookService service;

    private final Long userId = 1L;
    private final Long bookId = 42L;
    private final Long libraryId = 5L;

    @BeforeEach
    void setUp() {
        service = new AppBookService(
                bookRepository, libraryRepository, userBookProgressRepository, userBookFileProgressRepository,
                shelfRepository, authenticationService, mobileBookMapper,
                bookService, magicShelfBookService, entityManager
        );
    }

    // -------------------------------------------------------------------------
    // updateBookProgress — success
    // -------------------------------------------------------------------------

    @Test
    void updateBookProgress_success_delegatesToBookServiceWithPathBookId() {
        mockAdminUser();
        mockBookWithLibrary(bookId, libraryId);

        UpdateProgressRequest request = new UpdateProgressRequest();
        request.setFileProgress(new BookFileProgress(1L, "pos", "href", 0.5f, null, null));

        service.updateBookProgress(bookId, request);

        ArgumentCaptor<ReadProgressRequest> captor = ArgumentCaptor.forClass(ReadProgressRequest.class);
        verify(bookService).updateReadProgress(captor.capture());

        ReadProgressRequest delegated = captor.getValue();
        assertEquals(bookId, delegated.getBookId());
        assertNotNull(delegated.getFileProgress());
        assertEquals(0.5f, delegated.getFileProgress().progressPercent());
    }

    @Test
    void updateBookProgress_success_mapsAllFieldsFromAppRequest() {
        mockAdminUser();
        mockBookWithLibrary(bookId, libraryId);

        UpdateProgressRequest request = new UpdateProgressRequest();
        request.setFileProgress(new BookFileProgress(1L, null, null, 0.75f, null, null));

        service.updateBookProgress(bookId, request);

        ArgumentCaptor<ReadProgressRequest> captor = ArgumentCaptor.forClass(ReadProgressRequest.class);
        verify(bookService).updateReadProgress(captor.capture());

        ReadProgressRequest delegated = captor.getValue();
        assertEquals(bookId, delegated.getBookId());
        assertEquals(0.75f, delegated.getFileProgress().progressPercent());
        assertNull(delegated.getEpubProgress());
        assertNull(delegated.getPdfProgress());
        assertNull(delegated.getCbxProgress());
        assertNull(delegated.getAudiobookProgress());
    }

    // -------------------------------------------------------------------------
    // updateBookProgress — forbidden access
    // -------------------------------------------------------------------------

    @Test
    void updateBookProgress_nonAdminWithoutAccess_throwsForbidden() {
        mockNonAdminUser(Set.of(99L));
        mockBookWithLibrary(bookId, libraryId);

        UpdateProgressRequest request = new UpdateProgressRequest();
        request.setFileProgress(new BookFileProgress(1L, null, null, 0.5f, null, null));

        assertThrows(APIException.class, () -> service.updateBookProgress(bookId, request));
        verify(bookService, never()).updateReadProgress(any());
    }

    @Test
    void updateBookProgress_nonAdminWithAccess_succeeds() {
        mockNonAdminUser(Set.of(libraryId));
        mockBookWithLibrary(bookId, libraryId);

        UpdateProgressRequest request = new UpdateProgressRequest();
        request.setFileProgress(new BookFileProgress(1L, null, null, 0.5f, null, null));

        service.updateBookProgress(bookId, request);

        verify(bookService).updateReadProgress(any(ReadProgressRequest.class));
    }

    // -------------------------------------------------------------------------
    // updateBookProgress — book not found
    // -------------------------------------------------------------------------

    @Test
    void updateBookProgress_bookNotFound_throwsException() {
        mockAdminUser();
        when(bookRepository.findByIdWithBookFiles(bookId)).thenReturn(Optional.empty());

        UpdateProgressRequest request = new UpdateProgressRequest();
        request.setFileProgress(new BookFileProgress(1L, null, null, 0.5f, null, null));

        assertThrows(APIException.class, () -> service.updateBookProgress(bookId, request));
        verify(bookService, never()).updateReadProgress(any());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void mockAdminUser() {
        var permissions = new BookLoreUser.UserPermissions();
        permissions.setAdmin(true);
        BookLoreUser user = BookLoreUser.builder()
                .id(userId)
                .permissions(permissions)
                .build();
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(libraryRepository.findAll()).thenReturn(List.of(LibraryEntity.builder().id(libraryId).build()));
    }

    private void mockNonAdminUser(Set<Long> libraryIds) {
        List<Library> assignedLibraries = libraryIds.stream()
                .map(id -> Library.builder().id(id).build())
                .toList();
        var permissions = new BookLoreUser.UserPermissions();
        permissions.setAdmin(false);
        BookLoreUser user = BookLoreUser.builder()
                .id(userId)
                .permissions(permissions)
                .assignedLibraries(assignedLibraries)
                .build();
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
    }

    private void mockBookWithLibrary(Long bookId, Long libraryId) {
        LibraryEntity library = LibraryEntity.builder().id(libraryId).build();
        BookEntity book = BookEntity.builder().id(bookId).library(library).build();
        when(bookRepository.findByIdWithBookFiles(bookId)).thenReturn(Optional.of(book));
    }
}
