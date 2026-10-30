import {computed, linkedSignal, signal, type Signal} from '@angular/core';
import {injectQuery, keepPreviousData} from '@tanstack/angular-query-experimental';

import {type BrowseCollectionFilterParams} from '../../core/data/browse-query-params';
import {type BrowseQueryService} from '../../core/data/browse-query.service';
import {debouncedSignal} from '../util/debounced-signal';
import {heldSignal} from '../util/held-signal';
import {normalizeRemoteSearchTerm, SEARCH_DEBOUNCE_MS} from '../util/search-terms';
import {
  browseFilterGroups,
  browseFilterChips,
  browseFacetPicks,
  browseFacetSectionKeys,
  browseFacetValues,
  withBrowseFacetMatchAll,
  withBrowseFacetState,
  withBrowseFacetRange,
  type BrowseFacetDefinitions,
  type BrowseFacetSelection,
  type BrowseFacetValues,
  type BrowseFilterChip,
  type BrowseFilterGroup,
  type BrowseFilterMatchAll,
  type BrowseFilterOpen,
  type BrowseFilterRangeCommit,
  type BrowseFilterSearch,
  type BrowseFilterToggle,
  type BrowseScope,
} from './facets';

export interface BrowseFacetQueriesOptions<FacetKey extends string> {
  readonly source: Pick<BrowseQueryService<{id: number}, FacetKey, string>, 'facetIndex' | 'facet'>;
  readonly fields: {readonly facetKeys: readonly FacetKey[]; readonly openRailFacets: ReadonlySet<FacetKey>};
  readonly definitions: Signal<BrowseFacetDefinitions<FacetKey>>;
  readonly selection: Signal<BrowseFacetSelection<FacetKey>>;
  readonly query: Signal<string>;
  readonly scope?: Signal<BrowseScope<FacetKey> | null>;
  readonly enabled?: Signal<boolean>;
}

export function createBrowseFacetQueries<FacetKey extends string>({
  source,
  fields,
  definitions,
  selection,
  query,
  scope,
  enabled,
}: BrowseFacetQueriesOptions<FacetKey>) {
  const pageScope = () => scope?.() ?? undefined;
  const noFacets: Partial<Record<FacetKey, BrowseFacetValues>> = {};
  const noSearchTerms: Partial<Record<FacetKey, string>> = {};
  const collectionParams = computed<BrowseCollectionFilterParams<FacetKey>>(() => ({
    facets: selection(),
    scope: pageScope(),
    query: normalizeRemoteSearchTerm(query()) || undefined,
  }));
  const scopeParams = computed<BrowseCollectionFilterParams<FacetKey>>(() => ({
    facets: noFacets,
    scope: pageScope(),
  }));
  const isEnabled = () => enabled?.() ?? true;

  const indexQuery = injectQuery(() => source.facetIndex(scopeParams()));
  const available = computed<ReadonlySet<string>>(() => new Set(indexQuery.data()?.facetKeys));

  const selectedKeys = fields.facetKeys.filter(key => selection()[key] !== undefined);
  const sectionKeys = (key: FacetKey) => browseFacetSectionKeys(definitions(), key);
  const openKeys = signal<ReadonlySet<FacetKey>>(
    new Set([...fields.openRailFacets, ...selectedKeys].flatMap(sectionKeys)));
  const searchTerms = signal<Readonly<Partial<Record<FacetKey, string>>>>(noSearchTerms);
  const debouncedSearchTerms = debouncedSignal(searchTerms, SEARCH_DEBOUNCE_MS);

  function facetQueries(key: FacetKey) {
    const open = computed(() => isEnabled() && openKeys().has(key) && available().has(key));
    const searchTerm = computed(() => (debouncedSearchTerms()[key] ?? '').trim());

    const list = injectQuery(() => ({
      ...source.facet(key, collectionParams()),
      enabled: open(),
      placeholderData: keepPreviousData,
    }));

    const searchOnServer = computed(() => searchTerm() !== '' && list.data()?.complete === false);
    const search = injectQuery(() => ({
      ...source.facet(key, collectionParams(), searchTerm()),
      enabled: open() && searchOnServer(),
      placeholderData: keepPreviousData,
    }));

    return {
      key,
      loading: computed(() => open() && list.isPending()),
      stale: computed(() => open() && (list.isPlaceholderData() || (searchOnServer() && search.isPlaceholderData()))),
      served: computed(() => {
        const results = searchOnServer() ? search.data() : undefined;
        return results ?? list.data();
      }),
    };
  }

  const facets = fields.facetKeys.map(facetQueries);

  const served = computed(() => facets.flatMap(facet => facet.served() ?? []));
  const stale = computed(() => facets.some(facet => facet.stale()));
  const loadingKeys = computed(() => new Set(facets.filter(facet => facet.loading()).map(facet => facet.key)));
  const railReady = linkedSignal<boolean, boolean>({
    source: () => isEnabled() && !indexQuery.isPending() && loadingKeys().size === 0,
    computation: (loaded, previous) => loaded || (previous?.value ?? false),
  });

  const frame = heldSignal(
    () => browseFilterGroups(available(), served(), definitions(), selection()),
    stale,
  );
  const matchAllKeys = signal<ReadonlySet<FacetKey>>(
    new Set(fields.facetKeys.filter(key => selection()[key]?.matchAll)));
  const isMatchAll = (key: FacetKey) => {
    const values = browseFacetValues(selection(), key);
    return values.include.length > 0 ? values.matchAll === true : matchAllKeys().has(key);
  };
  const railGroups = computed<BrowseFilterGroup<FacetKey>[]>(() =>
    frame().map(group => {
      const keys = sectionKeys(group.key);
      return {
        ...group,
        picks: new Map(keys.map(key => [key, browseFacetPicks(browseFacetValues(selection(), key))])),
        matchAll: isMatchAll(group.key),
        loading: keys.some(key => loadingKeys().has(key)),
      };
    }));
  const chips = computed<BrowseFilterChip<FacetKey>[]>(() =>
    browseFilterChips(served(), definitions(), selection()));

  return {
    collectionParams,
    sortTokens: computed<readonly string[]>(() => indexQuery.data()?.sortTokens ?? []),
    ready: railReady.asReadonly(),
    chips,
    railGroups,
    openKeys: openKeys.asReadonly(),
    searchTerms: searchTerms.asReadonly(),
    setOpen: ({key, open}: BrowseFilterOpen<FacetKey>) => openKeys.update(keys => {
      const section = sectionKeys(key);
      return open ? new Set([...keys, ...section]) : new Set([...keys].filter(openKey => !section.includes(openKey)));
    }),
    setSearch: ({key, term}: BrowseFilterSearch<FacetKey>) =>
      searchTerms.update(terms => ({...terms, [key]: term})),
    withState: ({key, value, state}: BrowseFilterToggle<FacetKey>) =>
      withBrowseFacetState(selection(), key, value, state, isMatchAll(key)),
    chooseMatchAll: ({key, matchAll}: BrowseFilterMatchAll<FacetKey>) => {
      matchAllKeys.update(keys =>
        matchAll ? new Set([...keys, key]) : new Set([...keys].filter(matchKey => matchKey !== key)));
      return withBrowseFacetMatchAll(selection(), key, matchAll);
    },
    withRange: ({key, min, max}: BrowseFilterRangeCommit<FacetKey>) => {
      const bands = served().find(group => group.key === key)?.values ?? [];
      return withBrowseFacetRange(selection(), key, min, max, new Set(bands.map(value => value.value)));
    },
  };
}
