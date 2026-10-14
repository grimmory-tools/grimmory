package org.booklore.service.browse;

import org.booklore.browse.BrowsePager;
import org.booklore.browse.SortRegistry;
import org.booklore.browse.SortTerm;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

@FunctionalInterface
public interface PageFetcher<E, T> {

    FetchedPage<T> fetch(BrowseScope scope, Specification<E> filter, SortRegistry<E> sorts,
                         List<SortTerm> sortTerms, BrowsePager.Window window);

    record FetchedPage<T>(List<T> items, long total) {
    }
}
