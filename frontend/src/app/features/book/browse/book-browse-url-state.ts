import {computed, inject} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
import {ActivatedRoute, Router, type Params} from '@angular/router';

import {
  bookFacetQueryParams,
  parseFacetParams,
  parseSortTermsToken,
  type BookSortTerm,
  type FacetValueMap,
} from '../data/book-query-params';

export type BookBrowseViewMode = 'grid' | 'table';

export type BookBrowseUrlState = ReturnType<typeof createBookBrowseUrlState>;

export function createBookBrowseUrlState() {
  const route = inject(ActivatedRoute);
  const router = inject(Router);
  const queryParamMap = toSignal(route.queryParamMap, {
    initialValue: route.snapshot.queryParamMap,
  });

  const facets = computed(() => parseFacetParams(queryParamMap().getAll('facet')));
  const query = computed(() => (queryParamMap().get('query') ?? '').trim());
  const sortTerms = computed<readonly BookSortTerm[]>(() =>
    parseSortTermsToken(queryParamMap().get('sort')));
  const view = computed<BookBrowseViewMode | null>(() => {
    const requested = queryParamMap().get('view');
    return requested === 'grid' || requested === 'table' ? requested : null;
  });

  const merge = (queryParams: Params, replaceUrl: boolean): void => {
    void router.navigate([], {
      relativeTo: route,
      queryParams,
      queryParamsHandling: 'merge',
      replaceUrl,
    });
  };

  return {
    facets,
    query,
    sortTerms,
    view,

    setFacets(next: FacetValueMap): void {
      merge(bookFacetQueryParams(next), false);
    },

    setFacetRange(next: FacetValueMap): void {
      merge(bookFacetQueryParams(next), true);
    },

    setQuery(term: string): void {
      merge({query: term || null}, true);
    },

    clearQuery(): void {
      merge({query: null}, false);
    },

    clearFilters(): void {
      merge({facet: null, query: null}, false);
    },

    setView(next: BookBrowseViewMode): void {
      merge({view: next}, true);
    },

    setSortToken(token: string | null, replaceUrl: boolean): void {
      merge({sort: token}, replaceUrl);
    },

    openFilterPage(): void {
      void router.navigate(['filter'], {relativeTo: route, queryParamsHandling: 'preserve'});
    },

    applyFilters(nextFacets: FacetValueMap, nextQuery: string): void {
      void router.navigate(['..'], {
        relativeTo: route,
        queryParams: {
          ...bookFacetQueryParams(nextFacets),
          query: nextQuery || null,
        },
        queryParamsHandling: 'merge',
      });
    },
  };
}
