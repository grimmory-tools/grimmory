package org.booklore.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.Library;
import org.booklore.model.dto.MagicShelf;
import org.booklore.model.dto.Shelf;
import org.booklore.model.dto.response.MenuCountsResponse;
import org.booklore.model.entity.BookEntity;
import org.booklore.repository.BookRepository;
import org.booklore.service.library.LibraryService;
import org.booklore.service.opds.MagicShelfBookService;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MenuCountsService {

    private final BookRepository bookRepository;
    private final AuthenticationService authenticationService;
    private final LibraryService libraryService;
    private final ShelfService shelfService;
    private final MagicShelfService magicShelfService;
    private final MagicShelfBookService magicShelfBookService;
    private final EntityManager entityManager;

    public MenuCountsResponse getMenuCounts() {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        if (user == null || user.getId() == null) {
            return new MenuCountsResponse(Map.of(), Map.of(), Map.of(), 0L, 0L);
        }

        List<Library> libraries = libraryService.getLibraries(user);
        List<Shelf> shelves = shelfService.getShelves(user);
        List<MagicShelf> magicShelves = magicShelfService.getUserShelves();
        boolean isAdmin = user.getPermissions() != null && user.getPermissions().isAdmin();

        Set<Long> visibleLibraryIds = libraries.stream()
                .map(Library::getId)
                .collect(Collectors.toSet());
        Set<Long> visibleShelfIds = shelves.stream()
                .map(Shelf::getId)
                .collect(Collectors.toSet());

        Map<Long, Long> libraryCounts = fetchLibraryCounts(visibleLibraryIds, isAdmin);
        Map<Long, Long> shelfCounts = fetchShelfCounts(visibleLibraryIds, visibleShelfIds, isAdmin);
        Map<Long, Long> magicShelfCounts = fetchMagicShelfCounts(user.getId(), magicShelves);

        Specification<BookEntity> visibleScope = buildVisibleScope(visibleLibraryIds, isAdmin);
        long totalBookCount = bookRepository.count(visibleScope.and(notDeleted()));
        long unshelvedBookCount = bookRepository.count(visibleScope.and(notDeleted()).and(unshelved()));

        return new MenuCountsResponse(
                libraryCounts,
                shelfCounts,
                magicShelfCounts,
                totalBookCount,
                unshelvedBookCount
        );
    }

    private Map<Long, Long> fetchLibraryCounts(Set<Long> visibleLibraryIds, boolean isAdmin) {
        if (!isAdmin && visibleLibraryIds.isEmpty()) {
            return Collections.emptyMap();
        }

        String jpql = "SELECT b.library.id, COUNT(b.id) " +
                "FROM BookEntity b " +
                "WHERE (b.deleted IS NULL OR b.deleted = false) " +
                (isAdmin ? "" : "AND b.library.id IN :libraryIds ") +
                "GROUP BY b.library.id";

        TypedQuery<Tuple> query = entityManager.createQuery(jpql, Tuple.class);
        if (!isAdmin) {
            query.setParameter("libraryIds", visibleLibraryIds);
        }

        Map<Long, Long> counts = new LinkedHashMap<>();
        for (Tuple row : query.getResultList()) {
            counts.put(row.get(0, Long.class), row.get(1, Long.class));
        }
        return counts;
    }

    private Map<Long, Long> fetchShelfCounts(Set<Long> visibleLibraryIds, Set<Long> visibleShelfIds, boolean isAdmin) {
        if ((!isAdmin && visibleLibraryIds.isEmpty()) || (!isAdmin && visibleShelfIds.isEmpty())) {
            return Collections.emptyMap();
        }

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<BookEntity> root = query.from(BookEntity.class);
        Join<BookEntity, ?> shelfJoin = root.join("shelves");
        Path<Long> shelfIdPath = shelfJoin.get("id");
        Expression<Long> countExpr = cb.count(root);

        Predicate visiblePredicate = isAdmin
                ? cb.conjunction()
                : root.get("library").get("id").in(visibleLibraryIds);
        Predicate shelfVisibilityPredicate = isAdmin
                ? cb.conjunction()
                : shelfIdPath.in(visibleShelfIds);
        Predicate notDeletedPredicate = cb.or(
                cb.isNull(root.get("deleted")),
                cb.isFalse(root.get("deleted"))
        );

        query.multiselect(shelfIdPath, countExpr)
                .where(cb.and(visiblePredicate, shelfVisibilityPredicate, notDeletedPredicate))
                .groupBy(shelfIdPath);

        Map<Long, Long> counts = new LinkedHashMap<>();
        for (Tuple row : entityManager.createQuery(query).getResultList()) {
            counts.put(row.get(0, Long.class), row.get(1, Long.class));
        }
        return counts;
    }

    private Map<Long, Long> fetchMagicShelfCounts(Long userId, List<MagicShelf> magicShelves) {
        if (magicShelves == null || magicShelves.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, Long> counts = new LinkedHashMap<>();
        for (MagicShelf shelf : magicShelves) {
            if (shelf == null || shelf.getId() == null) {
                continue;
            }
            long count = 0L;
            try {
                Specification<BookEntity> spec = magicShelfBookService.toSpecification(userId, shelf.getId());
                count = bookRepository.count(spec.and(notDeleted()));
            } catch (Exception ex) {
                log.warn("Failed to evaluate magic shelf {} for menu counts: {}", shelf.getId(), ex.getMessage());
            }
            counts.put(shelf.getId(), count);
        }
        return counts;
    }

    private Specification<BookEntity> buildVisibleScope(Set<Long> visibleLibraryIds, boolean isAdmin) {
        if (isAdmin) {
            return (root, query, cb) -> cb.conjunction();
        }
        if (visibleLibraryIds == null || visibleLibraryIds.isEmpty()) {
            return (root, query, cb) -> cb.disjunction();
        }
        return (root, query, cb) -> root.get("library").get("id").in(visibleLibraryIds);
    }

    private Specification<BookEntity> notDeleted() {
        return (root, query, cb) -> cb.or(
                cb.isNull(root.get("deleted")),
                cb.isFalse(root.get("deleted"))
        );
    }

    private Specification<BookEntity> unshelved() {
        return (root, query, cb) -> cb.isEmpty(root.get("shelves"));
    }
}
