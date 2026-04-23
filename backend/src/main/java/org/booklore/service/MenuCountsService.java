package org.booklore.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.app.specification.AppBookSpecification;
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
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.util.*;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import org.booklore.model.entity.ShelfEntity;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Produces lightweight aggregate book counts for the sidebar menu so the frontend
 * does not need to fetch the full book list on every app load just to render
 * counts next to libraries, shelves, and magic shelves.
 */
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
        return getMenuCounts(user);
    }

    public MenuCountsResponse getMenuCounts(BookLoreUser user) {
        if (user == null) {
            return new MenuCountsResponse(Map.of(), Map.of(), Map.of(), 0L, 0L);
        }

        Long userId = user.getId();
        Set<Long> accessibleLibraryIds = getAccessibleLibraryIds(user);

        Specification<BookEntity> visibleBooksSpec = AppBookSpecification.notDeleted();
        if (accessibleLibraryIds != null) {
            visibleBooksSpec = visibleBooksSpec.and(AppBookSpecification.inLibraries(accessibleLibraryIds));
        }

        Map<Long, Long> libraryCounts = computeLibraryCounts(user, accessibleLibraryIds);
        Map<Long, Long> shelfCounts = computeShelfCounts(user, visibleBooksSpec);
        Map<Long, Long> magicShelfCounts = computeMagicShelfCounts(userId);

        long totalBookCount = bookRepository.count(visibleBooksSpec);
        long unshelvedBookCount = bookRepository.count(visibleBooksSpec.and(AppBookSpecification.unshelved()));

        return new MenuCountsResponse(libraryCounts, shelfCounts, magicShelfCounts, totalBookCount, unshelvedBookCount);
    }

    private Map<Long, Long> computeLibraryCounts(BookLoreUser user, Set<Long> accessibleLibraryIds) {
        Map<Long, Long> counts = new LinkedHashMap<>();
        List<Library> libraries = libraryService.getLibraries(user);
        for (Library library : libraries) {
            if (library != null && library.getId() != null) {
                counts.put(library.getId(), 0L);
            }
        }

        String jpql = "SELECT b.library.id, COUNT(b) FROM BookEntity b WHERE (b.deleted IS NULL OR b.deleted = false)";
        if (accessibleLibraryIds != null) {
            jpql += " AND b.library.id IN :libIds";
        }
        jpql += " GROUP BY b.library.id";

        var query = entityManager.createQuery(jpql, Tuple.class);
        if (accessibleLibraryIds != null) {
            query.setParameter("libIds", accessibleLibraryIds);
        }

        query.getResultList().forEach(t -> counts.put(t.get(0, Long.class), t.get(1, Long.class)));
        return counts;
    }

    private Map<Long, Long> computeShelfCounts(BookLoreUser user, Specification<BookEntity> visibleBooksSpec) {
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (Shelf shelf : shelfService.getShelves(user)) {
            if (shelf.getId() != null) {
                counts.put(shelf.getId(), 0L);
            }
        }

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<BookEntity> root = cq.from(BookEntity.class);
        Join<BookEntity, ShelfEntity> shelfJoin = root.join("shelves");

        cq.multiselect(shelfJoin.get("id"), cb.count(root));
        cq.where(visibleBooksSpec.toPredicate(root, cq, cb));
        cq.groupBy(shelfJoin.get("id"));

        entityManager.createQuery(cq).getResultList().forEach(t ->
                counts.put(t.get(0, Long.class), t.get(1, Long.class)));

        return counts;
    }

    private Map<Long, Long> computeMagicShelfCounts(Long userId) {
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (MagicShelf shelf : magicShelfService.getUserShelves()) {
            if (shelf.getId() == null) {
                continue;
            }
            try {
                Specification<BookEntity> spec = magicShelfBookService.toSpecification(userId, shelf.getId());
                counts.put(shelf.getId(), bookRepository.count(spec));
            } catch (Exception e) {
                log.warn("Failed to compute magic shelf count for shelf {}: {}", shelf.getId(), e.getMessage());
                counts.put(shelf.getId(), 0L);
            }
        }
        return counts;
    }

    private Set<Long> getAccessibleLibraryIds(BookLoreUser user) {
        if (user.getPermissions() != null && user.getPermissions().isAdmin()) {
            return null;
        }
        if (user.getAssignedLibraries() == null || user.getAssignedLibraries().isEmpty()) {
            return Collections.emptySet();
        }
        return user.getAssignedLibraries().stream()
                .map(Library::getId)
                .collect(Collectors.toSet());
    }
}
