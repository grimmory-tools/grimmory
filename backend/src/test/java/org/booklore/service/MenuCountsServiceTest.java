package org.booklore.service;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.BookLoreUser.UserPermissions;
import org.booklore.model.dto.Library;
import org.booklore.model.dto.MagicShelf;
import org.booklore.model.dto.Shelf;
import org.booklore.model.dto.response.MenuCountsResponse;
import org.booklore.model.entity.BookEntity;
import org.booklore.repository.BookRepository;
import org.booklore.service.library.LibraryService;
import org.booklore.service.opds.MagicShelfBookService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MenuCountsServiceTest {

    @Mock private BookRepository bookRepository;
    @Mock private AuthenticationService authenticationService;
    @Mock private LibraryService libraryService;
    @Mock private ShelfService shelfService;
    @Mock private MagicShelfService magicShelfService;
    @Mock private MagicShelfBookService magicShelfBookService;
    @Mock private EntityManager entityManager;

    private MenuCountsService service;

    @BeforeEach
    void setUp() {
        service = new MenuCountsService(
                bookRepository,
                authenticationService,
                libraryService,
                shelfService,
                magicShelfService,
                magicShelfBookService,
                entityManager
        );
        
        lenient().when(libraryService.getLibraries(any())).thenReturn(Collections.emptyList());
        lenient().when(shelfService.getShelves(any())).thenReturn(Collections.emptyList());
        lenient().when(magicShelfService.getUserShelves()).thenReturn(Collections.emptyList());
    }

    @Test
    void returnsEmptyMapsWhenNoAuthenticatedUser() {
        when(authenticationService.getAuthenticatedUser()).thenReturn(null);

        MenuCountsResponse response = service.getMenuCounts();

        assertThat(response.libraryCounts()).isEmpty();
        assertThat(response.shelfCounts()).isEmpty();
        assertThat(response.magicShelfCounts()).isEmpty();
    }

    @Test
    void countsAllLibrariesShelvesAndMagicShelvesForAdmin() {
        BookLoreUser admin = buildUser(42L, true, List.of(library(1L), library(2L)));
        when(authenticationService.getAuthenticatedUser()).thenReturn(admin);

        when(libraryService.getLibraries(any())).thenReturn(List.of(library(1L), library(2L)));
        when(shelfService.getShelves(any())).thenReturn(List.of(shelf(10L), shelf(11L)));
        when(magicShelfService.getUserShelves()).thenReturn(List.of(magicShelf(20L), magicShelf(21L)));
        
        // Mock Library Counts JPQL
        TypedQuery<Tuple> libraryQuery = mock(TypedQuery.class);
        when(entityManager.createQuery(anyString(), any(Class.class))).thenReturn(libraryQuery);
        Tuple libT1 = mock(Tuple.class);
        when(libT1.get(0, Long.class)).thenReturn(1L);
        when(libT1.get(1, Long.class)).thenReturn(100L);
        Tuple libT2 = mock(Tuple.class);
        when(libT2.get(0, Long.class)).thenReturn(2L);
        when(libT2.get(1, Long.class)).thenReturn(50L);
        when(libraryQuery.getResultList()).thenReturn(Arrays.asList(libT1, libT2));

        // Mock Shelf Counts Criteria
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        CriteriaQuery<Tuple> cq = mock(CriteriaQuery.class);
        Root<BookEntity> root = mock(Root.class);
        Join shelfJoin = mock(Join.class);
        Path idPath = mock(Path.class);
        
        when(entityManager.getCriteriaBuilder()).thenReturn(cb);
        when(cb.count(any())).thenReturn(mock(Expression.class));
        when(cb.createTupleQuery()).thenReturn(cq);
        when(cb.count(any())).thenReturn(mock(Expression.class));
        when(cq.from(BookEntity.class)).thenReturn(root);
        when(cq.multiselect(any(), any())).thenReturn(cq);
        when(cq.where(any(Predicate.class))).thenReturn(cq);
        when(cq.groupBy(any(Expression.class))).thenReturn(cq);
        when(root.join(eq("shelves"))).thenReturn(shelfJoin);
        when(shelfJoin.get("id")).thenReturn(idPath);
        when(cq.multiselect(any(), any())).thenReturn(cq);
        when(cq.where(any(Predicate.class))).thenReturn(cq);
        when(cq.groupBy(any(Expression.class))).thenReturn(cq);
        
        TypedQuery<Tuple> shelfQuery = mock(TypedQuery.class);
        when(entityManager.createQuery(cq)).thenReturn(shelfQuery);
        Tuple shelfT1 = mock(Tuple.class);
        when(shelfT1.get(0, Long.class)).thenReturn(10L);
        when(shelfT1.get(1, Long.class)).thenReturn(7L);
        Tuple shelfT2 = mock(Tuple.class);
        when(shelfT2.get(0, Long.class)).thenReturn(11L);
        when(shelfT2.get(1, Long.class)).thenReturn(3L);
        when(shelfQuery.getResultList()).thenReturn(List.of(shelfT1, shelfT2));

        when(bookRepository.count(any(Specification.class)))
                .thenReturn(9L, 2L, 150L, 40L); // magic counts (2) + total + unshelved

        @SuppressWarnings("unchecked")
        Specification<BookEntity> magicSpec1 = mock(Specification.class);
        @SuppressWarnings("unchecked")
        Specification<BookEntity> magicSpec2 = mock(Specification.class);
        when(magicShelfBookService.toSpecification(42L, 20L)).thenReturn(magicSpec1);
        when(magicShelfBookService.toSpecification(42L, 21L)).thenReturn(magicSpec2);

        MenuCountsResponse response = service.getMenuCounts();

        assertThat(response.libraryCounts()).containsEntry(1L, 100L).containsEntry(2L, 50L);
        assertThat(response.shelfCounts()).containsEntry(10L, 7L).containsEntry(11L, 3L);
        assertThat(response.magicShelfCounts()).containsEntry(20L, 9L).containsEntry(21L, 2L);
        assertThat(response.totalBookCount()).isEqualTo(150L);
        assertThat(response.unshelvedBookCount()).isEqualTo(40L);
    }

    @Test
    void magicShelfCountFallsBackToZeroOnEvaluatorFailure() {
        BookLoreUser user = buildUser(7L, true, List.of());
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);

        when(libraryService.getLibraries(any())).thenReturn(List.of());
        when(shelfService.getShelves(any())).thenReturn(List.of());
        when(magicShelfService.getUserShelves()).thenReturn(List.of(magicShelf(99L)));
        when(magicShelfBookService.toSpecification(7L, 99L))
                .thenThrow(new RuntimeException("broken rule"));

        // Mock empty results for libraries/shelves
        TypedQuery<Tuple> emptyQuery = mock(TypedQuery.class);
        when(entityManager.createQuery(anyString(), eq(Tuple.class))).thenReturn(emptyQuery);
        when(emptyQuery.getResultList()).thenReturn(Collections.emptyList());
        
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        CriteriaQuery<Tuple> cq = mock(CriteriaQuery.class);
        Root<BookEntity> root = mock(Root.class);
        Join shelfJoin = mock(Join.class);
        Path libraryPath = mock(Path.class);
        Path libraryIdPath = mock(Path.class);
        Predicate libraryPredicate = mock(Predicate.class);
        when(entityManager.getCriteriaBuilder()).thenReturn(cb);
        when(cb.count(any())).thenReturn(mock(Expression.class));
        when(cb.createTupleQuery()).thenReturn(cq);
        when(cq.from(BookEntity.class)).thenReturn(root);
        when(root.get(anyString())).thenReturn(libraryPath);
        when(libraryPath.get(anyString())).thenReturn(libraryIdPath);
        when(libraryIdPath.in(anyCollection())).thenReturn(libraryPredicate);
        when(cq.multiselect(any(), any())).thenReturn(cq);
        when(cq.where(any(Predicate.class))).thenReturn(cq);
        when(cq.groupBy(any(Expression.class))).thenReturn(cq);
        when(root.join(anyString())).thenReturn(shelfJoin);
        when(shelfJoin.get(anyString())).thenReturn(mock(Path.class));
        
        TypedQuery<Tuple> emptyCriteriaQuery = mock(TypedQuery.class);
        when(entityManager.createQuery(cq)).thenReturn(emptyCriteriaQuery);
        when(emptyCriteriaQuery.getResultList()).thenReturn(Collections.emptyList());

        when(bookRepository.count(any(Specification.class))).thenReturn(0L, 0L);

        MenuCountsResponse response = service.getMenuCounts();

        assertThat(response.magicShelfCounts()).containsEntry(99L, 0L);
    }

    @Test
    void nonAdminOnlyCountsAgainstAssignedLibraryScope() {
        BookLoreUser user = buildUser(5L, false, List.of(library(1L)));
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);

        when(libraryService.getLibraries(any())).thenReturn(List.of(library(1L)));
        when(shelfService.getShelves(any())).thenReturn(List.of(shelf(10L)));
        when(magicShelfService.getUserShelves()).thenReturn(List.of());

        // Mock Library Counts (JPQL)
        TypedQuery<Tuple> libraryQuery = mock(TypedQuery.class);
        when(entityManager.createQuery(anyString(), any(Class.class))).thenReturn(libraryQuery);
        Tuple libT1 = mock(Tuple.class);
        when(libT1.get(0, Long.class)).thenReturn(1L);
        when(libT1.get(1, Long.class)).thenReturn(25L);
        when(libraryQuery.getResultList()).thenReturn(Collections.singletonList(libT1));
        when(libraryQuery.setParameter(anyString(), any())).thenReturn(libraryQuery);

        // Mock Shelf Counts
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        CriteriaQuery<Tuple> cq = mock(CriteriaQuery.class);
        Root<BookEntity> root = mock(Root.class);
        Join shelfJoin = mock(Join.class);
        Path libraryPath = mock(Path.class);
        Path libraryIdPath = mock(Path.class);
        when(entityManager.getCriteriaBuilder()).thenReturn(cb);
        when(cb.count(any())).thenReturn(mock(Expression.class));
        when(cb.createTupleQuery()).thenReturn(cq);
        when(cq.from(BookEntity.class)).thenReturn(root);
        when(root.get("library")).thenReturn(libraryPath);
        when(libraryPath.get("id")).thenReturn(libraryIdPath);
        when(libraryIdPath.in(anyCollection())).thenReturn(mock(Predicate.class));
        when(cq.multiselect(any(), any())).thenReturn(cq);
        when(cq.where(any(Predicate.class))).thenReturn(cq);
        when(cq.groupBy(any(Expression.class))).thenReturn(cq);
        when(root.join(anyString())).thenReturn(shelfJoin);
        when(shelfJoin.get(anyString())).thenReturn(mock(Path.class));

        TypedQuery<Tuple> shelfQuery = mock(TypedQuery.class);
        when(entityManager.createQuery(cq)).thenReturn(shelfQuery);
        Tuple shelfT1 = mock(Tuple.class);
        when(shelfT1.get(0, Long.class)).thenReturn(10L);
        when(shelfT1.get(1, Long.class)).thenReturn(4L);
        when(shelfQuery.getResultList()).thenReturn(List.of(shelfT1));

        when(bookRepository.count(any(Specification.class))).thenReturn(25L, 12L);

        MenuCountsResponse response = service.getMenuCounts();

        assertThat(response.libraryCounts()).containsEntry(1L, 25L);
        assertThat(response.shelfCounts()).containsEntry(10L, 4L);
        assertThat(response.totalBookCount()).isEqualTo(25L);
        assertThat(response.unshelvedBookCount()).isEqualTo(12L);
        verify(magicShelfBookService, org.mockito.Mockito.never())
                .toSpecification(eq(5L), any());
    }

    private BookLoreUser buildUser(Long id, boolean isAdmin, List<Library> libraries) {
        UserPermissions permissions = new UserPermissions();
        permissions.setAdmin(isAdmin);
        BookLoreUser user = new BookLoreUser();
        user.setId(id);
        user.setPermissions(permissions);
        user.setAssignedLibraries(libraries);
        return user;
    }

    private Library library(Long id) {
        Library library = new Library();
        library.setId(id);
        library.setName("Library " + id);
        return library;
    }

    private Shelf shelf(Long id) {
        Shelf shelf = new Shelf();
        shelf.setId(id);
        shelf.setName("Shelf " + id);
        return shelf;
    }

    private MagicShelf magicShelf(Long id) {
        MagicShelf shelf = new MagicShelf();
        shelf.setId(id);
        shelf.setName("Magic " + id);
        return shelf;
    }
}
