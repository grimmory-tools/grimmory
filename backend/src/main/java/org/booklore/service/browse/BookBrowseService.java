package org.booklore.service.browse;

import lombok.RequiredArgsConstructor;
import org.booklore.browse.BrowsePage;
import org.booklore.browse.CursorCodec;
import org.booklore.browse.CursorState;
import org.booklore.browse.FacetLogic;
import org.booklore.browse.Link;
import org.booklore.browse.LinksBuilder;
import org.booklore.browse.ParamsHash;
import org.booklore.browse.SortParser;
import org.booklore.browse.SortTerm;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.ApiError;
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
    private static final int MAX_PAGE_SIZE = 100;

    private final AuthenticationService authenticationService;
    private final BookQueryService bookQueryService;
    private final ReadingProgressService readingProgressService;
    private final BookFilterSpecifications filterSpecifications;
    private final BookSortRegistry sortRegistry;
    private final CursorCodec cursorCodec;
    private final LinksBuilder linksBuilder;

    public BrowsePage<Book> browse(String sort, List<String> facet, String facetLogicParam, String query, String cursor, Pageable pageable) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();
        boolean isAdmin = user.getPermissions().isAdmin();

        Map<String, List<String>> facets = BookFilterSpecifications.parseFacets(facet);
        FacetLogic facetLogic = FacetLogic.from(facetLogicParam);
        String paramsHash = ParamsHash.compute(query, facets, facetLogic);

        long offset;
        int limit;
        String sortString;
        if (cursor != null) {
            CursorState state = cursorCodec.decode(cursor);
            cursorCodec.verifyParamsMatch(state, paramsHash);
            offset = state.offset();
            limit = state.limit();
            sortString = state.sort();
        } else {
            offset = pageable.getOffset();
            limit = pageable.getPageSize();
            sortString = sort;
        }
        if (limit <= 0) {
            throw ApiError.INVALID_INPUT.createException("Page size must be positive.");
        }
        limit = Math.min(limit, MAX_PAGE_SIZE);

        List<SortTerm> sortTerms = SortParser.parse(sortString, sortRegistry.registry().keys());
        Specification<BookEntity> filter = filterSpecifications.base(query, facets, facetLogic, userId, isAdmin, BookFilterSpecifications.libraryIds(user), null);
        Specification<BookEntity> spec = withSort(filter, sortTerms, userId);

        Pageable pageRequest = PageRequest.of((int) (offset / limit), limit);
        Page<Book> page = bookQueryService.findBooksPaged(spec, pageRequest, userId);
        enrich(page.getContent(), userId);

        CursorState baseState = new CursorState(CursorCodec.CURRENT_VERSION, offset, limit, sortString, paramsHash);
        String currentCursor = cursorCodec.encode(baseState);
        List<Link> links = linksBuilder.build(new LinksBuilder.Context(
                PAGE_PATH, BrowseParams.preserved(facet, facetLogicParam, query), offset, limit, page.getTotalElements(), baseState));

        return BrowsePage.of(page.getContent(), offset, limit, page.getTotalElements(), currentCursor, links);
    }

    public BrowsePage<Book> wrapLegacy(Page<Book> page, Pageable pageable) {
        long offset = pageable.getOffset();
        int limit = pageable.getPageSize();
        String paramsHash = ParamsHash.compute(null, Map.of(), FacetLogic.AND);
        CursorState baseState = new CursorState(CursorCodec.CURRENT_VERSION, offset, limit, null, paramsHash);
        List<Link> links = linksBuilder.build(new LinksBuilder.Context(
                PAGE_PATH, "", offset, limit, page.getTotalElements(), baseState));
        return BrowsePage.of(page.getContent(), offset, limit, page.getTotalElements(), cursorCodec.encode(baseState), links);
    }

    private Specification<BookEntity> withSort(Specification<BookEntity> filter, List<SortTerm> sortTerms, Long userId) {
        return (root, query, cb) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                query.orderBy(sortRegistry.registry().toOrders(sortTerms, root, query, cb, userId));
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
