package org.booklore.service.browse;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.booklore.browse.BrowsePage;
import org.booklore.browse.BrowsePager;
import org.booklore.browse.FacetLogic;
import org.booklore.browse.ParamsHash;
import org.booklore.browse.SortParser;
import org.booklore.browse.SortTerm;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.UserBookFileProgressEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.service.book.BookQueryService;
import org.booklore.service.progress.ReadingProgressService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookBrowseService {

    private static final String PAGE_PATH = "/api/v1/books/page";
    private static final String FACET_PATH = "/api/v1/books/facets";

    private final AuthenticationService authenticationService;
    private final BookQueryService bookQueryService;
    private final ReadingProgressService readingProgressService;
    private final BookFilterSpecifications filterSpecifications;
    private final BookSortRegistry sortRegistry;
    private final BrowseScopeFactory scopeFactory;
    private final BrowsePager pager;
    private final EntityManager entityManager;

    public BrowsePage<Book> browse(String sort, List<String> facet, String facetLogicParam, String query, String cursor, Pageable pageable) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        BrowseScope scope = scopeFactory.from(user);
        Long userId = scope.userId();

        Map<String, List<String>> facets = BrowseParams.parseFacets(facet);
        FacetLogic facetLogic = FacetLogic.from(facetLogicParam);
        String paramsHash = ParamsHash.compute(query, facets, facetLogic);

        BrowsePager.Window window = pager.resolve(sort, cursor, pageable, paramsHash);

        List<SortTerm> sortTerms = SortParser.parse(window.sort(), sortRegistry.registry().keys());
        Specification<BookEntity> filter = filterSpecifications.base(query, facets, facetLogic, scope, null);
        Specification<BookEntity> spec = withSort(filter, sortTerms, userId, window.randomSeed());

        Pageable pageRequest = PageRequest.of((int) (window.offset() / window.limit()), window.limit());
        Page<Book> page = bookQueryService.findBooksPaged(spec, pageRequest, userId);
        enrich(page.getContent(), userId);

        return pager.assemble(PAGE_PATH, FACET_PATH, BrowseParams.preserved(facet, facetLogicParam, query),
                window, page.getTotalElements(), page.getContent());
    }

    public List<Long> findAllIds(String sort, List<String> facet, String facetLogicParam, String query) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        BrowseScope scope = scopeFactory.from(user);
        Long userId = scope.userId();

        Map<String, List<String>> facets = BrowseParams.parseFacets(facet);
        FacetLogic facetLogic = FacetLogic.from(facetLogicParam);
        List<SortTerm> sortTerms = SortParser.parse(sort, sortRegistry.registry().keys());
        Specification<BookEntity> filter = filterSpecifications.base(query, facets, facetLogic, scope, null);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<BookEntity> root = cq.from(BookEntity.class);
        cq.select(root.get("id"));
        Predicate predicate = filter.toPredicate(root, cq, cb);
        if (predicate != null) {
            cq.where(predicate);
        }

        // The `findAllIds` doesn't pass a cursor so it's a random seed every time.
        int randomSeed = BrowsePager.newRandomSeed();

        cq.orderBy(sortRegistry.registry().toOrders(sortTerms, root, cq, cb, userId, randomSeed));

        return entityManager.createQuery(cq).getResultList();
    }

    public BrowsePage<Book> wrapLegacy(Page<Book> page, Pageable pageable) {
        String paramsHash = ParamsHash.compute(null, Map.of(), FacetLogic.AND);

        // Pass a null random seed for the legacy case - we don't actually store a seed value and
        // this is good enough for most use cases.
        BrowsePager.Window window = new BrowsePager.Window(pageable.getOffset(), pageable.getPageSize(), null, paramsHash, null);
        return pager.assemble(PAGE_PATH, FACET_PATH, "", window, page.getTotalElements(), page.getContent());
    }

    private Specification<BookEntity> withSort(Specification<BookEntity> filter, List<SortTerm> sortTerms, Long userId, Integer randomSeed) {
        return (root, query, cb) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                query.orderBy(sortRegistry.registry().toOrders(sortTerms, root, query, cb, userId, randomSeed));
            }
            return filter.toPredicate(root, query, cb);
        };
    }

    private void enrich(List<Book> books, Long userId) {
        Set<Long> bookIds = books.stream().map(Book::getId).collect(Collectors.toSet());
        Map<Long, UserBookProgressEntity> progress = readingProgressService.fetchUserProgress(userId, bookIds);
        Map<Long, UserBookFileProgressEntity> fileProgress = readingProgressService.fetchUserFileProgress(userId, bookIds);
        for (Book book : books) {
            readingProgressService.enrichBookWithProgress(book, progress.get(book.getId()), fileProgress.get(book.getId()));
        }
    }
}
