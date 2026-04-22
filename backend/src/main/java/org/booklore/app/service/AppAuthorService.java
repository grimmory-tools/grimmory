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
import org.booklore.repository.AuthorRepository;
import org.booklore.util.FileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.nio.file.Files;
import java.nio.file.Paths;
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

        StringBuilder whereClause = new StringBuilder(" WHERE (1=1)");
        buildLibraryFilter(whereClause, accessibleLibraryIds, libraryId);
        buildSearchFilter(whereClause, search);

        String fromClause = " FROM AuthorEntity a LEFT JOIN a.bookMetadataEntityList bm LEFT JOIN bm.book b";

        // Count query
        String countJpql = "SELECT COUNT(DISTINCT a.id)" + fromClause + whereClause;
        TypedQuery<Long> countQuery = entityManager.createQuery(countJpql, Long.class);
        setQueryParams(countQuery, accessibleLibraryIds, libraryId, search);
        long totalElements = countQuery.getSingleResult();

        if (totalElements == 0) {
            return AppPageResponse.of(Collections.emptyList(), pageNum, pageSize, 0L);
        }

        // Data query with book count
        String orderClause = buildOrderClause(sortBy, sortDir);
        String dataJpql = "SELECT a, COUNT(DISTINCT bm.id)" + fromClause + whereClause
                + " GROUP BY a" + orderClause;
        TypedQuery<Object[]> dataQuery = entityManager.createQuery(dataJpql, Object[].class);
        setQueryParams(dataQuery, accessibleLibraryIds, libraryId, search);
        dataQuery.setFirstResult(pageNum * pageSize);
        dataQuery.setMaxResults(pageSize);

        List<Object[]> results = dataQuery.getResultList();

        List<AppAuthorSummary> summaries = results.stream()
                .map(row -> {
                    AuthorEntity author = (AuthorEntity) row[0];
                    long bookCount = (Long) row[1];
                    boolean authorHasPhoto = Files.exists(Paths.get(fileService.getAuthorThumbnailFile(author.getId())));
                    return AppAuthorSummary.builder()
                            .id(author.getId())
                            .name(author.getName())
                            .asin(author.getAsin())
                            .bookCount((int) bookCount)
                            .hasPhoto(authorHasPhoto)
                            .build();
                })
                .collect(Collectors.toList());

        // Post-filter by hasPhoto if requested
        if (hasPhoto != null) {
            summaries = summaries.stream()
                    .filter(s -> s.isHasPhoto() == hasPhoto)
                    .collect(Collectors.toList());
            // Adjust total count for hasPhoto filter — requires a separate count
            long filteredTotal = countAuthorsWithPhotoFilter(accessibleLibraryIds, libraryId, search, hasPhoto);
            return AppPageResponse.of(summaries, pageNum, pageSize, filteredTotal);
        }

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
        boolean authorHasPhoto = Files.exists(Paths.get(fileService.getAuthorThumbnailFile(author.getId())));

        return AppAuthorDetail.builder()
                .id(author.getId())
                .name(author.getName())
                .description(author.getDescription())
                .asin(author.getAsin())
                .bookCount(bookCount)
                .hasPhoto(authorHasPhoto)
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

    private long countAuthorsWithPhotoFilter(Set<Long> accessibleLibraryIds, Long libraryId, String search, boolean hasPhoto) {
        // Since hasPhoto is file-system based, we need to count all matching authors
        // and check their photos. For large datasets this could be optimized with a DB column.
        StringBuilder whereClause = new StringBuilder(" WHERE (1=1)");
        buildLibraryFilter(whereClause, accessibleLibraryIds, libraryId);
        buildSearchFilter(whereClause, search);

        String jpql = "SELECT DISTINCT a FROM AuthorEntity a LEFT JOIN a.bookMetadataEntityList bm LEFT JOIN bm.book b"
                + whereClause;
        TypedQuery<AuthorEntity> query = entityManager.createQuery(jpql, AuthorEntity.class);
        setQueryParams(query, accessibleLibraryIds, libraryId, search);

        return query.getResultList().stream()
                .filter(a -> Files.exists(Paths.get(fileService.getAuthorThumbnailFile(a.getId()))) == hasPhoto)
                .count();
    }

    private void buildLibraryFilter(StringBuilder whereClause, Set<Long> accessibleLibraryIds, Long libraryId) {
        if (libraryId != null) {
            whereClause.append(" AND b.library.id = :libraryId");
        } else if (accessibleLibraryIds != null) {
            whereClause.append(" AND b.library.id IN :libraryIds");
        }
        whereClause.append(" AND (b.deleted IS NULL OR b.deleted = false)");
        whereClause.append(" AND b.bookFiles IS NOT EMPTY");
    }

    private void buildSearchFilter(StringBuilder whereClause, String search) {
        if (search != null && !search.trim().isEmpty()) {
            whereClause.append(" AND LOWER(a.name) LIKE :search");
        }
    }

    private String buildOrderClause(String sortBy, String sortDir) {
        String direction = "asc".equalsIgnoreCase(sortDir) ? "ASC" : "DESC";
        String field = switch (sortBy != null ? sortBy.toLowerCase() : "") {
            case "name" -> "a.name";
            case "bookcount", "book_count" -> "COUNT(DISTINCT bm.id)";
            case "recent", "id" -> "a.id";
            default -> "a.name";
        };
        return " ORDER BY " + field + " " + direction;
    }

    private void setQueryParams(TypedQuery<?> query, Set<Long> accessibleLibraryIds, Long libraryId, String search) {
        if (libraryId != null) {
            query.setParameter("libraryId", libraryId);
        } else if (accessibleLibraryIds != null) {
            query.setParameter("libraryIds", accessibleLibraryIds);
        }
        if (search != null && !search.trim().isEmpty()) {
            query.setParameter("search", "%" + search.trim().toLowerCase() + "%");
        }
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
