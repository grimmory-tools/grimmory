package org.booklore.app.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.APIException;
import org.booklore.app.dto.AppBookSummary;
import org.booklore.app.dto.AppPageResponse;
import org.booklore.app.dto.AppSeriesSummary;
import org.booklore.app.mapper.AppBookMapper;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.Library;
import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.BookRepository;
import org.booklore.repository.UserBookProgressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppSeriesServiceTest {

    @Mock private EntityManager entityManager;
    @Mock private AuthenticationService authenticationService;
    @Mock private BookRepository bookRepository;
    @Mock private UserBookProgressRepository userBookProgressRepository;
    @Mock private AppBookMapper mobileBookMapper;

    private AppSeriesService service;

    private final Long userId = 1L;

    @BeforeEach
    void setUp() {
        service = new AppSeriesService(
                entityManager, authenticationService, bookRepository,
                userBookProgressRepository, mobileBookMapper
        );
    }

    // ---- getSeries tests ----

    @Nested
    class GetSeriesTests {

        @Test
        void getSeries_admin_noParams_returnsPage() {
            mockAdminUser();
            mockAggregateQuery(List.of(
                    mockSeriesTuple("The Expanse", 9L, 9, Instant.now(), 3L)
            ));
            mockCountQuery(1L);
            mockBooksQuery(List.of(buildBook(1L, "The Expanse", 1.0f, "Author A")));

            AppPageResponse<AppSeriesSummary> result = service.getSeries(0, 20, null, null, null, null, false);

            assertNotNull(result);
            assertEquals(1, result.getContent().size());
            assertEquals("The Expanse", result.getContent().getFirst().getSeriesName());
            assertEquals(9, result.getContent().getFirst().getBookCount());
            assertEquals(3, result.getContent().getFirst().getBooksRead());
        }

        @Test
        void getSeries_admin_emptyResult_returnsEmptyPage() {
            mockAdminUser();
            mockAggregateQuery(Collections.emptyList());
            mockCountQuery(0L);

            AppPageResponse<AppSeriesSummary> result = service.getSeries(0, 20, null, null, null, null, false);

            assertNotNull(result);
            assertTrue(result.getContent().isEmpty());
            assertEquals(0, result.getTotalElements());
        }

        @Test
        void getSeries_nonAdmin_withAccessibleLibrary_succeeds() {
            mockNonAdminUser(Set.of(5L, 10L));
            mockAggregateQuery(List.of(
                    mockSeriesTuple("Dune", 6L, 6, Instant.now(), 2L)
            ));
            mockCountQuery(1L);
            mockBooksQuery(List.of(buildBook(2L, "Dune", 1.0f, "Frank Herbert")));

            AppPageResponse<AppSeriesSummary> result = service.getSeries(0, 20, null, null, 5L, null, false);

            assertNotNull(result);
            assertEquals(1, result.getContent().size());
        }

        @Test
        void getSeries_nonAdmin_noAccessToLibrary_throwsForbidden() {
            mockNonAdminUser(Set.of(10L));

            assertThrows(APIException.class, () ->
                    service.getSeries(0, 20, null, null, 5L, null, false));
        }

        @Test
        void getSeries_withSearch_passesSearchPattern() {
            mockAdminUser();
            mockAggregateQuery(List.of(
                    mockSeriesTuple("Harry Potter", 7L, 7, Instant.now(), 7L)
            ));
            mockCountQuery(1L);
            mockBooksQuery(List.of(buildBook(3L, "Harry Potter", 1.0f, "J.K. Rowling")));

            AppPageResponse<AppSeriesSummary> result = service.getSeries(0, 20, null, null, null, "harry", false);

            assertNotNull(result);
            assertEquals(1, result.getContent().size());
        }

        @Test
        void getSeries_inProgressOnly_returnsFilteredResults() {
            mockAdminUser();
            mockAggregateQueryInProgress(List.of(
                    mockSeriesTupleInProgress("The Expanse", 9L, 9, Instant.now(), 3L, Instant.now())
            ));
            mockCountQueryInProgress(1L);
            mockBooksQuery(List.of(buildBook(1L, "The Expanse", 1.0f, "Author A")));

            AppPageResponse<AppSeriesSummary> result = service.getSeries(0, 20, null, null, null, null, true);

            assertNotNull(result);
            assertEquals(1, result.getContent().size());
        }

        @Test
        void getSeries_paginationDefaults_appliedCorrectly() {
            mockAdminUser();
            mockAggregateQuery(Collections.emptyList());
            mockCountQuery(0L);

            AppPageResponse<AppSeriesSummary> result = service.getSeries(null, null, null, null, null, null, false);

            assertEquals(0, result.getPage());
            assertEquals(20, result.getSize());
        }

        @Test
        void getSeries_pageSizeCapped_atMax() {
            mockAdminUser();
            mockAggregateQuery(Collections.emptyList());
            mockCountQuery(0L);

            AppPageResponse<AppSeriesSummary> result = service.getSeries(0, 100, null, null, null, null, false);

            assertEquals(50, result.getSize());
        }

        @Test
        void getSeries_enrichesCoverBooksAndAuthors() {
            mockAdminUser();
            BookEntity book1 = buildBook(10L, "Series X", 1.0f, "Author A");
            BookEntity book2 = buildBook(11L, "Series X", 2.0f, "Author B");

            mockAggregateQuery(List.of(
                    mockSeriesTuple("Series X", 2L, 3, Instant.now(), 1L)
            ));
            mockCountQuery(1L);
            mockBooksQuery(List.of(book1, book2));

            AppPageResponse<AppSeriesSummary> result = service.getSeries(0, 20, null, null, null, null, false);

            AppSeriesSummary series = result.getContent().getFirst();
            assertEquals(2, series.getCoverBooks().size());
            assertEquals(2, series.getAuthors().size());
            // Cover books should be ordered by seriesNumber ASC
            assertEquals(1.0f, series.getCoverBooks().get(0).getSeriesNumber());
            assertEquals(2.0f, series.getCoverBooks().get(1).getSeriesNumber());
        }

        @Test
        void getSeries_sortByName_asc() {
            mockAdminUser();
            mockAggregateQuery(Collections.emptyList());
            mockCountQuery(0L);

            AppPageResponse<AppSeriesSummary> result = service.getSeries(0, 20, "name", "asc", null, null, false);

            assertNotNull(result);
        }

        @Test
        void getSeries_sortByBookCount_desc() {
            mockAdminUser();
            mockAggregateQuery(Collections.emptyList());
            mockCountQuery(0L);

            AppPageResponse<AppSeriesSummary> result = service.getSeries(0, 20, "bookCount", "desc", null, null, false);

            assertNotNull(result);
        }
    }

    // ---- getSeriesBooks tests ----

    @Nested
    class GetSeriesBooksTests {

        @Test
        void getSeriesBooks_admin_returnsBooks() {
            mockAdminUser();
            BookEntity book = buildBook(1L, "Dune", 1.0f, "Frank Herbert");
            mockBookPage(List.of(book), 1L);
            mockProgress(Collections.emptyList());
            mockMapperSummary();

            AppPageResponse<AppBookSummary> result = service.getSeriesBooks("Dune", 0, 20, null, null, null);

            assertNotNull(result);
            assertEquals(1, result.getContent().size());
            assertEquals(1, result.getTotalElements());
        }

        @Test
        void getSeriesBooks_nonAdmin_withAccess_succeeds() {
            mockNonAdminUser(Set.of(5L));
            BookEntity book = buildBook(2L, "Dune", 2.0f, "Frank Herbert");
            mockBookPage(List.of(book), 1L);
            mockProgress(Collections.emptyList());
            mockMapperSummary();

            AppPageResponse<AppBookSummary> result = service.getSeriesBooks("Dune", 0, 20, null, null, 5L);

            assertNotNull(result);
            assertEquals(1, result.getContent().size());
        }

        @Test
        void getSeriesBooks_nonAdmin_noAccess_throwsForbidden() {
            mockNonAdminUser(Set.of(10L));

            assertThrows(APIException.class, () ->
                    service.getSeriesBooks("Dune", 0, 20, null, null, 5L));
        }

        @Test
        void getSeriesBooks_emptyResult_returnsEmptyPage() {
            mockAdminUser();
            mockBookPage(Collections.emptyList(), 0L);

            AppPageResponse<AppBookSummary> result = service.getSeriesBooks("Nonexistent", 0, 20, null, null, null);

            assertNotNull(result);
            assertTrue(result.getContent().isEmpty());
            assertEquals(0, result.getTotalElements());
        }

        @Test
        void getSeriesBooks_sortByTitle_desc() {
            mockAdminUser();
            mockBookPage(Collections.emptyList(), 0L);

            AppPageResponse<AppBookSummary> result = service.getSeriesBooks("Dune", 0, 20, "title", "desc", null);

            assertNotNull(result);
        }

        @Test
        void getSeriesBooks_sortByRecentlyAdded_asc() {
            mockAdminUser();
            mockBookPage(Collections.emptyList(), 0L);

            AppPageResponse<AppBookSummary> result = service.getSeriesBooks("Dune", 0, 20, "recentlyAdded", "asc", null);

            assertNotNull(result);
        }

        @Test
        void getSeriesBooks_defaultSort_isSeriesNumber() {
            mockAdminUser();
            mockBookPage(Collections.emptyList(), 0L);

            AppPageResponse<AppBookSummary> result = service.getSeriesBooks("Dune", 0, 20, null, null, null);

            assertNotNull(result);
        }

        @Test
        void getSeriesBooks_paginationDefaults() {
            mockAdminUser();
            mockBookPage(Collections.emptyList(), 0L);

            AppPageResponse<AppBookSummary> result = service.getSeriesBooks("Dune", null, null, null, null, null);

            assertEquals(0, result.getPage());
            assertEquals(20, result.getSize());
        }

        @Test
        void getSeriesBooks_pageSizeCapped() {
            mockAdminUser();
            mockBookPage(Collections.emptyList(), 0L);

            AppPageResponse<AppBookSummary> result = service.getSeriesBooks("Dune", 0, 200, null, null, null);

            assertEquals(50, result.getSize());
        }
    }

    // ---- Helpers ----

    private void mockAdminUser() {
        var permissions = new BookLoreUser.UserPermissions();
        permissions.setAdmin(true);
        BookLoreUser user = BookLoreUser.builder()
                .id(userId)
                .permissions(permissions)
                .build();
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
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

    private Tuple mockSeriesTuple(String name, Long count, Integer total, Instant addedOn, Long booksRead) {
        Tuple tuple = mock(Tuple.class);
        when(tuple.get(0, String.class)).thenReturn(name);
        when(tuple.get(1, Long.class)).thenReturn(count);
        when(tuple.get(2, Integer.class)).thenReturn(total);
        when(tuple.get(3, Instant.class)).thenReturn(addedOn);
        when(tuple.get(4, Long.class)).thenReturn(booksRead);
        return tuple;
    }

    private Tuple mockSeriesTupleInProgress(String name, Long count, Integer total, Instant addedOn, Long booksRead, Instant lastReadTime) {
        Tuple tuple = mockSeriesTuple(name, count, total, addedOn, booksRead);
        when(tuple.get(5, Instant.class)).thenReturn(lastReadTime);
        return tuple;
    }

    @SuppressWarnings("unchecked")
    private void mockAggregateQuery(List<Tuple> results) {
        TypedQuery<Tuple> aggregateQ = mock(TypedQuery.class);
        when(aggregateQ.setParameter(anyString(), any())).thenReturn(aggregateQ);
        when(aggregateQ.setFirstResult(anyInt())).thenReturn(aggregateQ);
        when(aggregateQ.setMaxResults(anyInt())).thenReturn(aggregateQ);
        when(aggregateQ.getResultList()).thenReturn(results);

        TypedQuery<BookEntity> booksQ = mock(TypedQuery.class);
        when(booksQ.setParameter(anyString(), any())).thenReturn(booksQ);
        when(booksQ.getResultList()).thenReturn(Collections.emptyList());

        when(entityManager.createQuery(anyString(), eq(Tuple.class))).thenReturn(aggregateQ);
        when(entityManager.createQuery(anyString(), eq(BookEntity.class))).thenReturn(booksQ);
    }

    @SuppressWarnings("unchecked")
    private void mockAggregateQueryInProgress(List<Tuple> results) {
        // Pre-compute series names before setting up mocks to avoid calling .get() on mock Tuples during stubbing
        List<String> seriesNames = new ArrayList<>();
        for (Tuple t : results) {
            seriesNames.add(t.get(0, String.class));
        }

        TypedQuery<Tuple> aggregateQ = mock(TypedQuery.class);
        when(aggregateQ.setParameter(anyString(), any())).thenReturn(aggregateQ);
        when(aggregateQ.setFirstResult(anyInt())).thenReturn(aggregateQ);
        when(aggregateQ.setMaxResults(anyInt())).thenReturn(aggregateQ);
        when(aggregateQ.getResultList()).thenReturn(results);

        TypedQuery<BookEntity> booksQ = mock(TypedQuery.class);
        when(booksQ.setParameter(anyString(), any())).thenReturn(booksQ);
        when(booksQ.getResultList()).thenReturn(Collections.emptyList());

        // In-progress count uses String.class query
        TypedQuery<String> countQ = mock(TypedQuery.class);
        when(countQ.setParameter(anyString(), any())).thenReturn(countQ);
        when(countQ.getResultList()).thenReturn(seriesNames);

        when(entityManager.createQuery(anyString(), eq(Tuple.class))).thenReturn(aggregateQ);
        when(entityManager.createQuery(anyString(), eq(BookEntity.class))).thenReturn(booksQ);
        when(entityManager.createQuery(anyString(), eq(String.class))).thenReturn(countQ);
    }

    @SuppressWarnings("unchecked")
    private void mockCountQuery(long count) {
        TypedQuery<Long> countQ = mock(TypedQuery.class);
        when(countQ.setParameter(anyString(), any())).thenReturn(countQ);
        when(countQ.getSingleResult()).thenReturn(count);
        when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(countQ);
    }

    @SuppressWarnings("unchecked")
    private void mockCountQueryInProgress(long count) {
        TypedQuery<String> countQ = mock(TypedQuery.class);
        when(countQ.setParameter(anyString(), any())).thenReturn(countQ);
        List<String> names = new ArrayList<>();
        for (int i = 0; i < count; i++) names.add("series" + i);
        when(countQ.getResultList()).thenReturn(names);
        when(entityManager.createQuery(anyString(), eq(String.class))).thenReturn(countQ);
    }

    @SuppressWarnings("unchecked")
    private void mockBooksQuery(List<BookEntity> books) {
        TypedQuery<BookEntity> booksQ = mock(TypedQuery.class);
        when(booksQ.setParameter(anyString(), any())).thenReturn(booksQ);
        when(booksQ.getResultList()).thenReturn(books);
        when(entityManager.createQuery(anyString(), eq(BookEntity.class))).thenReturn(booksQ);
    }

    @SuppressWarnings("unchecked")
    private void mockBookPage(List<BookEntity> books, long total) {
        var page = new PageImpl<>(books, Pageable.ofSize(20), total);
        when(bookRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
    }

    private void mockProgress(List<UserBookProgressEntity> progressList) {
        when(userBookProgressRepository.findByUserIdAndBookIdIn(eq(userId), anySet()))
                .thenReturn(progressList);
    }

    private void mockMapperSummary() {
        when(mobileBookMapper.toSummary(any(BookEntity.class), any()))
                .thenAnswer(inv -> {
                    BookEntity b = inv.getArgument(0);
                    return AppBookSummary.builder()
                            .id(b.getId())
                            .title(b.getMetadata() != null ? b.getMetadata().getTitle() : null)
                            .build();
                });
    }

    private BookEntity buildBook(Long id, String seriesName, Float seriesNumber, String authorName) {
        AuthorEntity author = new AuthorEntity();
        author.setName(authorName);

        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .bookId(id)
                .title(seriesName + " #" + seriesNumber.intValue())
                .seriesName(seriesName)
                .seriesNumber(seriesNumber)
                .coverUpdatedOn(Instant.now())
                .authors(List.of(author))
                .build();

        BookFileEntity bookFile = BookFileEntity.builder()
                .id(id)
                .bookType(BookFileType.EPUB)
                .build();

        List<BookFileEntity> bookFiles = new ArrayList<>(List.of(bookFile));

        BookEntity book = BookEntity.builder()
                .id(id)
                .metadata(metadata)
                .addedOn(Instant.now())
                .bookFiles(bookFiles)
                .build();

        metadata.setBook(book);
        bookFile.setBook(book);

        return book;
    }
}
