package org.booklore.app.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.repository.AuthorRepository;
import org.booklore.util.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppAuthorServiceTest {

    @Mock private EntityManager entityManager;
    @Mock private AuthenticationService authenticationService;
    @Mock private AuthorRepository authorRepository;
    @Mock private FileService fileService;

    private AppAuthorService service;

    @BeforeEach
    void setUp() {
        service = new AppAuthorService(authorRepository, authenticationService, fileService, entityManager);
    }

    @Nested
    class GetAuthorsTests {

        @Test
        @SuppressWarnings("unchecked")
        void getAuthors_capsPageSize() {
            mockAdminUser();
            
            // Minimal Criteria API mocking to reach the early return on totalElements = 0
            CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);
            CriteriaQuery<Long> countCq = mock(CriteriaQuery.class, RETURNS_DEEP_STUBS);
            TypedQuery<Long> countQuery = mock(TypedQuery.class);
            
            when(entityManager.getCriteriaBuilder()).thenReturn(cb);
            when(cb.createQuery(Long.class)).thenReturn(countCq);
            when(entityManager.createQuery(countCq)).thenReturn(countQuery);
            when(countQuery.getSingleResult()).thenReturn(0L);

            var response = service.getAuthors(0, 100, "name", "asc", null, null, null);

            assertEquals(50, response.getSize()); // MAX_PAGE_SIZE
            assertTrue(response.getContent().isEmpty());
        }

        @Test
        void getAuthors_defaultsPagination() {
            mockAdminUser();
            
            CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);
            CriteriaQuery<Long> countCq = mock(CriteriaQuery.class, RETURNS_DEEP_STUBS);
            TypedQuery<Long> countQuery = mock(TypedQuery.class);
            
            when(entityManager.getCriteriaBuilder()).thenReturn(cb);
            when(cb.createQuery(Long.class)).thenReturn(countCq);
            when(entityManager.createQuery(countCq)).thenReturn(countQuery);
            when(countQuery.getSingleResult()).thenReturn(0L);

            var response = service.getAuthors(null, null, null, null, null, null, null);

            assertEquals(0, response.getPage());
            assertEquals(30, response.getSize()); // DEFAULT_PAGE_SIZE
        }
    }

    @Nested
    class AccessControlTests {
        @Test
        void getAuthors_nonAdmin_returnsEmptyOnZeroCount() {
            BookLoreUser user = BookLoreUser.builder()
                    .id(1L)
                    .permissions(new BookLoreUser.UserPermissions()) // non-admin
                    .assignedLibraries(List.of())
                    .build();
            when(authenticationService.getAuthenticatedUser()).thenReturn(user);

            CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);
            CriteriaQuery<Long> countCq = mock(CriteriaQuery.class, RETURNS_DEEP_STUBS);
            TypedQuery<Long> countQuery = mock(TypedQuery.class);
            
            when(entityManager.getCriteriaBuilder()).thenReturn(cb);
            when(cb.createQuery(Long.class)).thenReturn(countCq);
            when(entityManager.createQuery(countCq)).thenReturn(countQuery);
            when(countQuery.getSingleResult()).thenReturn(0L);

            var response = service.getAuthors(0, 10, "name", "asc", null, null, null);
            
            assertNotNull(response);
            verify(authenticationService).getAuthenticatedUser();
        }
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
