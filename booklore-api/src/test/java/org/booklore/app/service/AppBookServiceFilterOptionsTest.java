package org.booklore.app.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.APIException;
import org.booklore.app.dto.AppFilterOptions;
import org.booklore.app.mapper.AppBookMapper;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.Library;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.ShelfEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.BookRepository;
import org.booklore.repository.ShelfRepository;
import org.booklore.repository.UserBookFileProgressRepository;
import org.booklore.repository.UserBookProgressRepository;
import org.booklore.service.opds.MagicShelfBookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppBookServiceFilterOptionsTest {

    @Mock private BookRepository bookRepository;
    @Mock private UserBookProgressRepository userBookProgressRepository;
    @Mock private UserBookFileProgressRepository userBookFileProgressRepository;
    @Mock private ShelfRepository shelfRepository;
    @Mock private AuthenticationService authenticationService;
    @Mock private AppBookMapper mobileBookMapper;
    @Mock private MagicShelfBookService magicShelfBookService;
    @Mock private EntityManager entityManager;

    private AppBookService service;

    private final Long userId = 1L;

    @BeforeEach
    void setUp() {
        service = new AppBookService(
                bookRepository, userBookProgressRepository, userBookFileProgressRepository,
                shelfRepository, authenticationService, mobileBookMapper,
                magicShelfBookService, entityManager
        );
    }

    // -------------------------------------------------------------------------
    // Global (no scoping params)
    // -------------------------------------------------------------------------

    @Test
    void getFilterOptions_noParams_returnsGlobalOptions() {
        mockAdminUser();
        mockJpqlQueries();

        AppFilterOptions result = service.getFilterOptions(null, null, null);

        assertNotNull(result);
        assertNotNull(result.authors());
        assertNotNull(result.languages());
        assertNotNull(result.fileTypes());
        assertFalse(result.readStatuses().isEmpty());
    }

    // -------------------------------------------------------------------------
    // Library scoping
    // -------------------------------------------------------------------------

    @Test
    void getFilterOptions_withLibraryId_admin_succeeds() {
        mockAdminUser();
        mockJpqlQueries();

        AppFilterOptions result = service.getFilterOptions(5L, null, null);

        assertNotNull(result);
        verify(entityManager, times(9)).createQuery(anyString(), any(Class.class));
    }

    @Test
    void getFilterOptions_withLibraryId_nonAdminWithAccess_succeeds() {
        mockNonAdminUser(Set.of(5L, 10L));
        mockJpqlQueries();

        AppFilterOptions result = service.getFilterOptions(5L, null, null);

        assertNotNull(result);
    }

    @Test
    void getFilterOptions_withLibraryId_nonAdminNoAccess_throwsForbidden() {
        mockNonAdminUser(Set.of(10L));

        assertThrows(APIException.class, () -> service.getFilterOptions(5L, null, null));
    }

    // -------------------------------------------------------------------------
    // Shelf scoping
    // -------------------------------------------------------------------------

    @Test
    void getFilterOptions_withShelfId_publicShelf_succeeds() {
        mockAdminUser();
        ShelfEntity shelf = ShelfEntity.builder().id(10L).isPublic(true)
                .user(BookLoreUserEntity.builder().id(99L).build()).build();
        when(shelfRepository.findById(10L)).thenReturn(Optional.of(shelf));
        mockJpqlQueries();

        AppFilterOptions result = service.getFilterOptions(null, 10L, null);

        assertNotNull(result);
    }

    @Test
    void getFilterOptions_withShelfId_ownPrivateShelf_succeeds() {
        mockAdminUser();
        ShelfEntity shelf = ShelfEntity.builder().id(10L).isPublic(false)
                .user(BookLoreUserEntity.builder().id(userId).build()).build();
        when(shelfRepository.findById(10L)).thenReturn(Optional.of(shelf));
        mockJpqlQueries();

        AppFilterOptions result = service.getFilterOptions(null, 10L, null);

        assertNotNull(result);
    }

    @Test
    void getFilterOptions_withShelfId_otherPrivateShelf_throwsForbidden() {
        mockAdminUser();
        ShelfEntity shelf = ShelfEntity.builder().id(10L).isPublic(false)
                .user(BookLoreUserEntity.builder().id(99L).build()).build();
        when(shelfRepository.findById(10L)).thenReturn(Optional.of(shelf));

        assertThrows(APIException.class, () -> service.getFilterOptions(null, 10L, null));
    }

    @Test
    void getFilterOptions_withShelfId_notFound_throwsException() {
        mockAdminUser();
        when(shelfRepository.findById(10L)).thenReturn(Optional.empty());

        assertThrows(APIException.class, () -> service.getFilterOptions(null, 10L, null));
    }

    // -------------------------------------------------------------------------
    // Magic shelf scoping
    // -------------------------------------------------------------------------

    @Test
    void getFilterOptions_withMagicShelfId_emptyResult_returnsEmptyOptions() {
        mockAdminUser();
        mockMagicShelfSpec(7L, Collections.emptyList());

        AppFilterOptions result = service.getFilterOptions(null, null, 7L);

        assertNotNull(result);
        assertTrue(result.authors().isEmpty());
        assertTrue(result.languages().isEmpty());
        assertTrue(result.fileTypes().isEmpty());
        assertFalse(result.readStatuses().isEmpty());
    }

    @Test
    void getFilterOptions_withMagicShelfId_withBooks_returnsFilteredOptions() {
        mockAdminUser();
        mockMagicShelfSpec(7L, List.of(100L, 200L));
        mockJpqlQueries();

        AppFilterOptions result = service.getFilterOptions(null, null, 7L);

        assertNotNull(result);
        verify(magicShelfBookService).toSpecification(eq(userId), eq(7L));
    }

    @Test
    void getFilterOptions_withMagicShelfId_serviceThrows_propagatesException() {
        mockAdminUser();
        when(magicShelfBookService.toSpecification(eq(userId), eq(7L)))
                .thenThrow(new RuntimeException("Magic shelf not found"));

        assertThrows(RuntimeException.class, () -> service.getFilterOptions(null, null, 7L));
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
    private void mockMagicShelfSpec(Long magicShelfId, List<Long> bookIds) {
        Specification<BookEntity> mockSpec = mock(Specification.class);
        when(magicShelfBookService.toSpecification(eq(userId), eq(magicShelfId)))
                .thenReturn(mockSpec);

        jakarta.persistence.criteria.Predicate mockPredicate = mock(jakarta.persistence.criteria.Predicate.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        CriteriaQuery<Long> cq = mock(CriteriaQuery.class);
        Root<BookEntity> root = mock(Root.class);
        Path<Long> idPath = mock(Path.class);
        jakarta.persistence.TypedQuery<Long> typedQuery = mock(jakarta.persistence.TypedQuery.class);

        when(entityManager.getCriteriaBuilder()).thenReturn(cb);
        when(cb.createQuery(Long.class)).thenReturn(cq);
        when(cq.from(BookEntity.class)).thenReturn(root);
        when(root.get("id")).thenReturn((Path) idPath);
        when(cq.select(idPath)).thenReturn(cq);
        when(mockSpec.toPredicate(root, cq, cb)).thenReturn(mockPredicate);
        when(cq.where(mockPredicate)).thenReturn(cq);
        when(entityManager.createQuery(cq)).thenReturn(typedQuery);
        when(typedQuery.setMaxResults(anyInt())).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(bookIds);
    }

    @SuppressWarnings("unchecked")
    private void mockJpqlQueries() {
        TypedQuery<Tuple> tupleQuery = mock(TypedQuery.class);
        when(tupleQuery.setParameter(anyString(), any())).thenReturn(tupleQuery);
        when(tupleQuery.setMaxResults(anyInt())).thenReturn(tupleQuery);
        when(tupleQuery.getResultList()).thenReturn(Collections.emptyList());

        TypedQuery<BookFileType> ftQuery = mock(TypedQuery.class);
        when(ftQuery.setParameter(anyString(), any())).thenReturn(ftQuery);
        when(ftQuery.getResultList()).thenReturn(Collections.emptyList());

        when(entityManager.createQuery(anyString(), eq(Tuple.class)))
                .thenReturn(tupleQuery);
        when(entityManager.createQuery(anyString(), eq(BookFileType.class)))
                .thenReturn(ftQuery);
    }
}
