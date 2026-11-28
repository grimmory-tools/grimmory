package org.booklore.app.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.AllArgsConstructor;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.ApiError;
import org.booklore.app.dto.*;
import org.booklore.app.mapper.AppBookMapper;
import org.booklore.app.specification.AppBookSpecification;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.Library;
import org.booklore.model.entity.*;
import org.booklore.model.enums.ReadStatus;
import org.booklore.repository.BookRepository;
import org.booklore.repository.UserBookProgressRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import jakarta.persistence.Query;

@Service
@AllArgsConstructor
public class AppSeriesService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final EntityManager entityManager;
    private final AuthenticationService authenticationService;
    private final BookRepository bookRepository;
    private final UserBookProgressRepository userBookProgressRepository;
    private final AppBookMapper mobileBookMapper;

    @Transactional(readOnly = true)
    public AppPageResponse<AppSeriesSummary> getSeries(
            Integer page,
            Integer size,
            String sortBy,
            String sortDir,
            Long libraryId,
            String search,
            String statusFilter) {

        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();
        Set<Long> accessibleLibraryIds = getAccessibleLibraryIds(user);

        if (libraryId != null) {
            validateLibraryAccess(accessibleLibraryIds, libraryId);
        }

        int pageNum = page != null && page >= 0 ? page : 0;
        int pageSize = size != null && size > 0 ? Math.min(size, MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;

        // Build WHERE clause fragments
        String libraryClause = buildLibraryClause(accessibleLibraryIds, libraryId);
        String searchClause = (search != null && !search.trim().isEmpty())
                ? " AND (LOWER(m.seriesName) LIKE :searchPattern OR EXISTS (SELECT a.id FROM BookMetadataEntity searchMetadata JOIN searchMetadata.authors a WHERE searchMetadata.bookId = b.id AND LOWER(a.name) LIKE :searchPattern))"
                : "";

        String havingClause = buildSeriesStatusHaving(statusFilter);

        String orderBy = buildSeriesOrderBy(sortBy, sortDir);

        // Phase 1: Aggregate query
        String aggregateQuery = "SELECT m.seriesName, COUNT(b.id), MAX(m.seriesTotal), MAX(b.addedOn),"
                + " SUM(CASE WHEN p.readStatus = org.booklore.model.enums.ReadStatus.READ THEN 1 ELSE 0 END),"
                + " MAX(p.lastReadTime),"
                + " SUM(CASE WHEN p.readStatus IN (org.booklore.model.enums.ReadStatus.READING, org.booklore.model.enums.ReadStatus.RE_READING, org.booklore.model.enums.ReadStatus.PAUSED) THEN 1 ELSE 0 END),"
                + " SUM(CASE WHEN p.readStatus = org.booklore.model.enums.ReadStatus.WONT_READ THEN 1 ELSE 0 END),"
                + " SUM(CASE WHEN p.readStatus = org.booklore.model.enums.ReadStatus.ABANDONED THEN 1 ELSE 0 END),"
                + " SUM(CASE WHEN p.readStatus IS NULL OR p.readStatus = org.booklore.model.enums.ReadStatus.UNREAD THEN 1 ELSE 0 END)"
                + " FROM BookEntity b JOIN b.metadata m"
                + " LEFT JOIN b.userBookProgress p ON p.user.id = :userId"
                + " WHERE (b.deleted IS NULL OR b.deleted = false)"
                + " AND (b.bookFiles IS NOT EMPTY OR b.isPhysical = true)"
                + " AND m.seriesName IS NOT NULL"
                + libraryClause
                + searchClause
                + " GROUP BY m.seriesName"
                + havingClause
                + " ORDER BY " + orderBy;

        var aggregateQ = entityManager.createQuery(aggregateQuery, Tuple.class);
        aggregateQ.setParameter("userId", userId);
        setLibraryParams(aggregateQ, accessibleLibraryIds, libraryId);
        if (!searchClause.isEmpty()) {
            aggregateQ.setParameter("searchPattern", "%" + search.trim().toLowerCase() + "%");
        }
        aggregateQ.setFirstResult(pageNum * pageSize);
        aggregateQ.setMaxResults(pageSize);

        List<Tuple> aggregateResults = aggregateQ.getResultList();

        // Count query
        String countQuery = "SELECT COUNT(DISTINCT m.seriesName) FROM BookEntity b JOIN b.metadata m"
                + (havingClause.isEmpty() ? "" : " LEFT JOIN b.userBookProgress p ON p.user.id = :userId")
                + " WHERE (b.deleted IS NULL OR b.deleted = false)"
                + " AND (b.bookFiles IS NOT EMPTY OR b.isPhysical = true)"
                + " AND m.seriesName IS NOT NULL"
                + libraryClause
                + searchClause;

        if (!havingClause.isEmpty()) {
            // JPQL does not support subqueries in FROM; count the grouped series names directly.
            String countAlt = "SELECT m.seriesName FROM BookEntity b JOIN b.metadata m"
                    + " LEFT JOIN b.userBookProgress p ON p.user.id = :userId"
                    + " WHERE (b.deleted IS NULL OR b.deleted = false)"
                    + " AND (b.bookFiles IS NOT EMPTY OR b.isPhysical = true)"
                    + " AND m.seriesName IS NOT NULL"
                    + libraryClause
                    + searchClause
                    + " GROUP BY m.seriesName"
                    + havingClause;
            var countQ = entityManager.createQuery(countAlt, String.class);
            countQ.setParameter("userId", userId);
            setLibraryParams(countQ, accessibleLibraryIds, libraryId);
            if (!searchClause.isEmpty()) {
                countQ.setParameter("searchPattern", "%" + search.trim().toLowerCase() + "%");
            }
            long totalElements = countQ.getResultList().size();
            return buildSeriesPage(aggregateResults, userId, accessibleLibraryIds, libraryId, pageNum, pageSize, totalElements);
        }

        var countQ = entityManager.createQuery(countQuery, Long.class);
        setLibraryParams(countQ, accessibleLibraryIds, libraryId);
        if (!searchClause.isEmpty()) {
            countQ.setParameter("searchPattern", "%" + search.trim().toLowerCase() + "%");
        }
        long totalElements = countQ.getSingleResult();

        return buildSeriesPage(aggregateResults, userId, accessibleLibraryIds, libraryId, pageNum, pageSize, totalElements);
    }

    private AppPageResponse<AppSeriesSummary> buildSeriesPage(
            List<Tuple> aggregateResults,
            Long userId,
            Set<Long> accessibleLibraryIds,
            Long libraryId,
            int pageNum,
            int pageSize,
            long totalElements) {

        if (aggregateResults.isEmpty()) {
            return AppPageResponse.of(Collections.emptyList(), pageNum, pageSize, totalElements);
        }

        List<String> seriesNames = aggregateResults.stream()
                .map(t -> t.get(0, String.class))
                .toList();

        // Phase 2: Fetch books for enrichment (only ToOne joins; collections loaded via @BatchSize)
        String libraryClause = buildLibraryClause(accessibleLibraryIds, libraryId);
        String booksQuery = "SELECT b FROM BookEntity b"
                + " JOIN FETCH b.metadata m"
                + " WHERE m.seriesName IN :seriesNames"
                + " AND (b.deleted IS NULL OR b.deleted = false)"
                + " AND (b.bookFiles IS NOT EMPTY OR b.isPhysical = true)"
                + libraryClause;

        var booksQ = entityManager.createQuery(booksQuery, BookEntity.class);
        booksQ.setParameter("seriesNames", seriesNames);
        setLibraryParams(booksQ, accessibleLibraryIds, libraryId);

        List<BookEntity> books = booksQ.getResultList();
        Set<Long> bookIds = books.stream().map(BookEntity::getId).collect(Collectors.toSet());
        Map<Long, UserBookProgressEntity> progressByBookId = getProgressMap(userId, bookIds);

        // Group books by series name
        Map<String, List<BookEntity>> booksBySeries = books.stream()
                .collect(Collectors.groupingBy(b -> b.getMetadata().getSeriesName()));

        // Build aggregates map from Phase 1
        Map<String, Tuple> aggregateMap = new LinkedHashMap<>();
        for (Tuple t : aggregateResults) {
            aggregateMap.put(t.get(0, String.class), t);
        }

        // Merge into summaries, preserving Phase 1 order
        List<AppSeriesSummary> summaries = new ArrayList<>();
        for (String seriesName : seriesNames) {
            Tuple agg = aggregateMap.get(seriesName);
            List<BookEntity> seriesBooks = booksBySeries.getOrDefault(seriesName, Collections.emptyList());

            // Distinct authors across all books in series
            List<String> authors = seriesBooks.stream()
                    .filter(b -> b.getMetadata() != null && b.getMetadata().getAuthors() != null)
                    .flatMap(b -> b.getMetadata().getAuthors().stream())
                    .map(AuthorEntity::getName)
                    .distinct()
                    .toList();

            // Cover books sorted by seriesNumber ASC nulls last
            List<BookEntity> sortedSeriesBooks = seriesBooks.stream()
                    .sorted(Comparator.comparing(
                            (BookEntity b) -> b.getMetadata().getSeriesNumber(),
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
            List<SeriesCoverBook> coverBooks = sortedSeriesBooks.stream()
                    .limit(7)
                    .map(book -> toSeriesCoverBook(book, progressByBookId.get(book.getId())))
                    .toList();

            SeriesCoverBook nextUnread = sortedSeriesBooks.stream()
                    .filter(book -> progressByBookId.get(book.getId()) == null
                            || progressByBookId.get(book.getId()).getReadStatus() != ReadStatus.READ)
                    .findFirst()
                    .map(book -> toSeriesCoverBook(book, progressByBookId.get(book.getId())))
                    .orElse(null);

            Long booksReadLong = agg.get(4, Long.class);
            int booksRead = booksReadLong != null ? booksReadLong.intValue() : 0;
            int bookCount = agg.get(1, Long.class).intValue();
            Instant lastReadTime = agg.get(5, Instant.class);
            ReadStatus seriesStatus = deriveSeriesStatus(
                    bookCount,
                    booksRead,
                    tupleCount(agg, 6),
                    tupleCount(agg, 7),
                    tupleCount(agg, 8),
                    tupleCount(agg, 9));

            summaries.add(AppSeriesSummary.builder()
                    .seriesName(agg.get(0, String.class))
                    .bookCount(bookCount)
                    .seriesTotal(agg.get(2, Integer.class))
                    .latestAddedOn(agg.get(3, Instant.class))
                    .lastReadTime(lastReadTime)
                    .booksRead(booksRead)
                    .seriesStatus(seriesStatus)
                    .authors(authors)
                    .coverBooks(coverBooks)
                    .nextUnread(nextUnread)
                    .build());
        }

        return AppPageResponse.of(summaries, pageNum, pageSize, totalElements);
    }

    @Transactional(readOnly = true)
    public AppPageResponse<AppBookSummary> getSeriesBooks(
            String seriesName,
            Integer page,
            Integer size,
            String sortBy,
            String sortDir,
            Long libraryId) {

        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();
        Set<Long> accessibleLibraryIds = getAccessibleLibraryIds(user);

        if (libraryId != null) {
            validateLibraryAccess(accessibleLibraryIds, libraryId);
        }

        int pageNum = page != null && page >= 0 ? page : 0;
        int pageSize = size != null && size > 0 ? Math.min(size, MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;

        Sort sort = buildBookSort(sortBy, sortDir);
        Pageable pageable = PageRequest.of(pageNum, pageSize, sort);

        Specification<BookEntity> spec = buildSeriesBooksSpec(accessibleLibraryIds, libraryId, seriesName);

        Page<BookEntity> bookPage = bookRepository.findAll(spec, pageable);

        Set<Long> bookIds = bookPage.getContent().stream()
                .map(BookEntity::getId)
                .collect(Collectors.toSet());
        Map<Long, UserBookProgressEntity> progressMap = getProgressMap(userId, bookIds);

        List<AppBookSummary> summaries = bookPage.getContent().stream()
                .map(book -> mobileBookMapper.toSummary(book, progressMap.get(book.getId())))
                .toList();

        return AppPageResponse.of(summaries, pageNum, pageSize, bookPage.getTotalElements());
    }

    // --- Access control helpers (duplicated from AppBookService to minimize blast radius) ---

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

    private void validateLibraryAccess(Set<Long> accessibleLibraryIds, Long libraryId) {
        if (accessibleLibraryIds != null && !accessibleLibraryIds.contains(libraryId)) {
            throw ApiError.FORBIDDEN.createException("Access denied to library " + libraryId);
        }
    }

    // --- Query helpers ---

    private String buildLibraryClause(Set<Long> accessibleLibraryIds, Long libraryId) {
        if (libraryId != null) {
            return " AND b.library.id = :libraryId";
        } else if (accessibleLibraryIds != null) {
            return " AND b.library.id IN :libraryIds";
        }
        return "";
    }

    private void setLibraryParams(Query query, Set<Long> accessibleLibraryIds, Long libraryId) {
        if (libraryId != null) {
            query.setParameter("libraryId", libraryId);
        } else if (accessibleLibraryIds != null) {
            query.setParameter("libraryIds", accessibleLibraryIds);
        }
    }

    private SeriesCoverBook toSeriesCoverBook(BookEntity book, UserBookProgressEntity progress) {
        BookFileEntity primaryFile = book.getPrimaryBookFile();
        return SeriesCoverBook.builder()
                .bookId(book.getId())
                .coverUpdatedOn(book.getMetadata().getCoverUpdatedOn())
                .audiobookCoverUpdatedOn(book.getMetadata().getAudiobookCoverUpdatedOn())
                .seriesNumber(book.getMetadata().getSeriesNumber())
                .primaryFileType(primaryFile != null && primaryFile.getBookType() != null ? primaryFile.getBookType().name() : null)
                .title(book.getMetadata().getTitle())
                .authors(book.getMetadata().getAuthors().stream().map(AuthorEntity::getName).toList())
                .readStatus(progress != null && progress.getReadStatus() != null ? progress.getReadStatus() : ReadStatus.UNREAD)
                .build();
    }

    private int tupleCount(Tuple tuple, int index) {
        Long value = tuple.get(index, Long.class);
        return value == null ? 0 : value.intValue();
    }

    private ReadStatus deriveSeriesStatus(int total, int read, int active, int wontRead, int abandoned, int unread) {
        if (wontRead > 0) return ReadStatus.WONT_READ;
        if (abandoned > 0) return ReadStatus.ABANDONED;
        if (read == total) return ReadStatus.READ;
        if (active > 0) return ReadStatus.READING;
        if (read > 0) return ReadStatus.PARTIALLY_READ;
        if (unread == total) return ReadStatus.UNREAD;
        return ReadStatus.PARTIALLY_READ;
    }

    private String buildSeriesStatusHaving(String status) {
        if (status == null) return "";
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "not-started" -> " HAVING SUM(CASE WHEN p.readStatus IS NULL OR p.readStatus = org.booklore.model.enums.ReadStatus.UNREAD THEN 1 ELSE 0 END) = COUNT(b.id)";
            case "in-progress" -> " HAVING SUM(CASE WHEN p.readStatus IN (org.booklore.model.enums.ReadStatus.READING, org.booklore.model.enums.ReadStatus.RE_READING, org.booklore.model.enums.ReadStatus.PAUSED, org.booklore.model.enums.ReadStatus.PARTIALLY_READ) THEN 1 ELSE 0 END) > 0";
            case "completed" -> " HAVING SUM(CASE WHEN p.readStatus = org.booklore.model.enums.ReadStatus.READ THEN 1 ELSE 0 END) = COUNT(b.id)";
            case "abandoned" -> " HAVING SUM(CASE WHEN p.readStatus IN (org.booklore.model.enums.ReadStatus.ABANDONED, org.booklore.model.enums.ReadStatus.WONT_READ) THEN 1 ELSE 0 END) > 0";
            default -> "";
        };
    }

    private String buildSeriesOrderBy(String sortBy, String sortDir) {
        String dir = "asc".equalsIgnoreCase(sortDir) ? "ASC" : "DESC";
        String nullsClause = "ASC".equals(dir) ? " NULLS LAST" : " NULLS FIRST";

        return switch (sortBy != null ? sortBy.toLowerCase() : "") {
            case "name" -> "m.seriesName " + dir;
            case "bookcount" -> "COUNT(b.id) " + dir;
            case "readprogress" -> "SUM(CASE WHEN p.readStatus = org.booklore.model.enums.ReadStatus.READ THEN 1 ELSE 0 END) " + dir;
            case "lastreadtime" -> "MAX(p.lastReadTime) " + dir + nullsClause;
            default -> "MAX(b.addedOn) " + dir + nullsClause;
        };
    }

    private Sort buildBookSort(String sortBy, String sortDir) {
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String field = switch (sortBy != null ? sortBy.toLowerCase() : "") {
            case "title" -> "metadata.title";
            case "seriesnumber" -> "metadata.seriesNumber";
            case "recentlyadded" -> "addedOn";
            default -> "metadata.seriesNumber";
        };
        return Sort.by(direction, field);
    }

    private Specification<BookEntity> buildSeriesBooksSpec(Set<Long> accessibleLibraryIds, Long libraryId, String seriesName) {
        List<Specification<BookEntity>> specs = new ArrayList<>();
        specs.add(AppBookSpecification.notDeleted());
        specs.add(AppBookSpecification.hasDigitalFileOrIsPhysical());
        specs.add(AppBookSpecification.inSeries(seriesName));

        if (accessibleLibraryIds != null) {
            specs.add(libraryId != null
                    ? AppBookSpecification.inLibrary(libraryId)
                    : AppBookSpecification.inLibraries(accessibleLibraryIds));
        } else if (libraryId != null) {
            specs.add(AppBookSpecification.inLibrary(libraryId));
        }

        return AppBookSpecification.combine(specs.toArray(Specification[]::new));
    }

    private Map<Long, UserBookProgressEntity> getProgressMap(Long userId, Set<Long> bookIds) {
        if (bookIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userBookProgressRepository.findByUserIdAndBookIdIn(userId, bookIds).stream()
                .collect(Collectors.toMap(
                        p -> p.getBook().getId(),
                        Function.identity()
                ));
    }
}
