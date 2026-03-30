package org.booklore.app.service;

import lombok.AllArgsConstructor;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.ApiError;
import org.booklore.app.dto.AppBookDetail;
import org.booklore.app.dto.AppBookSummary;
import org.booklore.app.dto.AppFilterOptions;
import org.booklore.app.dto.AppPageResponse;
import org.booklore.app.mapper.AppBookMapper;
import org.booklore.app.specification.AppBookSpecification;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.Library;
import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.ReadStatus;
import org.booklore.repository.BookRepository;
import org.booklore.repository.ShelfRepository;
import org.booklore.repository.UserBookFileProgressRepository;
import org.booklore.repository.UserBookProgressRepository;
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

@Service
@AllArgsConstructor
public class AppBookService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final BookRepository bookRepository;
    private final UserBookProgressRepository userBookProgressRepository;
    private final UserBookFileProgressRepository userBookFileProgressRepository;
    private final ShelfRepository shelfRepository;
    private final AuthenticationService authenticationService;
    private final AppBookMapper mobileBookMapper;
    private final MagicShelfBookService magicShelfBookService;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public AppPageResponse<AppBookSummary> getBooks(
            Integer page,
            Integer size,
            String sortBy,
            String sortDir,
            Long libraryId,
            Long shelfId,
            ReadStatus status,
            String search,
            BookFileType fileType,
            Integer minRating,
            Integer maxRating,
            String authors,
            String language) {

        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();
        Set<Long> accessibleLibraryIds = getAccessibleLibraryIds(user);

        int pageNum = page != null && page >= 0 ? page : 0;
        int pageSize = size != null && size > 0 ? Math.min(size, MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;

        Sort sort = buildSort(sortBy, sortDir);
        Pageable pageable = PageRequest.of(pageNum, pageSize, sort);

        Specification<BookEntity> spec = buildSpecification(
                accessibleLibraryIds, libraryId, shelfId, status, search, userId,
                fileType, minRating, maxRating, authors, language);

        Page<BookEntity> bookPage = bookRepository.findAll(spec, pageable);
        return buildPageResponse(bookPage, userId, pageNum, pageSize);
    }

    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
    public List<AppBookSummary> getContinueReading(Integer limit) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();
        Set<Long> accessibleLibraryIds = getAccessibleLibraryIds(user);

        int maxItems = validateLimit(limit, 10);

        Specification<BookEntity> spec = AppBookSpecification.combine(
                AppBookSpecification.notDeleted(),
                AppBookSpecification.hasDigitalFile(),
                AppBookSpecification.inLibraries(accessibleLibraryIds),
                AppBookSpecification.inProgress(userId),
                AppBookSpecification.hasNonAudiobookFile()
        );

        List<BookEntity> books = bookRepository.findAll(spec);
        Map<Long, UserBookProgressEntity> progressMap = getProgressMapForBooks(userId, books);

        List<Long> topIds = books.stream()
                .filter(b -> progressMap.containsKey(b.getId()))
                .sorted((b1, b2) -> {
                    Instant t1 = progressMap.get(b1.getId()).getLastReadTime();
                    Instant t2 = progressMap.get(b2.getId()).getLastReadTime();
                    if (t1 == null && t2 == null) return 0;
                    if (t1 == null) return 1;
                    if (t2 == null) return -1;
                    return t2.compareTo(t1);
                })
                .limit(maxItems)
                .map(BookEntity::getId)
                .collect(Collectors.toList());

        if (topIds.isEmpty()) return Collections.emptyList();

        Map<Long, BookEntity> enrichedMap = bookRepository.findAllForSummaryByIds(topIds)
                .stream().collect(Collectors.toMap(BookEntity::getId, b -> b));

        return topIds.stream()
                .filter(enrichedMap::containsKey)
                .map(id -> mobileBookMapper.toSummary(enrichedMap.get(id), progressMap.get(id)))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AppBookSummary> getContinueListening(Integer limit) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();
        Set<Long> accessibleLibraryIds = getAccessibleLibraryIds(user);

        int maxItems = validateLimit(limit, 10);

        Specification<BookEntity> spec = AppBookSpecification.combine(
                AppBookSpecification.notDeleted(),
                AppBookSpecification.hasDigitalFile(),
                AppBookSpecification.inLibraries(accessibleLibraryIds),
                AppBookSpecification.inProgress(userId),
                AppBookSpecification.hasAudiobookFile()
        );

        List<BookEntity> books = bookRepository.findAll(spec);
        Map<Long, UserBookProgressEntity> progressMap = getProgressMapForBooks(userId, books);

        List<Long> topIds = books.stream()
                .filter(b -> progressMap.containsKey(b.getId()))
                .sorted((b1, b2) -> {
                    Instant t1 = progressMap.get(b1.getId()).getLastReadTime();
                    Instant t2 = progressMap.get(b2.getId()).getLastReadTime();
                    if (t1 == null && t2 == null) return 0;
                    if (t1 == null) return 1;
                    if (t2 == null) return -1;
                    return t2.compareTo(t1);
                })
                .limit(maxItems)
                .map(BookEntity::getId)
                .collect(Collectors.toList());

        if (topIds.isEmpty()) return Collections.emptyList();

        Map<Long, BookEntity> enrichedMap = bookRepository.findAllForSummaryByIds(topIds)
                .stream().collect(Collectors.toMap(BookEntity::getId, b -> b));

        return topIds.stream()
                .filter(enrichedMap::containsKey)
                .map(id -> mobileBookMapper.toSummary(enrichedMap.get(id), progressMap.get(id)))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
    public AppPageResponse<AppBookSummary> getBooksByMagicShelf(
            Long magicShelfId,
            Integer page,
            Integer size) {

        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();

        int pageNum = validatePageNumber(page);
        int pageSize = validatePageSize(size);

        var booksPage = magicShelfBookService.getBooksByMagicShelfId(userId, magicShelfId, pageNum, pageSize);

        Set<Long> bookIds = booksPage.getContent().stream()
                .map(Book::getId)
                .collect(Collectors.toSet());

        if (bookIds.isEmpty()) {
            return AppPageResponse.of(Collections.emptyList(), pageNum, pageSize, 0L);
        }

        List<BookEntity> bookEntities = bookRepository.findAllForSummaryByIds(bookIds);
        Map<Long, UserBookProgressEntity> progressMap = getProgressMapForBooks(userId, bookEntities);

        List<AppBookSummary> summaries = bookEntities.stream()
                .filter(BookEntity::hasFiles)
                .map(bookEntity -> mobileBookMapper.toSummary(bookEntity, progressMap.get(bookEntity.getId())))
                .collect(Collectors.toList());

        return AppPageResponse.of(summaries, pageNum, pageSize, booksPage.getTotalElements());
    }

    @Transactional(readOnly = true)
    public AppFilterOptions getFilterOptions(Long libraryId, Long shelfId, Long magicShelfId) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();
        Set<Long> accessibleLibraryIds = getAccessibleLibraryIds(user);

        // Resolve magic shelf to a set of book IDs if requested
        Set<Long> magicBookIds = null;
        if (magicShelfId != null) {
            magicBookIds = resolveMagicShelfBookIds(magicShelfId, userId);
            if (magicBookIds.isEmpty()) {
                return AppFilterOptions.builder()
                        .authors(Collections.emptyList())
                        .languages(Collections.emptyList())
                        .fileTypes(Collections.emptyList())
                        .readStatuses(getReadStatusOptions())
                        .build();
            }
        }

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

        // Build scoping clauses
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

        // Build the optional WHERE suffix once — each clause already starts with "AND"
        String scopeClause = buildScopeClause(libraryClause, shelfClause, magicBookClause);

        // Authors with book count (top 200 by count)
        String authorQuery = "SELECT a.name, COUNT(DISTINCT b.id) FROM BookEntity b"
                + " JOIN b.metadata m JOIN m.authors a"
                + " WHERE (b.deleted IS NULL OR b.deleted = false)"
                + " AND b.bookFiles IS NOT EMPTY"
                + scopeClause
                + " GROUP BY a.name ORDER BY COUNT(DISTINCT b.id) DESC";
        var authorQ = entityManager.createQuery(authorQuery, Tuple.class);
        setFilterQueryParams(authorQ, accessibleLibraryIds, libraryId, shelfId, magicBookIds);
        authorQ.setMaxResults(200);

        List<AppFilterOptions.AuthorOption> authors = authorQ.getResultList().stream()
                .map(t -> AppFilterOptions.AuthorOption.builder()
                        .name(t.get(0, String.class))
                        .count(t.get(1, Long.class))
                        .build())
                .toList();

        // Languages with book count
        String langQuery = "SELECT m.language, COUNT(DISTINCT b.id) FROM BookEntity b"
                + " JOIN b.metadata m"
                + " WHERE (b.deleted IS NULL OR b.deleted = false)"
                + " AND b.bookFiles IS NOT EMPTY"
                + " AND m.language IS NOT NULL AND m.language <> ''"
                + scopeClause
                + " GROUP BY m.language ORDER BY COUNT(DISTINCT b.id) DESC";
        var langQ = entityManager.createQuery(langQuery, Tuple.class);
        setFilterQueryParams(langQ, accessibleLibraryIds, libraryId, shelfId, magicBookIds);

        List<AppFilterOptions.LanguageOption> languages = langQ.getResultList().stream()
                .map(t -> AppFilterOptions.LanguageOption.builder()
                        .code(t.get(0, String.class))
                        .label(Locale.forLanguageTag(t.get(0, String.class)).getDisplayLanguage(Locale.ENGLISH))
                        .count(t.get(1, Long.class))
                        .build())
                .toList();

        // Distinct file types present in scoped books
        String fileTypeQuery = "SELECT DISTINCT bf.bookType FROM BookEntity b"
                + " JOIN b.bookFiles bf"
                + " WHERE (b.deleted IS NULL OR b.deleted = false)"
                + " AND b.bookFiles IS NOT EMPTY"
                + " AND bf.isBookFormat = true"
                + scopeClause;
        var ftQ = entityManager.createQuery(fileTypeQuery, BookFileType.class);
        setFilterQueryParams(ftQ, accessibleLibraryIds, libraryId, shelfId, magicBookIds);

        List<String> fileTypes = ftQ.getResultList().stream()
                .map(Enum::name)
                .sorted()
                .toList();

        // Read statuses — return all meaningful values
        List<String> readStatuses = getReadStatusOptions();

        return AppFilterOptions.builder()
                .authors(authors)
                .languages(languages)
                .fileTypes(fileTypes)
                .readStatuses(readStatuses)
                .build();
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

    private Set<Long> resolveMagicShelfBookIds(Long magicShelfId, Long userId) {
        // Reuse MagicShelfBookService which already handles access validation,
        // rule evaluation, and library filtering.
        var booksPage = magicShelfBookService.getBooksByMagicShelfId(userId, magicShelfId, 0, 10000);
        return booksPage.getContent().stream()
                .map(Book::getId)
                .collect(Collectors.toSet());
    }

    private List<String> getReadStatusOptions() {
        return Arrays.stream(ReadStatus.values())
                .filter(s -> s != ReadStatus.UNSET)
                .map(Enum::name)
                .toList();
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
            Long libraryId,
            Long shelfId,
            ReadStatus status,
            String search,
            Long userId,
            BookFileType fileType,
            Integer minRating,
            Integer maxRating,
            String authors,
            String language) {

        List<Specification<BookEntity>> specs = new ArrayList<>();
        specs.add(AppBookSpecification.notDeleted());
        specs.add(AppBookSpecification.hasDigitalFile());

        if (accessibleLibraryIds != null) {
            if (libraryId != null && accessibleLibraryIds.contains(libraryId)) {
                specs.add(AppBookSpecification.inLibrary(libraryId));
            } else if (libraryId != null) {
                throw ApiError.FORBIDDEN.createException("Access denied to library " + libraryId);
            } else {
                specs.add(AppBookSpecification.inLibraries(accessibleLibraryIds));
            }
        } else if (libraryId != null) {
            specs.add(AppBookSpecification.inLibrary(libraryId));
        }

        if (shelfId != null) {
            ShelfEntity shelf = shelfRepository.findById(shelfId)
                    .orElseThrow(() -> ApiError.SHELF_NOT_FOUND.createException(shelfId));
            if (!shelf.isPublic() && !shelf.getUser().getId().equals(userId)) {
                throw ApiError.FORBIDDEN.createException("Access denied to shelf " + shelfId);
            }
            specs.add(AppBookSpecification.inShelf(shelfId));
        }

        if (status != null) {
            specs.add(AppBookSpecification.withReadStatus(status, userId));
        }

        if (search != null && !search.trim().isEmpty()) {
            specs.add(AppBookSpecification.searchText(search));
        }

        if (fileType != null) {
            specs.add(AppBookSpecification.withFileType(fileType));
        }

        if (minRating != null) {
            specs.add(AppBookSpecification.withMinRating(minRating, userId));
        }

        if (maxRating != null) {
            specs.add(AppBookSpecification.withMaxRating(maxRating, userId));
        }

        if (authors != null && !authors.trim().isEmpty()) {
            specs.add(AppBookSpecification.withAuthor(authors.trim()));
        }

        if (language != null && !language.trim().isEmpty()) {
            specs.add(AppBookSpecification.withLanguage(language.trim()));
        }

        return AppBookSpecification.combine(specs.toArray(new Specification[0]));
    }

    private Sort buildSort(String sortBy, String sortDir) {
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        String field = switch (sortBy != null ? sortBy.toLowerCase() : "") {
            case "title" -> "metadata.title";
            case "seriesname", "series" -> "metadata.seriesName";
            case "lastreadtime" -> "addedOn";
            default -> "addedOn";
        };

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

        List<BookEntity> enriched = enrichBooksForSummary(bookPage.getContent());
        Map<Long, UserBookProgressEntity> progressMap = getProgressMapForBooks(userId, enriched);

        List<AppBookSummary> summaries = enriched.stream()
                .map(book -> mobileBookMapper.toSummary(book, progressMap.get(book.getId())))
                .collect(Collectors.toList());

        return AppPageResponse.of(summaries, pageNum, pageSize, bookPage.getTotalElements());
    }

    private List<BookEntity> enrichBooksForSummary(List<BookEntity> books) {
        if (books.isEmpty()) return books;
        Set<Long> ids = books.stream().map(BookEntity::getId).collect(Collectors.toSet());
        Map<Long, BookEntity> enrichedMap = bookRepository.findAllForSummaryByIds(ids)
                .stream().collect(Collectors.toMap(BookEntity::getId, b -> b));
        return books.stream()
                .map(b -> enrichedMap.getOrDefault(b.getId(), b))
                .collect(Collectors.toList());
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
}
