package org.booklore.app.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.app.dto.AppAuthorDetail;
import org.booklore.app.dto.AppAuthorSummary;
import org.booklore.app.dto.AppPageResponse;
import org.booklore.model.dto.BookLoreUser;
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

    @Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS)
    private CriteriaBuilder cb;

    private AppAuthorService service;

    private final Long userId = 1L;

    @BeforeEach
    void setUp() {
        service = new AppAuthorService(authorRepository, authenticationService, fileService, entityManager);
        when(entityManager.getCriteriaBuilder()).thenReturn(cb);
    }

    // ---- getAuthors tests ----

    @Nested
    class GetAuthorsTests {

        @Test
        @SuppressWarnings("unchecked")
        void getAuthors_admin_noFilters_returnsPage() {
            mockAdminUser();
            
            TypedQuery<Long> countQuery = mock(TypedQuery.class);
            when(entityManager.createQuery(any(CriteriaQuery.class))).thenReturn((TypedQuery) countQuery);
            when(countQuery.getSingleResult()).thenReturn(2L);

            TypedQuery<Object[]> dataQuery = mock(TypedQuery.class);
            // Re-stubbing for the second call
            when(entityManager.createQuery(any(CriteriaQuery.class)))
                .thenReturn((TypedQuery) countQuery)
                .thenReturn((TypedQuery) dataQuery);
                
            when(dataQuery.setFirstResult(anyInt())).thenReturn(dataQuery);
            when(dataQuery.setMaxResults(anyInt())).thenReturn(dataQuery);
            when(dataQuery.getResultList()).thenReturn(List.of(
                    new Object[]{buildAuthor(1L, "Author A", true), 5L},
                    new Object[]{buildAuthor(2L, "Author B", false), 3L}
            ));

            AppPageResponse<AppAuthorSummary> result = service.getAuthors(0, 30, "name", "asc", null, null, null);

            assertNotNull(result);
            assertEquals(2, result.getContent().size());
            assertEquals("Author A", result.getContent().get(0).getName());
            assertTrue(result.getContent().get(0).isHasPhoto());
            assertEquals(2, result.getTotalElements());
        }
    }

    // ---- getAuthorDetail tests ----

    @Nested
    class GetAuthorDetailTests {

        @Test
        @SuppressWarnings("unchecked")
        void getAuthorDetail_admin_returnsDetail() {
            mockAdminUser();
            AuthorEntity author = buildAuthor(1L, "J.R.R. Tolkien", true);
            author.setDescription("English writer and philologist.");
            author.setAsin("B000AP9MCS");
            when(authorRepository.findById(1L)).thenReturn(Optional.of(author));
            when(fileService.getAuthorThumbnailFile(anyLong())).thenReturn("test-path");
            
            TypedQuery<Long> countQuery = mock(TypedQuery.class);
            when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(countQuery);
            when(countQuery.setParameter(anyString(), any())).thenReturn(countQuery);
            when(countQuery.getSingleResult()).thenReturn(3L);

            AppAuthorDetail result = service.getAuthorDetail(1L);

            assertNotNull(result);
            assertEquals(1L, result.getId());
            assertEquals("J.R.R. Tolkien", result.getName());
            assertEquals(3, result.getBookCount());
            assertTrue(result.isHasPhoto());
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

    private AuthorEntity buildAuthor(Long id, String name, boolean hasPhoto) {
        return AuthorEntity.builder()
                .id(id)
                .name(name)
                .hasPhoto(hasPhoto)
                .build();
    }
}
