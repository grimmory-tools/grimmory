import {computed, inject} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
import {ActivatedRoute, Router, type Params} from '@angular/router';

import {type BrowseSortTerm} from '../../core/data/browse.models';
import {facetQueryParams, parseFacetParams, parseSortTermsToken} from '../../core/data/browse-query-params';
import {type BrowseFacetSelection} from './facets';

export type BrowseViewMode = 'grid' | 'table';

export interface BrowseUrlKeys<FacetKey extends string, SortKey extends string> {
  readonly isFacetKey: (key: string) => key is FacetKey;
  readonly isSortKey: (key: string) => key is SortKey;
}

export type BrowseUrlState<FacetKey extends string, SortKey extends string> =
  ReturnType<typeof createBrowseUrlState<FacetKey, SortKey>>;

export function createBrowseUrlState<FacetKey extends string, SortKey extends string>(
  {isFacetKey, isSortKey}: BrowseUrlKeys<FacetKey, SortKey>,
) {
  const route = inject(ActivatedRoute);
  const router = inject(Router);
  const queryParamMap = toSignal(route.queryParamMap, {
    initialValue: route.snapshot.queryParamMap,
  });

  const facets = computed(() => parseFacetParams(queryParamMap().getAll('facet'), isFacetKey));
  const query = computed(() => (queryParamMap().get('query') ?? '').trim());
  const sortTerms = computed<readonly BrowseSortTerm<SortKey>[]>(() =>
    parseSortTermsToken(queryParamMap().get('sort'), isSortKey));
  const view = computed<BrowseViewMode | null>(() => {
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

    setFacets(next: BrowseFacetSelection<FacetKey>): void {
      merge(facetQueryParams(next), false);
    },

    setFacetRange(next: BrowseFacetSelection<FacetKey>): void {
      merge(facetQueryParams(next), true);
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

    setView(next: BrowseViewMode): void {
      merge({view: next}, true);
    },

    setSortToken(token: string | null, replaceUrl: boolean): void {
      merge({sort: token}, replaceUrl);
    },

    openFilterPage(): void {
      void router.navigate(['filter'], {relativeTo: route, queryParamsHandling: 'preserve'});
    },

    applyFilters(nextFacets: BrowseFacetSelection<FacetKey>, nextQuery: string): void {
      void router.navigate(['..'], {
        relativeTo: route,
        queryParams: {
          ...facetQueryParams(nextFacets),
          query: nextQuery || null,
        },
        queryParamsHandling: 'merge',
      });
    },
  };
}
