package org.booklore.app.service;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.ApiError;
import org.booklore.app.dto.AppBookDetail;
import org.booklore.app.dto.AppBookProgressResponse;
import org.booklore.app.dto.AppBookSummary;
import org.booklore.app.dto.AppFilterOptions;
import org.booklore.app.dto.AppPageResponse;
import org.booklore.app.dto.UpdateProgressRequest;
import org.booklore.app.dto.BookListRequest;
import org.booklore.app.mapper.AppBookMapper;
import org.booklore.app.specification.AppBookSpecification;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.Library;
import org.booklore.model.dto.request.ReadProgressRequest;
import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.ComicCreatorRole;
import org.booklore.model.enums.ReadStatus;
import org.booklore.repository.BookRepository;
import org.booklore.repository.ShelfRepository;
import org.booklore.repository.UserBookFileProgressRepository;
import org.booklore.repository.UserBookProgressRepository;
import org.booklore.service.book.BookService;
import org.booklore.service.opds.MagicShelfBookService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

@Service
@Transactional(readOnly = true)
public class AppBookService {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 50;
    private static final String DEFAULT_SORT = "addedon";

    private final BookRepository bookRepository;
    private final UserBookProgressRepository userBookProgressRepository;
    private final UserBookFileProgressRepository userBookFileProgressRepository;
    private final ShelfRepository shelfRepository;
    private final AuthenticationService authenticationService;
    private final AppBookMapper mobileBookMapper;
    private final BookService bookService;
    private final MagicShelfBookService magicShelfBookService;
    private final EntityManager entityManager;

    private final Cache<String, AppFilterOptions> filterOptionsCache = Caffeine.newBuilder()
            .expireAfterWrite(java.time.Duration.ofSeconds(30))
            .maximumSize(50)
            .build();

    public AppBookService(BookRepository bookRepository,
                          UserBookProgressRepository userBookProgressRepository,
                          UserBookFileProgressRepository userBookFileProgressRepository,
                          ShelfRepository shelfRepository,
                          AuthenticationService authenticationService,
                          AppBookMapper mobileBookMapper,
                          BookService bookService,
                          MagicShelfBookService magicShelfBookService,
                          EntityManager entityManager) {
        this.bookRepository = bookRepository;
        this.userBookProgressRepository = userBookProgressRepository;
        this.userBookFileProgressRepository = userBookFileProgressRepository;
        this.shelfRepository = shelfRepository;
        this.authenticationService = authenticationService;
        this.mobileBookMapper = mobileBookMapper;
        this.bookService = bookService;
        this.magicShelfBookService = magicShelfBookService;
        this.entityManager = entityManager;
    }

    public AppPageResponse<AppBookSummary> getBooks(BookListRequest req) {

        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();
        Set<Long> accessibleLibraryIds = getAccessibleLibraryIds(user);

        int pageNum = req.page() != null && req.page() >= 0 ? req.page() : 0;
        int pageSize = req.size() != null && req.size() > 0 ? Math.min(req.size(), MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;

        // Handle magic shelf: compose the DB-side specification directly (no IN-list)
        if (req.magicShelfId() != null) {
            Sort sort = buildSort(req.sort(), req.dir());
            Pageable pageable = PageRequest.of(pageNum, pageSize, sort);

            Specification<BookEntity> spec = buildSpecification(
                    accessibleLibraryIds, userId, req);
            spec = spec.and(magicShelfBookService.toSpecification(userId, req.magicShelfId()));

            if (Boolean.TRUE.equals(req.unshelved())) {
                spec = spec.and(AppBookSpecification.unshelved());
            }

            Page<BookEntity> bookPage = bookRepository.findAll(spec, pageable);
            return buildPageResponse(bookPage, userId, pageNum, pageSize);
        }

        Sort sort = buildSort(req.sort(), req.dir());
        Pageable pageable = PageRequest.of(pageNum, pageSize, sort);

        Specification<BookEntity> spec = buildSpecification(
                accessibleLibraryIds, userId, req);

        if (Boolean.TRUE.equals(req.unshelved())) {
            spec = spec.and(AppBookSpecification.unshelved());
        }

        Page<BookEntity> bookPage = bookRepository.findAll(spec, pageable);
        return buildPageResponse(bookPage, userId, pageNum, pageSize);
    }

    public List<Long> getAllBookIds(BookListRequest req) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();
        Set<Long> accessibleLibraryIds = getAccessibleLibraryIds(user);

        Specification<BookEntity> spec = buildSpecification(accessibleLibraryIds, userId, req);

        if (req.magicShelfId() != null) {
            spec = spec.and(magicShelfBookService.toSpecification(userId, req.magicShelfId()));
        }

        if (Boolean.TRUE.equals(req.unshelved())) {
            spec = spec.and(AppBookSpecification.unshelved());
        }

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<BookEntity> root = cq.from(BookEntity.class);
        cq.select(root.get("id"));

        if (spec != null) {
            cq.where(spec.toPredicate(root, cq, cb));
        }

        return entityManager.createQuery(cq).getResultList();
    }

    public AppBookDetail getBookDetail(Long bookId) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();
        Set<Long> accessibleLibraryIds = getAccessibleLibraryIds(user);

        BookEntity book = bookRepository.findByIdWithBookFiles(bookId)
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        if (accessibleLibraryIds != null && !accessibleLibraryIds.contains(book.getLibrary().getId())) {
            throw ApiError.FORBIDDEN.createException("Access denied to this book");
        }

        UserBookProgressEntity progress = userBookProgressRepository
                .findByUserIdAndBookId(userId, bookId)
                .orElse(null);

        UserBookFileProgressEntity fileProgress = userBookFileProgressRepository
                .findMostRecentAudiobookProgressByUserIdAndBookId(userId, bookId)
                .orElse(null);

        return mobileBookMapper.toDetail(book, progress, fileProgress);
    }

    @Transactional(readOnly = true)
    public AppBookProgressResponse getBookProgress(Long bookId) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();
        Set<Long> accessibleLibraryIds = getAccessibleLibraryIds(user);

        BookEntity book = bookRepository.findByIdWithBookFiles(bookId)
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        if (accessibleLibraryIds != null && !accessibleLibraryIds.contains(book.getLibrary().getId())) {
            throw ApiError.FORBIDDEN.createException("Access denied to this book");
        }

        UserBookProgressEntity progress = userBookProgressRepository
                .findByUserIdAndBookId(userId, bookId)
                .orElse(null);

        UserBookFileProgressEntity fileProgress = userBookFileProgressRepository
                .findMostRecentAudiobookProgressByUserIdAndBookId(userId, bookId)
                .orElse(null);

        return mobileBookMapper.toProgressResponse(progress, fileProgress);
    }

    @Transactional
    public void updateBookProgress(Long bookId, UpdateProgressRequest request) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Set<Long> accessibleLibraryIds = getAccessibleLibraryIds(user);

        BookEntity book = bookRepository.findByIdWithBookFiles(bookId)
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        validateLibraryAccess(accessibleLibraryIds, book.getLibrary().getId());

        ReadProgressRequest progressRequest = new ReadProgressRequest();
        progressRequest.setBookId(bookId);
        progressRequest.setFileProgress(request.getFileProgress());
        progressRequest.setEpubProgress(request.getEpubProgress());
        progressRequest.setPdfProgress(request.getPdfProgress());
        progressRequest.setCbxProgress(request.getCbxProgress());
        progressRequest.setAudiobookProgress(request.getAudiobookProgress());
        progressRequest.setDateFinished(request.getDateFinished());

        bookService.updateReadProgress(progressRequest);
    }

    @Transactional(readOnly = true)
    public AppPageResponse<AppBookSummary> searchBooks(
            String query,
            Integer page,
            Integer size) {

        if (query == null || query.trim().isEmpty()) {
            throw ApiError.INVALID_QUERY_PARAMETERS.createException();
        }

        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();
        Set<Long> accessibleLibraryIds = getAccessibleLibraryIds(user);

        int pageNum = validatePageNumber(page);
        int pageSize = validatePageSize(size);

        Pageable pageable = PageRequest.of(pageNum, pageSize, Sort.by(Sort.Direction.DESC, "addedOn"));

        Specification<BookEntity> spec = AppBookSpecification.combine(
                AppBookSpecification.notDeleted(),
                AppBookSpecification.hasDigitalFile(),
                AppBookSpecification.inLibraries(accessibleLibraryIds),
                AppBookSpecification.searchText(query)
        );

        Page<BookEntity> bookPage = bookRepository.findAll(spec, pageable);
        return buildPageResponse(bookPage, userId, pageNum, pageSize);
    }

    public List<AppBookSummary> getContinueReading(Integer limit) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();
        Set<Long> accessibleLibraryIds = getAccessibleLibraryIds(user);

        int maxItems = validateLimit(limit, 10);

        List<Long> topIds = userBookProgressRepository.findTopContinueReadingBookIds(
                userId, accessibleLibraryIds, PageRequest.of(0, maxItems));

        if (topIds.isEmpty()) return Collections.emptyList();

        Map<Long, UserBookProgressEntity> progressMap = getProgressMap(userId, new HashSet<>(topIds));

        Map<Long, BookEntity> enrichedMap = bookRepository.findAllById(topIds)
                .stream().collect(Collectors.toMap(BookEntity::getId, b -> b));

        return topIds.stream()
                .filter(enrichedMap::containsKey)
                .map(id -> mobileBookMapper.toSummary(enrichedMap.get(id), progressMap.get(id)))
                .collect(Collectors.toList());
    }

    public List<AppBookSummary> getContinueListening(Integer limit) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();
        Set<Long> accessibleLibraryIds = getAccessibleLibraryIds(user);

        int maxItems = validateLimit(limit, 10);

        List<Long> topIds = userBookProgressRepository.findTopContinueListeningBookIds(
                userId, accessibleLibraryIds, PageRequest.of(0, maxItems));

        if (topIds.isEmpty()) return Collections.emptyList();

        Map<Long, UserBookProgressEntity> progressMap = getProgressMap(userId, new HashSet<>(topIds));

        Map<Long, BookEntity> enrichedMap = bookRepository.findAllById(topIds)
                .stream().collect(Collectors.toMap(BookEntity::getId, b -> b));

        return topIds.stream()
                .filter(enrichedMap::containsKey)
                .map(id -> mobileBookMapper.toSummary(enrichedMap.get(id), progressMap.get(id)))
                .collect(Collectors.toList());
    }

    public List<AppBookSummary> getRecentlyAdded(Integer limit) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();
        Set<Long> accessibleLibraryIds = getAccessibleLibraryIds(user);

        int maxItems = validateLimit(limit, 10);

        Specification<BookEntity> spec = AppBookSpecification.combine(
                AppBookSpecification.notDeleted(),
                AppBookSpecification.hasDigitalFile(),
                AppBookSpecification.inLibraries(accessibleLibraryIds),
                AppBookSpecification.addedWithinDays(30)
        );

        Pageable pageable = PageRequest.of(0, maxItems, Sort.by(Sort.Direction.DESC, "addedOn"));
        Page<BookEntity> bookPage = bookRepository.findAll(spec, pageable);
        Map<Long, UserBookProgressEntity> progressMap = getProgressMapForBooks(userId, bookPage.getContent());

        return bookPage.getContent().stream()
                .map(book -> mobileBookMapper.toSummary(book, progressMap.get(book.getId())))
                .collect(Collectors.toList());
    }

    public List<AppBookSummary> getRecentlyScanned(Integer limit) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();
        Set<Long> accessibleLibraryIds = getAccessibleLibraryIds(user);

        int maxItems = validateLimit(limit, 10);

        Specification<BookEntity> spec = AppBookSpecification.combine(
                AppBookSpecification.notDeleted(),
                AppBookSpecification.hasScannedOn(),
                AppBookSpecification.inLibraries(accessibleLibraryIds)
        );

        Pageable pageable = PageRequest.of(0, maxItems, Sort.by(Sort.Direction.DESC, "scannedOn"));
        Page<BookEntity> bookPage = bookRepository.findAll(spec, pageable);
        Map<Long, UserBookProgressEntity> progressMap = getProgressMapForBooks(userId, bookPage.getContent());

        return bookPage.getContent().stream()
                .map(book -> mobileBookMapper.toSummary(book, progressMap.get(book.getId())))
                .collect(Collectors.toList());
    }

    public AppPageResponse<AppBookSummary> getRandomBooks(
            Integer page,
            Integer size,
            Long libraryId) {

        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();
        Set<Long> accessibleLibraryIds = getAccessibleLibraryIds(user);

        int pageNum = validatePageNumber(page);
        int pageSize = validatePageSize(size);

        Specification<BookEntity> spec = buildBaseSpecification(accessibleLibraryIds, libraryId);

        long totalElements = bookRepository.count(spec);

        if (totalElements == 0) {
            return AppPageResponse.of(Collections.emptyList(), pageNum, pageSize, 0L);
        }

        long maxOffset = Math.max(0, totalElements - pageSize);
        int randomOffset = ThreadLocalRandom.current().nextInt((int) maxOffset + 1);

        Pageable pageable = PageRequest.of(randomOffset / pageSize, pageSize);
        Page<BookEntity> bookPage = bookRepository.findAll(spec, pageable);

        return buildPageResponse(bookPage, userId, pageNum, pageSize);
    }

    public AppPageResponse<AppBookSummary> getBooksByMagicShelf(
            Long magicShelfId,
            Integer page,
            Integer size) {

        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();

        int pageNum = validatePageNumber(page);
        int pageSize = validatePageSize(size);

        var booksPage = magicShelfBookService.getBooksByMagicShelfId(userId, magicShelfId, pageNum, pageSize);

        List<Long> orderedBookIds = booksPage.getContent().stream()
                .map(Book::getId)
                .toList();

        if (orderedBookIds.isEmpty()) {
            return AppPageResponse.of(Collections.emptyList(), pageNum, pageSize, 0L);
        }

        Map<Long, BookEntity> bookEntitiesById = bookRepository.findAllById(orderedBookIds).stream()
                .collect(Collectors.toMap(BookEntity::getId, Function.identity()));
        Map<Long, UserBookProgressEntity> progressMap = getProgressMap(userId, bookEntitiesById.keySet());

        List<AppBookSummary> summaries = orderedBookIds.stream()
                .map(bookEntitiesById::get)
                .filter(Objects::nonNull)
                .filter(BookEntity::hasFiles)
                .map(bookEntity -> mobileBookMapper.toSummary(bookEntity, progressMap.get(bookEntity.getId())))
                .collect(Collectors.toList());

        return AppPageResponse.of(summaries, pageNum, pageSize, booksPage.getTotalElements());
    }

    public AppFilterOptions getFilterOptions(Long libraryId, Long shelfId, Long magicShelfId) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();
        Set<Long> accessibleLibraryIds = getAccessibleLibraryIds(user);

        // Validate library access
        if (libraryId != null && accessibleLibraryIds != null && !accessibleLibraryIds.contains(libraryId)) {
            throw ApiError.FORBIDDEN.createException("Access denied to library " + libraryId);
        }

        // Validate shelf access
        if (shelfId != null) {
            ShelfEntity shelf = shelfRepository.findById(shelfId)
                    .orElseThrow(() -> ApiError.SHELF_NOT_FOUND.createException(shelfId));
            if (!shelf.isPublic() && !shelf.getUser().getId().equals(userId)) {
                throw ApiError.FORBIDDEN.createException("Access denied to shelf " + shelfId);
            }
        }

        // Cache lookup avoid re-running 26+ aggregate queries within the TTL window
        String cacheKey = userId + ":" + libraryId + ":" + shelfId + ":" + magicShelfId;
        AppFilterOptions cached = filterOptionsCache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }

        // Build unified scoping Specification for all aggregate queries
        Specification<BookEntity> baseSpec = buildBaseSpecification(accessibleLibraryIds, libraryId);
        Specification<BookEntity> magicShelfSpec = null;
        if (magicShelfId != null) {
            magicShelfSpec = magicShelfBookService.toSpecification(userId, magicShelfId);
            baseSpec = baseSpec.and(magicShelfSpec);
        }
        if (shelfId != null) {
            baseSpec = baseSpec.and(AppBookSpecification.inShelf(shelfId));
        }

        Set<Long> magicBookIds = null;
        if (magicShelfId != null) {
            magicBookIds = resolveMagicShelfBookIds(magicShelfSpec);
            if (magicBookIds.isEmpty()) {
                return emptyFilterOptions(cacheKey);
            }
        }

        String libraryClause = "";
        String shelfClause = "";
        String magicBookClause = "";
        if (magicBookIds != null) {
            magicBookClause = "AND b.id IN :magicBookIds";
        } else if (shelfId != null) {
            shelfClause = "AND b.id IN (SELECT sb.id FROM ShelfEntity s JOIN s.bookEntities sb WHERE s.id = :shelfId)";
        }
        if (libraryId != null) {
            libraryClause = "AND b.library.id = :libraryId";
        } else if (accessibleLibraryIds != null) {
            libraryClause = "AND b.library.id IN :libraryIds";
        }
        String scopeClause = buildScopeClause(libraryClause, shelfClause, magicBookClause);

        List<AppFilterOptions.CountedOption> authors = queryCountedOptions(
                "a.name", "JOIN b.metadata m JOIN m.authors a", "",
                scopeClause, accessibleLibraryIds, libraryId, shelfId, magicBookIds);

        List<AppFilterOptions.LanguageOption> languages = queryCountedOptions(
                "m.language", "JOIN b.metadata m",
                "AND m.language IS NOT NULL AND m.language <> ''",
                scopeClause, accessibleLibraryIds, libraryId, shelfId, magicBookIds).stream()
                .map(c -> new AppFilterOptions.LanguageOption(
                        c.name(),
                        Locale.forLanguageTag(c.name()).getDisplayLanguage(Locale.ENGLISH),
                        c.count()))
                .toList();

        List<AppFilterOptions.CountedOption> fileTypes = queryCountedOptions(
                "bf.bookType", "JOIN b.bookFiles bf", "AND bf.isBookFormat = true",
                scopeClause, accessibleLibraryIds, libraryId, shelfId, magicBookIds);

        List<AppFilterOptions.CountedOption> categories = queryCountedOptions(
                "c.name", "JOIN b.metadata m JOIN m.categories c", "",
                scopeClause, accessibleLibraryIds, libraryId, shelfId, magicBookIds);

        List<AppFilterOptions.CountedOption> publishers = queryCountedOptions(
                "m.publisher", "JOIN b.metadata m",
                "AND m.publisher IS NOT NULL AND m.publisher <> ''",
                scopeClause, accessibleLibraryIds, libraryId, shelfId, magicBookIds);

        List<AppFilterOptions.CountedOption> seriesOptions = queryCountedOptions(
                "m.seriesName", "JOIN b.metadata m",
                "AND m.seriesName IS NOT NULL AND m.seriesName <> ''",
                scopeClause, accessibleLibraryIds, libraryId, shelfId, magicBookIds);

        List<AppFilterOptions.CountedOption> tags = queryCountedOptions(
                "t.name", "JOIN b.metadata m JOIN m.tags t", "",
                scopeClause, accessibleLibraryIds, libraryId, shelfId, magicBookIds);

        List<AppFilterOptions.CountedOption> moods = queryCountedOptions(
                "mo.name", "JOIN b.metadata m JOIN m.moods mo", "",
                scopeClause, accessibleLibraryIds, libraryId, shelfId, magicBookIds);

        List<AppFilterOptions.CountedOption> narrators = queryCountedOptions(
                "m.narrator", "JOIN b.metadata m",
                "AND m.narrator IS NOT NULL AND m.narrator <> ''",
                scopeClause, accessibleLibraryIds, libraryId, shelfId, magicBookIds);

        List<AppFilterOptions.CountedOption> readStatuses = queryReadStatusCounts(
                userId, scopeClause, accessibleLibraryIds, libraryId, shelfId, magicBookIds);

        List<AppFilterOptions.CountedOption> ageRatings = queryGroupedCount(
                "CASE " +
                "  WHEN m.ageRating >= 0 AND m.ageRating < 6 THEN '0' " +
                "  WHEN m.ageRating >= 6 AND m.ageRating < 10 THEN '6' " +
                "  WHEN m.ageRating >= 10 AND m.ageRating < 13 THEN '10' " +
                "  WHEN m.ageRating >= 13 AND m.ageRating < 16 THEN '13' " +
                "  WHEN m.ageRating >= 16 AND m.ageRating < 18 THEN '16' " +
                "  WHEN m.ageRating >= 18 AND m.ageRating < 21 THEN '18' " +
                "  WHEN m.ageRating >= 21 THEN '21' " +
                "END",
                "JOIN b.metadata m", "AND m.ageRating IS NOT NULL",
                scopeClause, accessibleLibraryIds, libraryId, shelfId, magicBookIds);

        List<AppFilterOptions.CountedOption> contentRatings = queryCountedOptions(
                "m.contentRating", "JOIN b.metadata m",
                "AND m.contentRating IS NOT NULL AND m.contentRating <> ''",
                scopeClause, accessibleLibraryIds, libraryId, shelfId, magicBookIds);

        List<AppFilterOptions.CountedOption> matchScores = queryGroupedCount(
                "CASE " +
                "  WHEN b.metadataMatchScore >= 0.95 THEN '0' " +
                "  WHEN b.metadataMatchScore >= 0.90 THEN '1' " +
                "  WHEN b.metadataMatchScore >= 0.80 THEN '2' " +
                "  WHEN b.metadataMatchScore >= 0.70 THEN '3' " +
                "  WHEN b.metadataMatchScore >= 0.50 THEN '4' " +
                "  WHEN b.metadataMatchScore >= 0.30 THEN '5' " +
                "  WHEN b.metadataMatchScore >= 0.00 THEN '6' " +
                "END",
                "", "AND b.metadataMatchScore IS NOT NULL",
                scopeClause, accessibleLibraryIds, libraryId, shelfId, magicBookIds);

        List<AppFilterOptions.CountedOption> publishedYears = queryCountedOptions(
                "CAST(YEAR(m.publishedDate) AS string)", "JOIN b.metadata m",
                "AND m.publishedDate IS NOT NULL",
                scopeClause, accessibleLibraryIds, libraryId, shelfId, magicBookIds);

        List<AppFilterOptions.CountedOption> fileSizes = queryGroupedCount(
                "CASE " +
                "  WHEN bf.fileSizeKb < 1024 THEN '0' " +
                "  WHEN bf.fileSizeKb < 10240 THEN '1' " +
                "  WHEN bf.fileSizeKb < 51200 THEN '2' " +
                "  WHEN bf.fileSizeKb < 102400 THEN '3' " +
                "  WHEN bf.fileSizeKb < 512000 THEN '4' " +
                "  WHEN bf.fileSizeKb < 1048576 THEN '5' " +
                "  WHEN bf.fileSizeKb < 2097152 THEN '6' " +
                "  ELSE '7' " +
                "END",
                "JOIN b.bookFiles bf", "AND bf.isBookFormat = true",
                scopeClause, accessibleLibraryIds, libraryId, shelfId, magicBookIds);

        String personalRatingQuery = "SELECT CAST(ubp.personalRating AS string), COUNT(DISTINCT ubp.book.id) " +
                "FROM UserBookProgressEntity ubp " +
                "WHERE ubp.user.id = :userId AND ubp.personalRating IS NOT NULL " +
                "AND ubp.book.id IN (SELECT b.id FROM BookEntity b WHERE (b.deleted IS NULL OR b.deleted = false) " +
                "AND b.bookFiles IS NOT EMPTY " +
                scopeClause + ") " +
                "GROUP BY 1 ORDER BY 1 DESC";
        var prQ = entityManager.createQuery(personalRatingQuery, Tuple.class);
        prQ.setParameter("userId", userId);
        setFilterQueryParams(prQ, accessibleLibraryIds, libraryId, shelfId, magicBookIds);
        List<AppFilterOptions.CountedOption> personalRatings = prQ.getResultList().stream()
                .map(t -> new AppFilterOptions.CountedOption(t.get(0, String.class), t.get(1, Long.class)))
                .toList();

        List<AppFilterOptions.CountedOption> amazonRatings = queryRatingBuckets("m.amazonRating", scopeClause, accessibleLibraryIds, libraryId, shelfId, magicBookIds);
        List<AppFilterOptions.CountedOption> goodreadsRatings = queryRatingBuckets("m.goodreadsRating", scopeClause, accessibleLibraryIds, libraryId, shelfId, magicBookIds);
        List<AppFilterOptions.CountedOption> hardcoverRatings = queryRatingBuckets("m.hardcoverRating", scopeClause, accessibleLibraryIds, libraryId, shelfId, magicBookIds);
        List<AppFilterOptions.CountedOption> lubimyczytacRatings = queryRatingBuckets("m.lubimyczytacRating", scopeClause, accessibleLibraryIds, libraryId, shelfId, magicBookIds);
        List<AppFilterOptions.CountedOption> ranobedbRatings = queryRatingBuckets("m.ranobedbRating", scopeClause, accessibleLibraryIds, libraryId, shelfId, magicBookIds);
        List<AppFilterOptions.CountedOption> audibleRatings = queryRatingBuckets("m.audibleRating", scopeClause, accessibleLibraryIds, libraryId, shelfId, magicBookIds);

        List<AppFilterOptions.CountedOption> pageCounts = queryGroupedCount(
                "CASE " +
                "  WHEN m.pageCount < 50 THEN '0' " +
                "  WHEN m.pageCount < 100 THEN '1' " +
                "  WHEN m.pageCount < 200 THEN '2' " +
                "  WHEN m.pageCount < 400 THEN '3' " +
                "  WHEN m.pageCount < 600 THEN '4' " +
                "  WHEN m.pageCount < 1000 THEN '5' " +
                "  ELSE '6' " +
                "END",
                "JOIN b.metadata m", "AND m.pageCount IS NOT NULL",
                scopeClause, accessibleLibraryIds, libraryId, shelfId, magicBookIds);

        List<AppFilterOptions.CountedOption> shelfStatuses = queryGroupedCount(
                "CASE WHEN (SELECT COUNT(s) FROM b.shelves s) > 0 THEN 'shelved' ELSE 'unshelved' END",
                "", "",
                scopeClause, accessibleLibraryIds, libraryId, shelfId, magicBookIds);

        List<AppFilterOptions.CountedOption> comicCharacters = queryCountedOptions(
                "c.name", "JOIN b.metadata m JOIN m.comicMetadata cm JOIN cm.characters c", "",
                scopeClause, accessibleLibraryIds, libraryId, shelfId, magicBookIds);

        List<AppFilterOptions.CountedOption> comicTeams = queryCountedOptions(
                "t.name", "JOIN b.metadata m JOIN m.comicMetadata cm JOIN cm.teams t", "",
                scopeClause, accessibleLibraryIds, libraryId, shelfId, magicBookIds);

        List<AppFilterOptions.CountedOption> comicLocations = queryCountedOptions(
                "l.name", "JOIN b.metadata m JOIN m.comicMetadata cm JOIN cm.locations l", "",
                scopeClause, accessibleLibraryIds, libraryId, shelfId, magicBookIds);

        // Comic Creators — uses creatorMappings join table with role enum
        String creatorJpql = "SELECT cr.name, mapping.role, COUNT(DISTINCT b.id) FROM BookEntity b"
                + " JOIN b.metadata m JOIN m.comicMetadata cm JOIN cm.creatorMappings mapping JOIN mapping.creator cr"
                + " WHERE (b.deleted IS NULL OR b.deleted = false)"
                + " AND b.bookFiles IS NOT EMPTY"
                + " " + scopeClause
                + " GROUP BY cr.name, mapping.role ORDER BY COUNT(DISTINCT b.id) DESC";
        var creatorQ = entityManager.createQuery(creatorJpql, Tuple.class);
        setFilterQueryParams(creatorQ, accessibleLibraryIds, libraryId, shelfId, magicBookIds);
        creatorQ.setMaxResults(1000);
        List<AppFilterOptions.CountedOption> allCreators = creatorQ.getResultList().stream()
                .map(t -> {
                    String name = t.get(0, String.class);
                    ComicCreatorRole role = t.get(1, ComicCreatorRole.class);
                    long count = t.get(2, Long.class);
                    return new AppFilterOptions.CountedOption(name + ":" + creatorRoleLabel(role), count);
                })
                .toList();

        List<AppFilterOptions.CountedOption> shelves = queryCountedOptions(
                "CAST(s.id AS string) || ':' || s.name", "JOIN b.shelves s", "",
                scopeClause, accessibleLibraryIds, libraryId, shelfId, magicBookIds);

        List<AppFilterOptions.CountedOption> libraries = queryCountedOptions(
                "CAST(l.id AS string) || ':' || l.name", "JOIN b.library l", "",
                scopeClause, accessibleLibraryIds, libraryId, shelfId, magicBookIds);

        AppFilterOptions result = AppFilterOptions.builder()
                .authors(authors)
                .languages(languages)
                .fileTypes(fileTypes)
                .readStatuses(readStatuses)
                .categories(categories)
                .publishers(publishers)
                .series(seriesOptions)
                .tags(tags)
                .moods(moods)
                .narrators(narrators)
                .ageRatings(ageRatings)
                .contentRatings(contentRatings)
                .matchScores(matchScores)
                .publishedYears(publishedYears)
                .fileSizes(fileSizes)
                .personalRatings(personalRatings)
                .amazonRatings(amazonRatings)
                .goodreadsRatings(goodreadsRatings)
                .hardcoverRatings(hardcoverRatings)
                .lubimyczytacRatings(lubimyczytacRatings)
                .ranobedbRatings(ranobedbRatings)
                .audibleRatings(audibleRatings)
                .pageCounts(pageCounts)
                .shelfStatuses(shelfStatuses)
                .comicCharacters(comicCharacters)
                .comicTeams(comicTeams)
                .comicLocations(comicLocations)
                .comicCreators(allCreators)
                .shelves(shelves)
                .libraries(libraries)
                .build();
        filterOptionsCache.put(cacheKey, result);
        return result;
    }

    private AppFilterOptions emptyFilterOptions(String cacheKey) {
        AppFilterOptions empty = AppFilterOptions.builder()
                .authors(Collections.emptyList())
                .languages(Collections.emptyList())
                .fileTypes(Collections.emptyList())
                .readStatuses(Collections.emptyList())
                .categories(Collections.emptyList())
                .publishers(Collections.emptyList())
                .series(Collections.emptyList())
                .tags(Collections.emptyList())
                .moods(Collections.emptyList())
                .narrators(Collections.emptyList())
                .ageRatings(Collections.emptyList())
                .contentRatings(Collections.emptyList())
                .matchScores(Collections.emptyList())
                .publishedYears(Collections.emptyList())
                .fileSizes(Collections.emptyList())
                .personalRatings(Collections.emptyList())
                .amazonRatings(Collections.emptyList())
                .goodreadsRatings(Collections.emptyList())
                .hardcoverRatings(Collections.emptyList())
                .lubimyczytacRatings(Collections.emptyList())
                .ranobedbRatings(Collections.emptyList())
                .audibleRatings(Collections.emptyList())
                .pageCounts(Collections.emptyList())
                .shelfStatuses(Collections.emptyList())
                .comicCharacters(Collections.emptyList())
                .comicTeams(Collections.emptyList())
                .comicLocations(Collections.emptyList())
                .comicCreators(Collections.emptyList())
                .shelves(Collections.emptyList())
                .libraries(Collections.emptyList())
                .build();
        filterOptionsCache.put(cacheKey, empty);
        return empty;
    }

    @Transactional
    public void updateReadStatus(Long bookId, ReadStatus status) {
        UserBookProgressEntity progress = validateAccessAndGetProgress(bookId);

        progress.setReadStatus(status);
        progress.setReadStatusModifiedTime(Instant.now());

        if (status == ReadStatus.READ && progress.getDateFinished() == null) {
            progress.setDateFinished(Instant.now());
        }

        userBookProgressRepository.save(progress);
    }

    @Transactional
    public void updatePersonalRating(Long bookId, Integer rating) {
        UserBookProgressEntity progress = validateAccessAndGetProgress(bookId);

        progress.setPersonalRating(rating);
        userBookProgressRepository.save(progress);
    }

    private UserBookProgressEntity validateAccessAndGetProgress(Long bookId) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();
        Set<Long> accessibleLibraryIds = getAccessibleLibraryIds(user);

        BookEntity book = bookRepository.findByIdWithBookFiles(bookId)
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        validateLibraryAccess(accessibleLibraryIds, book.getLibrary().getId());

        return userBookProgressRepository
                .findByUserIdAndBookId(userId, bookId)
                .orElseGet(() -> createNewProgress(userId, book));
    }

    private void validateLibraryAccess(Set<Long> accessibleLibraryIds, Long libraryId) {
        if (accessibleLibraryIds != null && !accessibleLibraryIds.contains(libraryId)) {
            throw ApiError.FORBIDDEN.createException("Access denied to this book");
        }
    }

    private UserBookProgressEntity createNewProgress(Long userId, BookEntity book) {
        return UserBookProgressEntity.builder()
                .user(BookLoreUserEntity.builder().id(userId).build())
                .book(book)
                .build();
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

    private Specification<BookEntity> buildSpecification(
            Set<Long> accessibleLibraryIds,
            Long userId,
            BookListRequest req) {

        List<Specification<BookEntity>> specs = new ArrayList<>();
        specs.add(AppBookSpecification.notDeleted());
        specs.add(AppBookSpecification.hasDigitalFile());

        if (accessibleLibraryIds != null) {
            if (req.libraryId() != null && accessibleLibraryIds.contains(req.libraryId())) {
                specs.add(AppBookSpecification.inLibrary(req.libraryId()));
            } else if (req.libraryId() != null) {
                throw ApiError.FORBIDDEN.createException("Access denied to library " + req.libraryId());
            } else {
                specs.add(AppBookSpecification.inLibraries(accessibleLibraryIds));
            }
        } else if (req.libraryId() != null) {
            specs.add(AppBookSpecification.inLibrary(req.libraryId()));
        }

        if (req.shelfId() != null) {
            ShelfEntity shelf = shelfRepository.findById(req.shelfId())
                    .orElseThrow(() -> ApiError.SHELF_NOT_FOUND.createException(req.shelfId()));
            if (!shelf.isPublic() && !shelf.getUser().getId().equals(userId)) {
                throw ApiError.FORBIDDEN.createException("Access denied to shelf " + req.shelfId());
            }
            specs.add(AppBookSpecification.inShelf(req.shelfId()));
        }

        if (req.status() != null && !req.status().isEmpty()) {
            List<String> cleaned = BookListRequest.cleanValues(req.status());
            if (!cleaned.isEmpty()) {
                specs.add(AppBookSpecification.withReadStatuses(cleaned, userId, req.effectiveFilterMode()));
            }
        }

        if (req.search() != null && !req.search().trim().isEmpty()) {
            specs.add(AppBookSpecification.searchText(req.search()));
        }

        if (req.fileType() != null && !req.fileType().isEmpty()) {
            List<String> cleaned = BookListRequest.cleanValues(req.fileType());
            if (!cleaned.isEmpty()) {
                specs.add(AppBookSpecification.withFileTypes(cleaned, req.effectiveFilterMode()));
            }
        }

        if (req.minRating() != null) {
            specs.add(AppBookSpecification.withMinRating(req.minRating(), userId));
        }

        if (req.maxRating() != null) {
            specs.add(AppBookSpecification.withMaxRating(req.maxRating(), userId));
        }

        if (req.authors() != null && !req.authors().isEmpty()) {
            List<String> cleaned = BookListRequest.cleanValues(req.authors());
            if (!cleaned.isEmpty()) {
                specs.add(AppBookSpecification.withAuthors(cleaned, req.effectiveFilterMode()));
            }
        }

        if (req.language() != null && !req.language().isEmpty()) {
            List<String> cleaned = BookListRequest.cleanValues(req.language());
            if (!cleaned.isEmpty()) {
                specs.add(AppBookSpecification.withLanguages(cleaned, req.effectiveFilterMode()));
            }
        }

        if (req.series() != null && !req.series().isEmpty()) {
            List<String> cleaned = BookListRequest.cleanValues(req.series());
            if (!cleaned.isEmpty()) {
                specs.add(AppBookSpecification.inSeriesMulti(cleaned, req.effectiveFilterMode()));
            }
        }

        if (req.category() != null && !req.category().isEmpty()) {
            List<String> cleaned = BookListRequest.cleanValues(req.category());
            if (!cleaned.isEmpty()) {
                specs.add(AppBookSpecification.withCategories(cleaned, req.effectiveFilterMode()));
            }
        }

        if (req.publisher() != null && !req.publisher().isEmpty()) {
            List<String> cleaned = BookListRequest.cleanValues(req.publisher());
            if (!cleaned.isEmpty()) {
                specs.add(AppBookSpecification.withPublishers(cleaned, req.effectiveFilterMode()));
            }
        }

        if (req.tag() != null && !req.tag().isEmpty()) {
            List<String> cleaned = BookListRequest.cleanValues(req.tag());
            if (!cleaned.isEmpty()) {
                specs.add(AppBookSpecification.withTags(cleaned, req.effectiveFilterMode()));
            }
        }

        if (req.mood() != null && !req.mood().isEmpty()) {
            List<String> cleaned = BookListRequest.cleanValues(req.mood());
            if (!cleaned.isEmpty()) {
                specs.add(AppBookSpecification.withMoods(cleaned, req.effectiveFilterMode()));
            }
        }

        if (req.narrator() != null && !req.narrator().isEmpty()) {
            List<String> cleaned = BookListRequest.cleanValues(req.narrator());
            if (!cleaned.isEmpty()) {
                specs.add(AppBookSpecification.withNarrators(cleaned, req.effectiveFilterMode()));
            }
        }

        if (req.ageRating() != null && !req.ageRating().isEmpty()) {
            List<String> cleaned = BookListRequest.cleanValues(req.ageRating());
            if (!cleaned.isEmpty()) {
                specs.add(AppBookSpecification.withAgeRatings(cleaned, req.effectiveFilterMode()));
            }
        }

        if (req.contentRating() != null && !req.contentRating().isEmpty()) {
            List<String> cleaned = BookListRequest.cleanValues(req.contentRating());
            if (!cleaned.isEmpty()) {
                specs.add(AppBookSpecification.withContentRatings(cleaned, req.effectiveFilterMode()));
            }
        }

        if (req.matchScore() != null && !req.matchScore().isEmpty()) {
            List<String> cleaned = BookListRequest.cleanValues(req.matchScore());
            if (!cleaned.isEmpty()) {
                specs.add(AppBookSpecification.withMatchScores(cleaned, req.effectiveFilterMode()));
            }
        }

        if (req.publishedDate() != null && !req.publishedDate().isEmpty()) {
            List<String> cleaned = BookListRequest.cleanValues(req.publishedDate());
            if (!cleaned.isEmpty()) {
                specs.add(AppBookSpecification.withPublishedYears(cleaned, req.effectiveFilterMode()));
            }
        }

        if (req.fileSize() != null && !req.fileSize().isEmpty()) {
            List<String> cleaned = BookListRequest.cleanValues(req.fileSize());
            if (!cleaned.isEmpty()) {
                specs.add(AppBookSpecification.withFileSizes(cleaned, req.effectiveFilterMode()));
            }
        }

        if (req.personalRating() != null && !req.personalRating().isEmpty()) {
            List<String> cleaned = BookListRequest.cleanValues(req.personalRating());
            if (!cleaned.isEmpty()) {
                specs.add(AppBookSpecification.withPersonalRatings(cleaned, userId, req.effectiveFilterMode()));
            }
        }

        if (req.amazonRating() != null && !req.amazonRating().isEmpty()) {
            List<String> cleaned = BookListRequest.cleanValues(req.amazonRating());
            if (!cleaned.isEmpty()) {
                specs.add(AppBookSpecification.withAmazonRatings(cleaned, req.effectiveFilterMode()));
            }
        }

        if (req.goodreadsRating() != null && !req.goodreadsRating().isEmpty()) {
            List<String> cleaned = BookListRequest.cleanValues(req.goodreadsRating());
            if (!cleaned.isEmpty()) {
                specs.add(AppBookSpecification.withGoodreadsRatings(cleaned, req.effectiveFilterMode()));
            }
        }

        if (req.hardcoverRating() != null && !req.hardcoverRating().isEmpty()) {
            List<String> cleaned = BookListRequest.cleanValues(req.hardcoverRating());
            if (!cleaned.isEmpty()) {
                specs.add(AppBookSpecification.withHardcoverRatings(cleaned, req.effectiveFilterMode()));
            }
        }

        if (req.lubimyczytacRating() != null && !req.lubimyczytacRating().isEmpty()) {
            List<String> cleaned = BookListRequest.cleanValues(req.lubimyczytacRating());
            if (!cleaned.isEmpty()) {
                specs.add(AppBookSpecification.withLubimyczytacRatings(cleaned, req.effectiveFilterMode()));
            }
        }

        if (req.ranobedbRating() != null && !req.ranobedbRating().isEmpty()) {
            List<String> cleaned = BookListRequest.cleanValues(req.ranobedbRating());
            if (!cleaned.isEmpty()) {
                specs.add(AppBookSpecification.withRanobedbRatings(cleaned, req.effectiveFilterMode()));
            }
        }

        if (req.audibleRating() != null && !req.audibleRating().isEmpty()) {
            List<String> cleaned = BookListRequest.cleanValues(req.audibleRating());
            if (!cleaned.isEmpty()) {
                specs.add(AppBookSpecification.withAudibleRatings(cleaned, req.effectiveFilterMode()));
            }
        }

        if (req.pageCount() != null && !req.pageCount().isEmpty()) {
            List<String> cleaned = BookListRequest.cleanValues(req.pageCount());
            if (!cleaned.isEmpty()) {
                specs.add(AppBookSpecification.withPageCounts(cleaned, req.effectiveFilterMode()));
            }
        }

        if (req.shelfStatus() != null && !req.shelfStatus().isEmpty()) {
            List<String> cleaned = BookListRequest.cleanValues(req.shelfStatus());
            if (!cleaned.isEmpty()) {
                specs.add(AppBookSpecification.withShelfStatus(cleaned, req.effectiveFilterMode()));
            }
        }

        if (req.comicCharacter() != null && !req.comicCharacter().isEmpty()) {
            List<String> cleaned = BookListRequest.cleanValues(req.comicCharacter());
            if (!cleaned.isEmpty()) {
                specs.add(AppBookSpecification.withComicCharacters(cleaned, req.effectiveFilterMode()));
            }
        }

        if (req.comicTeam() != null && !req.comicTeam().isEmpty()) {
            List<String> cleaned = BookListRequest.cleanValues(req.comicTeam());
            if (!cleaned.isEmpty()) {
                specs.add(AppBookSpecification.withComicTeams(cleaned, req.effectiveFilterMode()));
            }
        }

        if (req.comicLocation() != null && !req.comicLocation().isEmpty()) {
            List<String> cleaned = BookListRequest.cleanValues(req.comicLocation());
            if (!cleaned.isEmpty()) {
                specs.add(AppBookSpecification.withComicLocations(cleaned, req.effectiveFilterMode()));
            }
        }

        if (req.comicCreator() != null && !req.comicCreator().isEmpty()) {
            List<String> cleaned = BookListRequest.cleanValues(req.comicCreator());
            if (!cleaned.isEmpty()) {
                specs.add(AppBookSpecification.withComicCreators(cleaned, req.effectiveFilterMode()));
            }
        }

        if (req.shelves() != null && !req.shelves().isEmpty()) {
            List<String> cleaned = BookListRequest.cleanValues(req.shelves());
            if (!cleaned.isEmpty()) {
                specs.add(AppBookSpecification.inShelves(cleaned, req.effectiveFilterMode()));
            }
        }

        if (req.libraries() != null && !req.libraries().isEmpty()) {
            List<String> cleaned = BookListRequest.cleanValues(req.libraries());
            if (!cleaned.isEmpty()) {
                specs.add(AppBookSpecification.inLibraries(cleaned, req.effectiveFilterMode()));
            }
        }

        // Any of the user book progress sorting we need to filter to only the current user's progress.
        String field = getSortField(req.sort());
        if (field.startsWith("userBookProgress.")) {
            specs.add(AppBookSpecification.withProgress(userId, true));
        }

        return AppBookSpecification.combine(specs.toArray(new Specification[0]));
    }

    private String getSortField(String sortBy) {
        return switch (sortBy != null ? sortBy.toLowerCase() : DEFAULT_SORT) {
            case "addedon" -> "addedOn";
            case "title" -> "metadata.title";
            case "seriesname", "series" -> "metadata.seriesName";
            case "seriesnumber" -> "metadata.seriesNumber";
            case "pagecount" -> "metadata.pageCount";
            case "language" -> "metadata.language";
            case "publisher" -> "metadata.publisher";
            case "publisheddate" -> "metadata.publishedDate";
            case "amazonrating" -> "metadata.amazonRating";
            case "amazonreviewcount" -> "metadata.amazonReviewCount";
            case "goodreadsrating" -> "metadata.goodreadsRating";
            case "goodreadsreviewcount" -> "metadata.goodreadsReviewCount";
            case "hardcoverrating" -> "metadata.hardcoverRating";
            case "hardcoverreviewcount" -> "metadata.hardcoverReviewCount";
            case "ranobedbrating" -> "metadata.ranobedbRating";
            case "narrator" -> "metadata.narrator";
            case "lastreadtime" -> "userBookProgress.lastReadTime";
            case "readstatus" -> "userBookProgress.readStatus";
            case "datefinished" -> "userBookProgress.dateFinished";
            case "personalrating" -> "userBookProgress.personalRating";
            default -> throw ApiError.INVALID_INPUT.createException("Invalid sort");
        };
    }

    private Sort buildSort(String sortBy, String sortDir) {
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        String field = getSortField(sortBy);

        return Sort.by(direction, field);
    }

    private int validatePageNumber(Integer page) {
        return page != null && page >= 0 ? page : 0;
    }

    private int validatePageSize(Integer size) {
        return size != null && size > 0 ? Math.min(size, MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;
    }

    private int validateLimit(Integer limit, int defaultValue) {
        return limit != null && limit > 0 ? Math.min(limit, MAX_PAGE_SIZE) : defaultValue;
    }

    private Specification<BookEntity> buildBaseSpecification(Set<Long> accessibleLibraryIds, Long libraryId) {
        List<Specification<BookEntity>> specs = new ArrayList<>();
        specs.add(AppBookSpecification.notDeleted());
        specs.add(AppBookSpecification.hasDigitalFile());

        if (accessibleLibraryIds != null) {
            if (libraryId != null && !accessibleLibraryIds.contains(libraryId)) {
                throw ApiError.FORBIDDEN.createException("Access denied to library " + libraryId);
            }
            specs.add(libraryId != null
                    ? AppBookSpecification.inLibrary(libraryId)
                    : AppBookSpecification.inLibraries(accessibleLibraryIds));
        } else if (libraryId != null) {
            specs.add(AppBookSpecification.inLibrary(libraryId));
        }

        return AppBookSpecification.combine(specs.toArray(new Specification[0]));
    }

    private AppPageResponse<AppBookSummary> buildPageResponse(
            Page<BookEntity> bookPage,
            Long userId,
            int pageNum,
            int pageSize) {

        List<BookEntity> books = bookPage.getContent();
        Map<Long, UserBookProgressEntity> progressMap = getProgressMapForBooks(userId, books);

        List<AppBookSummary> summaries = books.stream()
                .map(book -> mobileBookMapper.toSummary(book, progressMap.get(book.getId())))
                .collect(Collectors.toList());

        return AppPageResponse.of(summaries, pageNum, pageSize, bookPage.getTotalElements());
    }


    private Map<Long, UserBookProgressEntity> getProgressMapForBooks(Long userId, List<BookEntity> books) {
        if (books.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> bookIds = books.stream()
                .map(BookEntity::getId)
                .collect(Collectors.toSet());
        return getProgressMap(userId, bookIds);
    }

    private List<AppFilterOptions.CountedOption> queryCountedOptions(
            String selectExpr, String joins, String extraWhere,
            String scopeClause, Set<Long> accessibleLibraryIds,
            Long libraryId, Long shelfId, Set<Long> magicBookIds) {
        String jpql = "SELECT " + selectExpr + ", COUNT(DISTINCT b.id) FROM BookEntity b"
                + " " + joins
                + " WHERE (b.deleted IS NULL OR b.deleted = false)"
                + " AND b.bookFiles IS NOT EMPTY"
                + (extraWhere.isEmpty() ? "" : " " + extraWhere)
                + " " + scopeClause
                + " GROUP BY " + selectExpr + " ORDER BY COUNT(DISTINCT b.id) DESC";
        var q = entityManager.createQuery(jpql, Tuple.class);
        setFilterQueryParams(q, accessibleLibraryIds, libraryId, shelfId, magicBookIds);
        q.setMaxResults(1000);
        return q.getResultList().stream()
                .map(this::mapToCountedOption)
                .toList();
    }

    private String buildScopeClause(String libraryClause, String shelfClause, String magicBookClause) {
        var sb = new StringBuilder();
        if (!libraryClause.isEmpty()) sb.append(" ").append(libraryClause);
        if (!shelfClause.isEmpty()) sb.append(" ").append(shelfClause);
        if (!magicBookClause.isEmpty()) sb.append(" ").append(magicBookClause);
        return sb.toString();
    }

    private void setFilterQueryParams(jakarta.persistence.Query query, Set<Long> accessibleLibraryIds, Long libraryId, Long shelfId, Set<Long> magicBookIds) {
        if (libraryId != null) {
            query.setParameter("libraryId", libraryId);
        } else if (accessibleLibraryIds != null) {
            query.setParameter("libraryIds", accessibleLibraryIds);
        }
        if (shelfId != null && magicBookIds == null) {
            query.setParameter("shelfId", shelfId);
        }
        if (magicBookIds != null) {
            query.setParameter("magicBookIds", magicBookIds);
        }
    }

    private Set<Long> resolveMagicShelfBookIds(Specification<BookEntity> spec) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<BookEntity> root = cq.from(BookEntity.class);
        cq.select(root.get("id"));
        cq.where(spec.toPredicate(root, cq, cb));
        return new HashSet<>(entityManager.createQuery(cq)
                .getResultList());
    }

    private List<AppFilterOptions.CountedOption> queryReadStatusCounts(
            Long userId, String scopeClause, Set<Long> accessibleLibraryIds,
            Long libraryId, Long shelfId, Set<Long> magicBookIds) {
        String jpql = "SELECT ubp.readStatus, COUNT(DISTINCT ubp.book.id) FROM UserBookProgressEntity ubp"
                + " WHERE ubp.user.id = :userId"
                + " AND ubp.readStatus <> org.booklore.model.enums.ReadStatus.UNSET"
                + " AND ubp.book.id IN ("
                + "   SELECT b.id FROM BookEntity b"
                + "   WHERE (b.deleted IS NULL OR b.deleted = false)"
                + "   AND b.bookFiles IS NOT EMPTY"
                + " " + scopeClause
                + " )"
                + " GROUP BY ubp.readStatus ORDER BY COUNT(DISTINCT ubp.book.id) DESC";
        var q = entityManager.createQuery(jpql, Tuple.class);
        q.setParameter("userId", userId);
        setFilterQueryParams(q, accessibleLibraryIds, libraryId, shelfId, magicBookIds);
        List<AppFilterOptions.CountedOption> options = q.getResultList().stream()
                .map(t -> new AppFilterOptions.CountedOption(
                        t.get(0, ReadStatus.class).name(),
                        t.get(1, Long.class)))
                .toList();

        String baseQuery = "SELECT COUNT(DISTINCT b.id) FROM BookEntity b"
                + " WHERE (b.deleted IS NULL OR b.deleted = false)"
                + " AND b.bookFiles IS NOT EMPTY"
                + " AND b.id NOT IN ("
                + "   SELECT ubp.book.id FROM UserBookProgressEntity ubp WHERE ubp.user.id = :userId"
                + " )"
                + " " + scopeClause;
        var baseQ = entityManager.createQuery(baseQuery, Long.class);
        baseQ.setParameter("userId", userId);
        setFilterQueryParams(baseQ, accessibleLibraryIds, libraryId, shelfId, magicBookIds);
        long unsetCount = baseQ.getSingleResult();

        if (unsetCount > 0) {
            List<AppFilterOptions.CountedOption> mutable = new ArrayList<>(options);
            mutable.addFirst(new AppFilterOptions.CountedOption("UNSET", unsetCount));
            return List.copyOf(mutable);
        }
        return options;
    }

    private List<AppFilterOptions.CountedOption> queryRatingBuckets(
            String ratingExpr, String scopeClause, Set<Long> accessibleLibraryIds,
            Long libraryId, Long shelfId, Set<Long> magicBookIds) {
        return queryGroupedCount(
                "CASE " +
                "  WHEN " + ratingExpr + " >= 4.5 THEN '5' " +
                "  WHEN " + ratingExpr + " >= 4.0 THEN '4' " +
                "  WHEN " + ratingExpr + " >= 3.0 THEN '3' " +
                "  WHEN " + ratingExpr + " >= 2.0 THEN '2' " +
                "  WHEN " + ratingExpr + " >= 1.0 THEN '1' " +
                "  ELSE '0' " +
                "END",
                "JOIN b.metadata m", "AND " + ratingExpr + " IS NOT NULL",
                scopeClause, accessibleLibraryIds, libraryId, shelfId, magicBookIds);
    }

    private List<AppFilterOptions.CountedOption> queryGroupedCount(
            String caseExpr, String joins, String extraWhere,
            String scopeClause, Set<Long> accessibleLibraryIds,
            Long libraryId, Long shelfId, Set<Long> magicBookIds) {
        String jpql = "SELECT " + caseExpr + ", COUNT(DISTINCT b.id) FROM BookEntity b " + joins +
                " WHERE (b.deleted IS NULL OR b.deleted = false) AND b.bookFiles IS NOT EMPTY "
                + extraWhere + " " + scopeClause +
                " GROUP BY 1";
        var q = entityManager.createQuery(jpql, Tuple.class);
        setFilterQueryParams(q, accessibleLibraryIds, libraryId, shelfId, magicBookIds);
        return q.getResultList().stream()
                .filter(t -> t.get(0) != null)
                .map(this::mapToCountedOption)
                .toList();
    }

    private AppFilterOptions.CountedOption mapToCountedOption(Tuple t) {
        Object val = t.get(0);
        String name = val instanceof Enum<?> e ? e.name() : val != null ? String.valueOf(val) : null;
        return new AppFilterOptions.CountedOption(name, t.get(1, Long.class));
    }

    private static String creatorRoleLabel(ComicCreatorRole role) {
        return switch (role) {
            case PENCILLER -> "penciller";
            case INKER -> "inker";
            case COLORIST -> "colorist";
            case LETTERER -> "letterer";
            case COVER_ARTIST -> "coverArtist";
            case EDITOR -> "editor";
        };
    }
}
