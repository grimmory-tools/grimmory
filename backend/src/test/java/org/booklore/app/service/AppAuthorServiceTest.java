package org.booklore.app.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.APIException;
import org.booklore.app.dto.AppAuthorDetail;
import org.booklore.app.dto.AppAuthorSummary;
import org.booklore.app.dto.AppPageResponse;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.Library;
import org.booklore.model.entity.AuthorEntity;
import org.booklore.repository.AuthorRepository;
import org.booklore.util.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppAuthorServiceTest {

    @Mock private EntityManager entityManager;
    @Mock private AuthenticationService authenticationService;
    @Mock private AuthorRepository authorRepository;
    @Mock private FileService fileService;

    private AppAuthorService service;

    private final Long userId = 1L;

    @BeforeEach
    void setUp() {
        service = new AppAuthorService(authorRepository, authenticationService, fileService, entityManager);
    }

    // ---- getAuthors tests ----

    @Nested
    class GetAuthorsTests {

        @Test
        void getAuthors_admin_noFilters_returnsPage() {
            mockAdminUser();
            mockCountQuery(2L);
            mockDataQuery(List.<Object[]>of(
                    new Object[]{buildAuthor(1L, "Author A"), 5L},
                    new Object[]{buildAuthor(2L, "Author B"), 3L}
            ));
            mockAuthorThumbnailExists(1L, true);
            mockAuthorThumbnailExists(2L, false);

            AppPageResponse<AppAuthorSummary> result = service.getAuthors(0, 30, "name", "asc", null, null, null);

            assertNotNull(result);
            assertEquals(2, result.getContent().size());
            assertEquals("Author A", result.getContent().get(0).getName());
            assertEquals(5, result.getContent().get(0).getBookCount());
            assertTrue(result.getContent().get(0).isHasPhoto());
            assertEquals("Author B", result.getContent().get(1).getName());
            assertEquals(3, result.getContent().get(1).getBookCount());
            assertFalse(result.getContent().get(1).isHasPhoto());
            assertEquals(2, result.getTotalElements());
        }

        @Test
        void getAuthors_admin_emptyResult_returnsEmptyPage() {
            mockAdminUser();
            mockCountQuery(0L);

            AppPageResponse<AppAuthorSummary> result = service.getAuthors(0, 30, null, null, null, null, null);

            assertNotNull(result);
            assertTrue(result.getContent().isEmpty());
            assertEquals(0, result.getTotalElements());
        }

        @Test
        void getAuthors_nonAdmin_withAccessibleLibrary_succeeds() {
            mockNonAdminUser(Set.of(5L, 10L));
            mockCountQuery(1L);
            mockDataQuery(List.<Object[]>of(
                    new Object[]{buildAuthor(3L, "Author C"), 2L}
            ));
            mockAuthorThumbnailExists(3L, false);

            AppPageResponse<AppAuthorSummary> result = service.getAuthors(0, 30, null, null, 5L, null, null);

            assertNotNull(result);
            assertEquals(1, result.getContent().size());
            assertEquals("Author C", result.getContent().get(0).getName());
        }

        @Test
        void getAuthors_nonAdmin_noLibraryFilter_usesAccessibleLibraries() {
            mockNonAdminUser(Set.of(5L));
            mockCountQuery(1L);
            mockDataQuery(List.<Object[]>of(
                    new Object[]{buildAuthor(4L, "Author D"), 1L}
            ));
            mockAuthorThumbnailExists(4L, true);

            AppPageResponse<AppAuthorSummary> result = service.getAuthors(0, 30, null, null, null, null, null);

            assertNotNull(result);
            assertEquals(1, result.getContent().size());
        }

        @Test
        void getAuthors_withSearch_filtersResultsByName() {
            mockAdminUser();
            mockCountQuery(1L);
            mockDataQuery(List.<Object[]>of(
                    new Object[]{buildAuthor(5L, "Brandon Sanderson"), 12L}
            ));
            mockAuthorThumbnailExists(5L, true);

            AppPageResponse<AppAuthorSummary> result = service.getAuthors(0, 30, null, null, null, "brandon", null);

            assertNotNull(result);
            assertEquals(1, result.getContent().size());
            assertEquals("Brandon Sanderson", result.getContent().get(0).getName());
        }

        @Test
        void getAuthors_withHasPhotoTrue_filtersToAuthorsWithPhotos() {
            mockAdminUser();
            mockCountQuery(2L);
            mockDataQuery(List.<Object[]>of(
                    new Object[]{buildAuthor(6L, "Author E"), 4L},
                    new Object[]{buildAuthor(7L, "Author F"), 2L}
            ));
            mockAuthorThumbnailExists(6L, true);
            mockAuthorThumbnailExists(7L, false);
            // Mock the hasPhoto count query
            mockAuthorEntityQuery(List.of(buildAuthor(6L, "Author E"), buildAuthor(7L, "Author F")));

            AppPageResponse<AppAuthorSummary> result = service.getAuthors(0, 30, null, null, null, null, true);

            assertNotNull(result);
            assertEquals(1, result.getContent().size());
            assertEquals("Author E", result.getContent().get(0).getName());
            assertTrue(result.getContent().get(0).isHasPhoto());
        }

        @Test
        void getAuthors_withHasPhotoFalse_filtersToAuthorsWithoutPhotos() {
            mockAdminUser();
            mockCountQuery(2L);
            mockDataQuery(List.<Object[]>of(
                    new Object[]{buildAuthor(8L, "Author G"), 3L},
                    new Object[]{buildAuthor(9L, "Author H"), 1L}
            ));
            mockAuthorThumbnailExists(8L, true);
            mockAuthorThumbnailExists(9L, false);
            mockAuthorEntityQuery(List.of(buildAuthor(8L, "Author G"), buildAuthor(9L, "Author H")));

            AppPageResponse<AppAuthorSummary> result = service.getAuthors(0, 30, null, null, null, null, false);

            assertNotNull(result);
            assertEquals(1, result.getContent().size());
            assertEquals("Author H", result.getContent().get(0).getName());
            assertFalse(result.getContent().get(0).isHasPhoto());
        }

        @Test
        void getAuthors_paginationDefaults_appliedCorrectly() {
            mockAdminUser();
            mockCountQuery(0L);

            AppPageResponse<AppAuthorSummary> result = service.getAuthors(null, null, null, null, null, null, null);

            assertEquals(0, result.getPage());
            assertEquals(30, result.getSize());
        }

        @Test
        void getAuthors_pageSizeCapped_atMax() {
            mockAdminUser();
            mockCountQuery(0L);

            AppPageResponse<AppAuthorSummary> result = service.getAuthors(0, 100, null, null, null, null, null);

            assertEquals(50, result.getSize());
        }

        @Test
        void getAuthors_sortByName_asc() {
            mockAdminUser();
            mockCountQuery(0L);

            AppPageResponse<AppAuthorSummary> result = service.getAuthors(0, 30, "name", "asc", null, null, null);

            assertNotNull(result);
        }

        @Test
        void getAuthors_sortByBookCount_desc() {
            mockAdminUser();
            mockCountQuery(0L);

            AppPageResponse<AppAuthorSummary> result = service.getAuthors(0, 30, "bookCount", "desc", null, null, null);

            assertNotNull(result);
        }

        @Test
        void getAuthors_sortByRecent_desc() {
            mockAdminUser();
            mockCountQuery(0L);

            AppPageResponse<AppAuthorSummary> result = service.getAuthors(0, 30, "recent", "desc", null, null, null);

            assertNotNull(result);
        }
    }

    // ---- getAuthorDetail tests ----

    @Nested
    class GetAuthorDetailTests {

        @Test
        void getAuthorDetail_admin_returnsDetail() {
            mockAdminUser();
            AuthorEntity author = buildAuthor(1L, "J.R.R. Tolkien");
            author.setDescription("English writer and philologist.");
            author.setAsin("B000AP9MCS");
            when(authorRepository.findById(1L)).thenReturn(Optional.of(author));
            mockAuthorThumbnailExists(1L, true);
            mockBookCountQuery(3);

            AppAuthorDetail result = service.getAuthorDetail(1L);

            assertNotNull(result);
            assertEquals(1L, result.getId());
            assertEquals("J.R.R. Tolkien", result.getName());
            assertEquals("English writer and philologist.", result.getDescription());
            assertEquals("B000AP9MCS", result.getAsin());
            assertEquals(3, result.getBookCount());
            assertTrue(result.isHasPhoto());
        }

        @Test
        void getAuthorDetail_nonAdmin_withAccess_returnsDetail() {
            mockNonAdminUser(Set.of(5L));
            AuthorEntity author = buildAuthor(2L, "Frank Herbert");
            when(authorRepository.findById(2L)).thenReturn(Optional.of(author));
            when(authorRepository.existsByIdAndLibraryIds(eq(2L), anySet())).thenReturn(true);
            mockAuthorThumbnailExists(2L, false);
            mockBookCountQuery(6);

            AppAuthorDetail result = service.getAuthorDetail(2L);

            assertNotNull(result);
            assertEquals("Frank Herbert", result.getName());
            assertEquals(6, result.getBookCount());
            assertFalse(result.isHasPhoto());
        }

        @Test
        void getAuthorDetail_nonAdmin_noAccess_throwsNotFound() {
            mockNonAdminUser(Set.of(5L));
            AuthorEntity author = buildAuthor(3L, "Secret Author");
            when(authorRepository.findById(3L)).thenReturn(Optional.of(author));
            when(authorRepository.existsByIdAndLibraryIds(eq(3L), anySet())).thenReturn(false);

            assertThrows(APIException.class, () -> service.getAuthorDetail(3L));
        }

        @Test
        void getAuthorDetail_nonExistentAuthor_throwsNotFound() {
            mockAdminUser();
            when(authorRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(APIException.class, () -> service.getAuthorDetail(999L));
        }

        @Test
        void getAuthorDetail_nonAdmin_emptyLibraries_throwsNotFound() {
            mockNonAdminUser(Collections.emptySet());
            AuthorEntity author = buildAuthor(4L, "Author X");
            when(authorRepository.findById(4L)).thenReturn(Optional.of(author));

            assertThrows(APIException.class, () -> service.getAuthorDetail(4L));
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

    @SuppressWarnings("unchecked")
    private void mockCountQuery(long count) {
        TypedQuery<Long> countQ = mock(TypedQuery.class);
        when(countQ.setParameter(anyString(), any())).thenReturn(countQ);
        when(countQ.getSingleResult()).thenReturn(count);
        when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(countQ);
    }

    @SuppressWarnings("unchecked")
    private void mockDataQuery(List<Object[]> results) {
        TypedQuery<Object[]> dataQ = mock(TypedQuery.class);
        when(dataQ.setParameter(anyString(), any())).thenReturn(dataQ);
        when(dataQ.setFirstResult(anyInt())).thenReturn(dataQ);
        when(dataQ.setMaxResults(anyInt())).thenReturn(dataQ);
        when(dataQ.getResultList()).thenReturn(results);
        when(entityManager.createQuery(anyString(), eq(Object[].class))).thenReturn(dataQ);
    }

    @SuppressWarnings("unchecked")
    private void mockAuthorEntityQuery(List<AuthorEntity> authors) {
        TypedQuery<AuthorEntity> authorQ = mock(TypedQuery.class);
        when(authorQ.setParameter(anyString(), any())).thenReturn(authorQ);
        when(authorQ.getResultList()).thenReturn(authors);
        when(entityManager.createQuery(anyString(), eq(AuthorEntity.class))).thenReturn(authorQ);
    }

    @SuppressWarnings("unchecked")
    private void mockBookCountQuery(int count) {
        TypedQuery<Long> countQ = mock(TypedQuery.class);
        when(countQ.setParameter(anyString(), any())).thenReturn(countQ);
        when(countQ.getSingleResult()).thenReturn((long) count);
        when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(countQ);
    }

    private void mockAuthorThumbnailExists(Long authorId, boolean exists) {
        String path = "/mock/authors/" + authorId + "/thumbnail.jpg";
        when(fileService.getAuthorThumbnailFile(authorId)).thenReturn(path);
        // Since Files.exists checks real filesystem, a non-existent mock path returns false.
        // For "exists = true" tests, we need a path that actually exists.
        if (exists) {
            // Use a path we know exists — the temp directory
            when(fileService.getAuthorThumbnailFile(authorId)).thenReturn(System.getProperty("java.io.tmpdir"));
        }
    }

    private AuthorEntity buildAuthor(Long id, String name) {
        return AuthorEntity.builder()
                .id(id)
                .name(name)
                .build();
    }
}
