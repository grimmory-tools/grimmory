import {computed, signal, type Signal} from '@angular/core';
import {injectQuery, keepPreviousData} from '@tanstack/angular-query-experimental';

import {type BrowseQueryService} from '../../core/data/browse-query.service';
import {debouncedSignal} from '../util/debounced-signal';
import {SEARCH_DEBOUNCE_MS} from '../util/search-terms';
import {createBrowseFacetQueries, type BrowseFacetQueriesOptions} from './facet-queries';
import {
  browseFacetMatchAll,
  countBrowseFacetValues,
  setBrowseFacetValue,
  withBrowseFacetMatchAll,
  type BrowseFacetDefinitions,
  type BrowseFacetKey,
  type BrowseFacetPin,
  type BrowseFacetSelection,
  type BrowseFilterRangeCommit,
  type BrowseFilterToggle,
} from './facets';
import {createBrowseFilterSettings} from './filter-settings';
import {type BrowseUrlState} from './url-state';

export interface BrowseFilterPageConfig<Item extends {id: number}, FacetKey extends string, SortKey extends string> {
  readonly source: Pick<BrowseQueryService<Item, FacetKey, SortKey>, 'facetIndex' | 'facet' | 'page'>;
  readonly fields: BrowseFacetQueriesOptions<FacetKey>['fields'];
  readonly urlState: BrowseUrlState<FacetKey, SortKey>;
  readonly facetDefinitions: Signal<BrowseFacetDefinitions<FacetKey>>;
  readonly pinned?: Signal<BrowseFacetPin<FacetKey> | null>;
}

export type BrowseFilterPage<Item extends {id: number}, FacetKey extends string, SortKey extends string> =
  ReturnType<typeof createBrowseFilterPage<Item, FacetKey, SortKey>>;

export function createBrowseFilterPage<Item extends {id: number}, FacetKey extends string, SortKey extends string>(
  config: BrowseFilterPageConfig<Item, FacetKey, SortKey>,
) {
  const {source, urlState} = config;
  const noFacets: Partial<Record<BrowseFacetKey<FacetKey>, readonly string[]>> = {};
  const filterSettings = createBrowseFilterSettings(urlState);

  const staged = signal<BrowseFacetSelection<FacetKey>>(urlState.facets());
  const matchAll = computed(() => browseFacetMatchAll(staged()) ?? filterSettings.matchAll());
  const stagedQuery = signal(urlState.query());
  const debouncedQuery = debouncedSignal(computed(() => stagedQuery().trim()), SEARCH_DEBOUNCE_MS);

  const queries = createBrowseFacetQueries({
    source,
    fields: config.fields,
    definitions: config.facetDefinitions,
    selection: staged,
    query: debouncedQuery,
    pinned: config.pinned,
  });

  const previewQuery = injectQuery(() => ({
    ...source.page({...queries.collectionParams(), size: 1, sort: []}),
    placeholderData: keepPreviousData,
  }));

  return {
    filterSettings,
    queries,
    stagedQuery,
    matchAll,
    hasActiveFilters: computed(() =>
      countBrowseFacetValues(staged()) > 0 || stagedQuery().trim().length > 0),
    previewTotal: computed(() => previewQuery.data()?.page.totalElements ?? null),
    previewLoading: computed(() => previewQuery.isPending() || previewQuery.isPlaceholderData()),

    toggle(toggle: BrowseFilterToggle<FacetKey>): void {
      staged.update(current => setBrowseFacetValue(current, toggle.key, toggle.value, toggle.state, matchAll()));
    },

    setMatchAll(next: boolean): void {
      filterSettings.saveMatchAll(next);
      staged.update(current => withBrowseFacetMatchAll(current, next));
    },

    commitRange(commit: BrowseFilterRangeCommit<FacetKey>): void {
      staged.update(current => queries.withRange(current, commit, matchAll()));
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
