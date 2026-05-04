package org.booklore.app.service;

import lombok.AllArgsConstructor;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.ApiError;
import org.booklore.app.dto.AppAuthorDetail;
import org.booklore.app.dto.AppAuthorSummary;
import org.booklore.app.dto.AppPageResponse;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.Library;
import org.booklore.model.entity.*;
import org.booklore.repository.AuthorRepository;
import org.booklore.util.FileService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import org.booklore.app.specification.AppAuthorSpecification;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class AppAuthorService {

    private static final int DEFAULT_PAGE_SIZE = 30;
    private static final int MAX_PAGE_SIZE = 50;

    private final AuthorRepository authorRepository;
    private final AuthenticationService authenticationService;
    private final FileService fileService;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public AppPageResponse<AppAuthorSummary> getAuthors(
            Integer page,
            Integer size,
            String sortBy,
            String sortDir,
            Long libraryId,
            String search,
            Boolean hasPhoto) {

        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Set<Long> accessibleLibraryIds = getAccessibleLibraryIds(user);
        Collection<Long> effectiveLibraryIds = resolveEffectiveLibraryIds(accessibleLibraryIds, libraryId);

        int pageNum = page != null && page >= 0 ? page : 0;
        int pageSize = size != null && size > 0 ? Math.min(size, MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;

        Specification<AuthorEntity> spec = buildSpecification(effectiveLibraryIds, search, hasPhoto);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        // Count query
        CriteriaQuery<Long> countCq = cb.createQuery(Long.class);
        Root<AuthorEntity> countRoot = countCq.from(AuthorEntity.class);
        countCq.select(cb.countDistinct(countRoot.get(AuthorEntity_.id)));
        countCq.where(spec.toPredicate(countRoot, countCq, cb));

        long totalElements = entityManager.createQuery(countCq).getSingleResult();

        if (totalElements == 0) {
            return AppPageResponse.of(Collections.emptyList(), pageNum, pageSize, 0L);
        }

        // Data query with book count using Tuple for DTO projection
        CriteriaQuery<Tuple> dataCq = cb.createTupleQuery();
        Root<AuthorEntity> dataRoot = dataCq.from(AuthorEntity.class);

        // Pass the effective library filter for book count
        Expression<Long> bookCountExpr = AppAuthorSpecification.bookCount(dataCq, dataRoot, cb, effectiveLibraryIds);

        dataCq.select(cb.tuple(
            dataRoot.get(AuthorEntity_.id).alias("id"),
            dataRoot.get(AuthorEntity_.name).alias("name"),
            dataRoot.get(AuthorEntity_.asin).alias("asin"),
            dataRoot.get(AuthorEntity_.hasPhoto).alias("hasPhoto"),
            bookCountExpr.alias("bookCount")
        ));
        dataCq.where(spec.toPredicate(dataRoot, dataCq, cb));
        dataCq.groupBy(dataRoot);

        String normalizedSort = sortBy == null ? "" : sortBy.toLowerCase();
        boolean primaryIsId = switch (normalizedSort) {
            case "recent", "id" -> true;
            default -> false;
        };
        Expression<?> sortExpr = switch (normalizedSort) {
            case "bookcount", "book_count" -> bookCountExpr;
            case "recent", "id" -> dataRoot.get(AuthorEntity_.id);
            default -> dataRoot.get(AuthorEntity_.name);
        };

        Order primary = "asc".equalsIgnoreCase(sortDir) ? cb.asc(sortExpr) : cb.desc(sortExpr);
        if (primaryIsId) {
            dataCq.orderBy(primary);
        } else {
            dataCq.orderBy(primary, cb.asc(dataRoot.get(AuthorEntity_.id)));
        }

        TypedQuery<Tuple> dataQuery = entityManager.createQuery(dataCq);
        dataQuery.setFirstResult(pageNum * pageSize);
        dataQuery.setMaxResults(pageSize);

        List<Tuple> results = dataQuery.getResultList();

        List<AppAuthorSummary> summaries = results.stream()
                .map(row -> {
                    Long id = row.get("id", Long.class);
                    String name = row.get("name", String.class);
                    String asin = row.get("asin", String.class);
                    Long count = row.get("bookCount", Long.class);
                    boolean dbHasPhoto = Boolean.TRUE.equals(row.get("hasPhoto", Boolean.class));

                    // Check actual file existence for final summary report
                    boolean actualHasPhoto = Files.exists(Paths.get(fileService.getAuthorThumbnailFile(id)));

                    return AppAuthorSummary.builder()
                            .id(id)
                            .name(name)
                            .asin(asin)
                            .bookCount(count != null ? count.intValue() : 0)
                            .hasPhoto(actualHasPhoto)
                            .build();
                })
                .toList();

        return AppPageResponse.of(summaries, pageNum, pageSize, totalElements);
    }

    @Transactional(readOnly = true)
    public AppAuthorDetail getAuthorDetail(Long authorId) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Set<Long> accessibleLibraryIds = getAccessibleLibraryIds(user);

        AuthorEntity author = authorRepository.findById(authorId)
                .orElseThrow(() -> ApiError.AUTHOR_NOT_FOUND.createException(authorId));

        // Verify access for non-admin users
        if (accessibleLibraryIds != null) {
            if (accessibleLibraryIds.isEmpty() || !authorRepository.existsByIdAndLibraryIds(authorId, accessibleLibraryIds)) {
                throw ApiError.AUTHOR_NOT_FOUND.createException(authorId);
            }
        }

        // Count books accessible to this user
        int bookCount = countAccessibleBooks(authorId, accessibleLibraryIds);

        return AppAuthorDetail.builder()
                .id(author.getId())
                .name(author.getName())
                .description(author.getDescription())
                .asin(author.getAsin())
                .bookCount(bookCount)
                .hasPhoto(author.isHasPhoto())
                .build();
    }

    private int countAccessibleBooks(Long authorId, Set<Long> accessibleLibraryIds) {
        if (accessibleLibraryIds != null && accessibleLibraryIds.isEmpty()) {
            return 0;
        }

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<AuthorEntity> author = cq.from(AuthorEntity.class);

        Join<AuthorEntity, BookMetadataEntity> metadata = author.join(AuthorEntity_.bookMetadataEntityList);
        Join<BookMetadataEntity, BookEntity> book = metadata.join(BookMetadataEntity_.book);

        cq.select(cb.countDistinct(metadata.get(BookMetadataEntity_.bookId)));

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(author.get(AuthorEntity_.id), authorId));
        predicates.add(cb.or(
                cb.isNull(book.get(BookEntity_.deleted)),
                cb.equal(book.get(BookEntity_.deleted), false)
        ));
        predicates.add(cb.isNotEmpty(book.get(BookEntity_.bookFiles)));

        if (accessibleLibraryIds != null) {
            predicates.add(book.get(BookEntity_.library).get(LibraryEntity_.id).in(accessibleLibraryIds));
        }

        cq.where(predicates.toArray(new Predicate[0]));

        return entityManager.createQuery(cq).getSingleResult().intValue();
    }

    private Collection<Long> resolveEffectiveLibraryIds(Set<Long> accessibleLibraryIds, Long libraryId) {
        if (libraryId == null) {
            return accessibleLibraryIds;
        }
        if (accessibleLibraryIds == null) {
            return List.of(libraryId); // admin
        }
        return accessibleLibraryIds.contains(libraryId) ? List.of(libraryId) : List.of();
    }

    private Specification<AuthorEntity> buildSpecification(Collection<Long> effectiveLibraryIds, String search, Boolean hasPhoto) {
        Specification<AuthorEntity> spec = AppAuthorSpecification.visibleTo(effectiveLibraryIds);

        if (effectiveLibraryIds != null && effectiveLibraryIds.isEmpty()) {
            // Non-admin user with no library access: force empty result.
            spec = spec.and((root, query, cb) -> cb.disjunction());
        }

        if (search != null && !search.isBlank()) {
            spec = spec.and(AppAuthorSpecification.searchText(search));
        }

        if (hasPhoto != null) {
            spec = spec.and(AppAuthorSpecification.hasPhoto(hasPhoto));
        }

        return spec;
    }

    private Set<Long> getAccessibleLibraryIds(BookLoreUser user) {
        if (user.getPermissions().isAdmin()) {
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
