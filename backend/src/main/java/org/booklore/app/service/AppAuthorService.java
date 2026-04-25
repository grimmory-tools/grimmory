package org.booklore.app.service;

import lombok.AllArgsConstructor;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.ApiError;
import org.booklore.app.dto.AppAuthorDetail;
import org.booklore.app.dto.AppAuthorSummary;
import org.booklore.app.dto.AppPageResponse;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.Library;
import org.booklore.model.entity.AuthorEntity;
import org.booklore.model.entity.AuthorEntity_;
import org.booklore.repository.AuthorRepository;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.util.FileService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import org.booklore.app.specification.AppAuthorSpecification;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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

        int pageNum = page != null && page >= 0 ? page : 0;
        int pageSize = size != null && size > 0 ? Math.min(size, MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;

        Specification<AuthorEntity> spec = buildSpecification(accessibleLibraryIds, libraryId, search, hasPhoto);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        // Count query
        CriteriaQuery<Long> countCq = cb.createQuery(Long.class);
        Root<AuthorEntity> countRoot = countCq.from(AuthorEntity.class);
        countCq.select(cb.countDistinct(countRoot));
        countCq.where(spec.toPredicate(countRoot, countCq, cb));
        
        long totalElements = entityManager.createQuery(countCq).getSingleResult();

        if (totalElements == 0) {
            return AppPageResponse.of(Collections.emptyList(), pageNum, pageSize, 0L);
        }

        // Data query with book count using Tuple for DTO projection
        CriteriaQuery<Tuple> dataCq = cb.createTupleQuery();
        Root<AuthorEntity> dataRoot = dataCq.from(AuthorEntity.class);
        
        Expression<Long> bookCountExpr = AppAuthorSpecification.bookCountExpression(dataRoot, cb);

        dataCq.multiselect(
            dataRoot.get(AuthorEntity_.id).alias("id"),
            dataRoot.get(AuthorEntity_.name).alias("name"),
            dataRoot.get(AuthorEntity_.asin).alias("asin"),
            dataRoot.get(AuthorEntity_.hasPhoto).alias("hasPhoto"),
            bookCountExpr.alias("bookCount")
        );
        dataCq.where(spec.toPredicate(dataRoot, dataCq, cb));
        dataCq.groupBy(dataRoot);

        // Sorting logic using switch expression (Java 21+)
        Expression<?> sortExpr = switch (sortBy == null ? "" : sortBy.toLowerCase()) {
            case "bookcount", "book_count" -> bookCountExpr;
            case "recent", "id" -> dataRoot.get(AuthorEntity_.id);
            default -> dataRoot.get(AuthorEntity_.name);
        };

        dataCq.orderBy("asc".equalsIgnoreCase(sortDir) ? cb.asc(sortExpr) : cb.desc(sortExpr));

        TypedQuery<Tuple> dataQuery = entityManager.createQuery(dataCq);
        dataQuery.setFirstResult(pageNum * pageSize);
        dataQuery.setMaxResults(pageSize);

        List<Tuple> results = dataQuery.getResultList();

        List<AppAuthorSummary> summaries = results.stream()
                .map(tuple -> AppAuthorSummary.builder()
                        .id(tuple.get("id", Long.class))
                        .name(tuple.get("name", String.class))
                        .asin(tuple.get("asin", String.class))
                        .bookCount(tuple.get("bookCount", Long.class).intValue())
                        .hasPhoto(tuple.get("hasPhoto", Boolean.class))
                        .build())
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
        StringBuilder jpql = new StringBuilder(
                "SELECT COUNT(DISTINCT bm.id) FROM AuthorEntity a JOIN a.bookMetadataEntityList bm JOIN bm.book b"
                        + " WHERE a.id = :authorId AND (b.deleted IS NULL OR b.deleted = false)"
                        + " AND b.bookFiles IS NOT EMPTY");
        if (accessibleLibraryIds != null) {
            jpql.append(" AND b.library.id IN :libraryIds");
        }
        TypedQuery<Long> query = entityManager.createQuery(jpql.toString(), Long.class);
        query.setParameter("authorId", authorId);
        if (accessibleLibraryIds != null) {
            query.setParameter("libraryIds", accessibleLibraryIds);
        }
        return query.getSingleResult().intValue();
    }


    private Specification<AuthorEntity> buildSpecification(Set<Long> accessibleLibraryIds, Long libraryId, String search, Boolean hasPhoto) {
        Specification<AuthorEntity> spec = Specification.where(AppAuthorSpecification.notDeleted())
                .and(AppAuthorSpecification.hasDigitalFile());

        if (libraryId != null) {
            spec = spec.and(AppAuthorSpecification.inLibrary(libraryId));
        } else if (accessibleLibraryIds != null && !accessibleLibraryIds.isEmpty()) {
            spec = spec.and(AppAuthorSpecification.inLibraries(accessibleLibraryIds));
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
