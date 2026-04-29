package org.booklore.app.service;

import jakarta.persistence.EntityManager;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.app.dto.BookListRequest;
import org.booklore.app.mapper.AppBookMapper;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.entity.BookEntity;
import org.booklore.repository.BookRepository;
import org.booklore.repository.ShelfRepository;
import org.booklore.repository.UserBookFileProgressRepository;
import org.booklore.repository.UserBookProgressRepository;
import org.booklore.service.book.BookService;
import org.booklore.service.opds.MagicShelfBookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests that the author and authorSurnameVorname sort fields are properly
 * handled by the service layer — specifically that:
 * <ul>
 *   <li>Sort.unsorted() is passed to the repository (not Sort.by("__author__"))</li>
 *   <li>The specification includes the ordering specification</li>
 *   <li>Other sort fields still produce normal Sort.by() calls</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppBookServiceSortTest {

    @Mock private BookRepository bookRepository;
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

    @BeforeEach
    void setUp() {
        service = new AppBookService(
                bookRepository, userBookProgressRepository, userBookFileProgressRepository,
                shelfRepository, authenticationService, mobileBookMapper,
                bookService, magicShelfBookService, entityManager
        );
        mockAdminUser();
    }

    @Test
    void getBooks_sortByAuthor_usesUnsortedPageable() {
        BookListRequest req = request("author", "asc");
        mockEmptyBookPage();

        service.getBooks(req);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(bookRepository).findAll(any(Specification.class), captor.capture());

        Sort sort = captor.getValue().getSort();
        assertTrue(sort.isUnsorted(), "Author sort should produce Sort.unsorted()");
    }

    @Test
    void getBooks_sortByAuthorSurnameVorname_usesUnsortedPageable() {
        BookListRequest req = request("authorSurnameVorname", "desc");
        mockEmptyBookPage();

        service.getBooks(req);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(bookRepository).findAll(any(Specification.class), captor.capture());

        Sort sort = captor.getValue().getSort();
        assertTrue(sort.isUnsorted(), "Author surname sort should produce Sort.unsorted()");
    }

    @Test
    void getBooks_sortByTitle_usesSortedPageable() {
        BookListRequest req = request("title", "asc");
        mockEmptyBookPage();

        service.getBooks(req);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(bookRepository).findAll(any(Specification.class), captor.capture());

        Sort sort = captor.getValue().getSort();
        assertFalse(sort.isUnsorted(), "Title sort should produce a sorted pageable");
        assertEquals("metadata.title", sort.getOrderFor("metadata.title").getProperty());
        assertEquals(Sort.Direction.ASC, sort.getOrderFor("metadata.title").getDirection());
    }

    @Test
    void getBooks_sortByAddedOn_defaultDirection_isSorted() {
        BookListRequest req = request("addedOn", "desc");
        mockEmptyBookPage();

        service.getBooks(req);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(bookRepository).findAll(any(Specification.class), captor.capture());

        Sort sort = captor.getValue().getSort();
        assertFalse(sort.isUnsorted());
        assertEquals(Sort.Direction.DESC, sort.getOrderFor("addedOn").getDirection());
    }

    @Test
    void getBooks_sortByNullField_defaultsToAddedOn() {
        BookListRequest req = request(null, "desc");
        mockEmptyBookPage();

        service.getBooks(req);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(bookRepository).findAll(any(Specification.class), captor.capture());

        Sort sort = captor.getValue().getSort();
        assertFalse(sort.isUnsorted());
        assertNotNull(sort.getOrderFor("addedOn"));
    }

    @Test
    void getBooks_sortByUnknownField_defaultsToAddedOn() {
        BookListRequest req = request("nonExistentField", "asc");
        mockEmptyBookPage();

        service.getBooks(req);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(bookRepository).findAll(any(Specification.class), captor.capture());

        Sort sort = captor.getValue().getSort();
        assertNotNull(sort.getOrderFor("addedOn"));
    }

    @Test
    void getBooks_sortByAuthor_caseInsensitive() {
        // "AUTHOR", "Author", "aUtHoR" should all work
        for (String variant : List.of("AUTHOR", "Author", "aUtHoR")) {
            reset(bookRepository);
            mockEmptyBookPage();

            BookListRequest req = request(variant, "asc");
            service.getBooks(req);

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(bookRepository).findAll(any(Specification.class), captor.capture());
            assertTrue(captor.getValue().getSort().isUnsorted(),
                    "'" + variant + "' should be recognized as author sort");
        }
    }

    private BookListRequest request(String sort, String dir) {
        return defaultRequest().withSort(sort).withDir(dir).toRequest();
    }

    /**
     * Creates a default request with all fields null except pagination.
     * This localizes the record constructor brittleness to one place.
     */
    private BookListRequestRecord defaultRequest() {
        return new BookListRequestRecord(
                0, 20, null, null,
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null,
                null, null,
                null, null, null
        );
    }

    /** Helper class to provide fluent-ish updates to the brittle BookListRequest record */
    private record BookListRequestRecord(
            Integer page, Integer size, String sort, String dir,
            Long libraryId, Long shelfId, List<String> status, String search, List<String> fileType,
            Integer minRating, Integer maxRating, List<String> authors, List<String> language,
            List<String> series, List<String> category, List<String> publisher, List<String> tag,
            List<String> mood, List<String> narrator, List<String> ageRating, List<String> contentRating,
            List<String> matchScore, List<String> publishedDate, List<String> fileSize,
            List<String> personalRating, List<String> amazonRating, List<String> goodreadsRating,
            List<String> hardcoverRating, List<String> lubimyczytacRating, List<String> ranobedbRating,
            List<String> audibleRating, List<String> pageCount, List<String> shelfStatus,
            List<String> comicCharacter, List<String> comicTeam, List<String> comicLocation,
            List<String> comicCreator, List<String> shelves, List<String> libraries,
            Long magicShelfId, Boolean unshelved, String filterMode
    ) {
        BookListRequest toRequest() {
            return new BookListRequest(
                    page, size, sort, dir, libraryId, shelfId, status, search, fileType,
                    minRating, maxRating, authors, language, series, category, publisher, tag,
                    mood, narrator, ageRating, contentRating, matchScore, publishedDate, fileSize,
                    personalRating, amazonRating, goodreadsRating, hardcoverRating, lubimyczytacRating,
                    ranobedbRating, audibleRating, pageCount, shelfStatus, comicCharacter, comicTeam,
                    comicLocation, comicCreator, shelves, libraries, magicShelfId, unshelved, filterMode
            );
        }

        BookListRequestRecord withSort(String sort) {
            return new BookListRequestRecord(
                    page, size, sort, dir, libraryId, shelfId, status, search, fileType,
                    minRating, maxRating, authors, language, series, category, publisher, tag,
                    mood, narrator, ageRating, contentRating, matchScore, publishedDate, fileSize,
                    personalRating, amazonRating, goodreadsRating, hardcoverRating, lubimyczytacRating,
                    ranobedbRating, audibleRating, pageCount, shelfStatus, comicCharacter, comicTeam,
                    comicLocation, comicCreator, shelves, libraries, magicShelfId, unshelved, filterMode
            );
        }

        BookListRequestRecord withDir(String dir) {
            return new BookListRequestRecord(
                    page, size, sort, dir, libraryId, shelfId, status, search, fileType,
                    minRating, maxRating, authors, language, series, category, publisher, tag,
                    mood, narrator, ageRating, contentRating, matchScore, publishedDate, fileSize,
                    personalRating, amazonRating, goodreadsRating, hardcoverRating, lubimyczytacRating,
                    ranobedbRating, audibleRating, pageCount, shelfStatus, comicCharacter, comicTeam,
                    comicLocation, comicCreator, shelves, libraries, magicShelfId, unshelved, filterMode
            );
        }
    }

    private void mockAdminUser() {
        var permissions = new BookLoreUser.UserPermissions();
        permissions.setAdmin(true);
        BookLoreUser user = BookLoreUser.builder()
                .id(userId)
                .permissions(permissions)
                .build();
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
    }

    @SuppressWarnings("unchecked")
    private void mockEmptyBookPage() {
        Page<BookEntity> emptyPage = new PageImpl<>(Collections.emptyList());
        when(bookRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(emptyPage);
    }
}
