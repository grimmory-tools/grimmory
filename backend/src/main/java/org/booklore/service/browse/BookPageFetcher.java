package org.booklore.service.browse;

import lombok.RequiredArgsConstructor;
import org.booklore.browse.BrowsePager;
import org.booklore.browse.SortRegistry;
import org.booklore.browse.SortTerm;
import org.booklore.model.dto.Book;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.UserBookFileProgressEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.service.book.BookQueryService;
import org.booklore.service.progress.ReadingProgressService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class BookPageFetcher implements BrowseService.PageFetcher<BookEntity, Book> {

    private final BookQueryService bookQueryService;
    private final ReadingProgressService readingProgressService;

    @Override
    public BrowseService.FetchedPage<Book> fetch(BrowseScope scope, Specification<BookEntity> filter, SortRegistry<BookEntity> sorts,
                                                 List<SortTerm> sortTerms, BrowsePager.Window window) {
        Long userId = scope.userId();
        Specification<BookEntity> spec = withSort(filter, sorts, sortTerms, userId, window.randomSeed());

        Pageable pageRequest = PageRequest.of((int) (window.offset() / window.limit()), window.limit());
        Page<Book> page = bookQueryService.findBooksPaged(spec, pageRequest, userId);
        enrich(page.getContent(), userId);
        return new BrowseService.FetchedPage<>(page.getContent(), page.getTotalElements());
    }

    private Specification<BookEntity> withSort(Specification<BookEntity> filter, SortRegistry<BookEntity> sorts,
                                               List<SortTerm> sortTerms, Long userId, Integer randomSeed) {
        return (root, query, cb) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                query.orderBy(sorts.toOrders(sortTerms, root, query, cb, userId, randomSeed));
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
