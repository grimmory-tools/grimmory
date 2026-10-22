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
  hasBrowseFacetValues,
  pinnedBrowseFacetSelection,
  withBrowseFacetRange,
  type BrowseFacetDefinitions,
  type BrowseFacetKey,
  type BrowseFacetPin,
  type BrowseFacetSelection,
  type BrowseFilterChip,
  type BrowseFilterGroup,
  type BrowseFilterOpen,
  type BrowseFilterRangeCommit,
  type BrowseFilterSearch,
} from './facets';

export interface BrowseFacetQueriesOptions<FacetKey extends string> {
  readonly source: Pick<BrowseQueryService<{id: number}, FacetKey, string>, 'facetIndex' | 'facet'>;
  readonly fields: {readonly facetKeys: readonly FacetKey[]; readonly openRailFacets: ReadonlySet<FacetKey>};
  readonly definitions: Signal<BrowseFacetDefinitions<FacetKey>>;
  readonly selection: Signal<BrowseFacetSelection<FacetKey>>;
  readonly query: Signal<string>;
  readonly pinned?: Signal<BrowseFacetPin<FacetKey> | null>;
  readonly enabled?: Signal<boolean>;
}

export function createBrowseFacetQueries<FacetKey extends string>({
  source,
  fields,
  definitions,
  selection,
  query,
  pinned,
  enabled,
}: BrowseFacetQueriesOptions<FacetKey>) {
  const pin = () => pinned?.() ?? null;
  const noFacets: Partial<Record<BrowseFacetKey<FacetKey>, readonly string[]>> = {};
  const noSearchTerms: Partial<Record<FacetKey, string>> = {};
  const collectionParams = computed<BrowseCollectionFilterParams<FacetKey>>(() => ({
    facets: pinnedBrowseFacetSelection(selection(), pin()),
    query: normalizeRemoteSearchTerm(query()) || undefined,
  }));
  const scopeParams = computed<BrowseCollectionFilterParams<FacetKey>>(() => ({
    facets: pinnedBrowseFacetSelection(noFacets, pin()),
  }));
  const isEnabled = () => enabled?.() ?? true;

  const indexQuery = injectQuery(() => source.facetIndex(scopeParams()));
  const available = computed<ReadonlySet<string>>(() => new Set(indexQuery.data()?.facetKeys));

  const selectedKeys = fields.facetKeys.filter(key => hasBrowseFacetValues(selection(), key));
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
  const railGroups = computed<BrowseFilterGroup<FacetKey>[]>(() =>
    frame().map(group => {
      const keys = sectionKeys(group.key);
      return {
        ...group,
        picks: new Map(keys.map(key => [key, browseFacetPicks(selection(), key)])),
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
    withRange: (
      current: BrowseFacetSelection<FacetKey>,
      {key, min, max}: BrowseFilterRangeCommit<FacetKey>,
      matchAll: boolean,
    ) => {
      const bands = served().find(group => group.key === key)?.values ?? [];
      return withBrowseFacetRange(current, key, min, max, new Set(bands.map(value => value.value)), matchAll);
    },
  };
}
