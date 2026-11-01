import {computed, signal, type Signal} from '@angular/core';
import {injectQuery, keepPreviousData} from '@tanstack/angular-query-experimental';

import {type BrowseQueryService} from '../../core/data/browse-query.service';
import {debouncedSignal} from '../util/debounced-signal';
import {SEARCH_DEBOUNCE_MS} from '../util/search-terms';
import {createBrowseFacetQueries, type BrowseFacetQueriesOptions} from './facet-queries';
import {
  countBrowseFacetValues,
  type BrowseFacetDefinitions,
  type BrowseFacetSelection,
  type BrowseFacetValues,
  type BrowseFilterMatchAll,
  type BrowseFilterRangeCommit,
  type BrowseFilterToggle,
  type BrowseScope,
} from './facets';
import {type BrowseUrlState} from './url-state';

export interface BrowseFilterPageConfig<Item extends {id: number}, FacetKey extends string, SortKey extends string> {
  readonly source: Pick<BrowseQueryService<Item, FacetKey, SortKey>, 'facetIndex' | 'facet' | 'page'>;
  readonly fields: BrowseFacetQueriesOptions<FacetKey>['fields'];
  readonly urlState: BrowseUrlState<FacetKey, SortKey>;
  readonly facetDefinitions: Signal<BrowseFacetDefinitions<FacetKey>>;
  readonly scope?: Signal<BrowseScope<FacetKey> | null>;
}

export type BrowseFilterPage<Item extends {id: number}, FacetKey extends string, SortKey extends string> =
  ReturnType<typeof createBrowseFilterPage<Item, FacetKey, SortKey>>;

export function createBrowseFilterPage<Item extends {id: number}, FacetKey extends string, SortKey extends string>(
  config: BrowseFilterPageConfig<Item, FacetKey, SortKey>,
) {
  const {source, urlState} = config;
  const noFacets: Partial<Record<FacetKey, BrowseFacetValues>> = {};

  const staged = signal<BrowseFacetSelection<FacetKey>>(urlState.facets());
  const stagedQuery = signal(urlState.query());
  const debouncedQuery = debouncedSignal(computed(() => stagedQuery().trim()), SEARCH_DEBOUNCE_MS);

  const queries = createBrowseFacetQueries({
    source,
    fields: config.fields,
    definitions: config.facetDefinitions,
    selection: staged,
    query: debouncedQuery,
    scope: config.scope,
  });

  const previewQuery = injectQuery(() => ({
    ...source.page({...queries.collectionParams(), size: 1, sort: []}),
    placeholderData: keepPreviousData,
  }));

  return {
    queries,
    stagedQuery,
    hasActiveFilters: computed(() =>
      countBrowseFacetValues(staged()) > 0 || stagedQuery().trim().length > 0),
    previewTotal: computed(() => previewQuery.data()?.page.totalElements ?? null),
    previewLoading: computed(() => previewQuery.isPending() || previewQuery.isPlaceholderData()),

    toggle(toggle: BrowseFilterToggle<FacetKey>): void {
      staged.set(queries.withState(toggle));
    },

    setMatchAll(change: BrowseFilterMatchAll<FacetKey>): void {
      staged.set(queries.chooseMatchAll(change));
    },

    commitRange(commit: BrowseFilterRangeCommit<FacetKey>): void {
      staged.set(queries.withRange(commit));
    },

    clear(): void {
      staged.set(noFacets);
      stagedQuery.set('');
    },

    commit(): void {
      urlState.applyFilters(staged(), stagedQuery().trim());
    },
  };
}
