package org.booklore.app.service;

import jakarta.persistence.EntityManager;
import org.booklore.app.dto.AppBookSummary;
import org.booklore.app.dto.AppPageResponse;
import org.booklore.app.dto.BookListRequest;
import org.booklore.app.mapper.AppBookMapper;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.entity.AuthorEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.repository.BookRepository;
import org.booklore.repository.ShelfRepository;
import org.booklore.repository.UserBookFileProgressRepository;
import org.booklore.repository.UserBookProgressRepository;
import org.booklore.service.book.BookService;
import org.booklore.service.opds.MagicShelfBookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppBookServiceSortingTest {

    @Mock private BookRepository bookRepository;
    @Mock private UserBookProgressRepository userBookProgressRepository;
    @Mock private UserBookFileProgressRepository userBookFileProgressRepository;
    @Mock private ShelfRepository shelfRepository;
    @Mock private AuthenticationService authenticationService;
    @Mock private AppBookMapper appBookMapper;
    @Mock private BookService bookService;
    @Mock private MagicShelfBookService magicShelfBookService;
    @Mock private EntityManager entityManager;

    private AppBookService service;

    @BeforeEach
    void setUp() {
        service = new AppBookService(
                bookRepository, userBookProgressRepository, userBookFileProgressRepository,
                shelfRepository, authenticationService, appBookMapper,
                bookService, magicShelfBookService, entityManager
        );
        mockAdminUser();
        when(appBookMapper.toSummary(any(BookEntity.class), any()))
                .thenAnswer(invocation -> AppBookSummary.builder()
                        .id(((BookEntity) invocation.getArgument(0)).getId())
                        .build());
    }

    @Test
    void getBooks_sortByAuthor_ascending_joinsAllAuthorsInOrder() {
        List<BookEntity> unsorted = List.of(
                book(1L, Instant.parse("2024-01-02T00:00:00Z"), "Jane Zee", "Adam Zoo"),
                book(2L, Instant.parse("2024-01-03T00:00:00Z"), "Adam Alpha"),
                book(3L, Instant.parse("2024-01-01T00:00:00Z"), "Jane Zee")
        );
        when(bookRepository.findAll(any(Specification.class))).thenReturn(unsorted);

        AppPageResponse<AppBookSummary> response = service.getBooks(baseRequest("author", "asc"));

        assertEquals(List.of(2L, 3L, 1L), response.getContent().stream().map(AppBookSummary::getId).toList());
    }

    @Test
    void getBooks_sortByAuthorSurnameVorname_descending_appliesSurnameParsing() {
        List<BookEntity> unsorted = List.of(
                book(1L, Instant.parse("2024-01-02T00:00:00Z"), "Jane Zee"),
                book(2L, Instant.parse("2024-01-03T00:00:00Z"), "Bob Alpha"),
                book(3L, Instant.parse("2024-01-01T00:00:00Z"), "Mary Anne Smith")
        );
        when(bookRepository.findAll(any(Specification.class))).thenReturn(unsorted);

        AppPageResponse<AppBookSummary> response = service.getBooks(baseRequest("authorSurnameVorname", "desc"));

        assertEquals(List.of(1L, 3L, 2L), response.getContent().stream().map(AppBookSummary::getId).toList());
    }

    @Test
    void getBooks_sortByAuthor_usesStableTiebreakersForEqualAuthorKeys() {
        List<BookEntity> unsorted = List.of(
                book(2L, Instant.parse("2024-01-03T00:00:00Z"), "Jane Zee"),
                book(1L, Instant.parse("2024-01-01T00:00:00Z"), "Jane Zee")
        );
        when(bookRepository.findAll(any(Specification.class))).thenReturn(unsorted);

        AppPageResponse<AppBookSummary> response = service.getBooks(baseRequest("author", "asc"));

        assertEquals(List.of(1L, 2L), response.getContent().stream().map(AppBookSummary::getId).toList());
    }

    @Test
    void getBooks_unknownSortKey_fallsBackToDefaultPagedQuery() {
        Page<BookEntity> page = new PageImpl<>(
                List.of(book(10L, Instant.parse("2024-01-01T00:00:00Z"), "Author One")),
                PageRequest.of(0, 50),
                1
        );
        when(bookRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);

        AppPageResponse<AppBookSummary> response = service.getBooks(baseRequest("notARealSort", "asc"));

        assertEquals(List.of(10L), response.getContent().stream().map(AppBookSummary::getId).toList());
        verify(bookRepository).findAll(any(Specification.class), any(PageRequest.class));
    }

    private BookListRequest baseRequest(String sort, String dir) {
        return new BookListRequest(
                0, 50, sort, dir,
                null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null
        );
    }

    private BookEntity book(Long id, Instant addedOn, String... authorNames) {
        List<AuthorEntity> authors = List.of(authorNames).stream()
                .map(name -> AuthorEntity.builder().name(name).build())
                .toList();
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .bookId(id)
                .title("Book " + id)
                .authors(authors)
                .build();
        return BookEntity.builder()
                .id(id)
                .addedOn(addedOn)
                .metadata(metadata)
                .build();
    }

    private void mockAdminUser() {
        var permissions = new BookLoreUser.UserPermissions();
        permissions.setAdmin(true);
        BookLoreUser user = BookLoreUser.builder()
                .id(1L)
                .permissions(permissions)
                .build();
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
    }
}
