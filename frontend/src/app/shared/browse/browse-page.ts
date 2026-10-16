import {computed, inject, linkedSignal, signal, type Signal} from '@angular/core';
import {injectInfiniteQuery, QueryClient} from '@tanstack/angular-query-experimental';

import {type BrowseSortTerm} from '../../core/data/browse.models';
import {browseCollectionKeys} from '../../core/data/browse-query-keys';
import {sortTermsToken, type BrowsePageParams} from '../../core/data/browse-query-params';
import {type BrowseQueryService} from '../../core/data/browse-query.service';
import {LayoutService} from '../layout/layout.service';
import {LocalStorageService} from '../service/local-storage.service';
import {createGridDensity, type GridDensityDirection, type GridDensityGrid} from '../util/grid-density.util';
import {heldSignal} from '../util/held-signal';
import {type ScalePreference} from '../util/scale-preference.util';
import {createBrowseFacetQueries, type BrowseFacetQueriesOptions} from './facet-queries';
import {
  countBrowseFacetValues,
  setBrowseFacetValue,
  type BrowseFacetDefinitions,
  type BrowseFacetPin,
  type BrowseFilterChip,
  type BrowseFilterRangeCommit,
  type BrowseFilterToggle,
} from './facets';
import {createBrowseFilterSettings} from './filter-settings';
import {createBrowseResults} from './results';
import {createBrowseSearchDraft} from './search-draft';
import {createBrowseSelection} from './selection';
import {installBrowseSelectionShortcuts} from './selection-shortcuts';
import {browseSortOptions, type BrowseSortDefinitions} from './sort';
import {type BrowseUrlState} from './url-state';

const PAGE_SIZE = 60;
const RAIL_OPEN_STORAGE_KEY = 'browseFilterRailOpen';
const SELECTION_CLICK_AWAY_EXEMPT =
  'app-browse-bulk-actions-bar, app-menu, app-page-header, aside, ' +
  '.cdk-overlay-container, .p-dialog-mask, [role="dialog"], ' +
  'button, a, input, textarea, select, label';

const GRID_GAP = 16;
const DEFAULT_MOBILE_COLUMNS = 3;
const MIN_MOBILE_COLUMNS = 2;
const MAX_MOBILE_COLUMNS = 4;

export interface BrowseDensityConfig {
  readonly baseWidth: number;
  readonly scale: ScalePreference;
  readonly mobileColumnsStorageKey: string;
}

export interface BrowsePageConfig<Item extends {id: number}, FacetKey extends string, SortKey extends string> {
  readonly source: Pick<
    BrowseQueryService<Item, FacetKey, SortKey>,
    'facetIndex' | 'facet' | 'infinitePage' | 'restartInfinitePage' | 'ids'
  >;
  readonly fields: BrowseFacetQueriesOptions<FacetKey>['fields'] & {
    readonly sortDefinitions: BrowseSortDefinitions<SortKey>;
  };
  readonly urlState: BrowseUrlState<FacetKey, SortKey>;
  readonly facetDefinitions: Signal<BrowseFacetDefinitions<FacetKey>>;
  readonly pinned?: Signal<BrowseFacetPin<FacetKey> | null>;
  readonly defaultSortTerms: (available: ReadonlySet<SortKey>) => readonly BrowseSortTerm<SortKey>[];
  readonly artworkUrls: (items: readonly Item[], lastVisibleIndex: number) => readonly string[];
  readonly density: BrowseDensityConfig;
  readonly clickAwayExempt: string;
  readonly selectionSuspended?: () => boolean;
  readonly scrollToTop?: () => void;
}

export interface BrowsePageView {
  scrollToTop(): void;
  densityGrid(): GridDensityGrid | null;
}

export interface BrowseChipsBand<FacetKey extends string> {
  count: number;
  query: string;
  chips: readonly BrowseFilterChip<FacetKey>[];
}

export type BrowsePage<Item extends {id: number}, FacetKey extends string, SortKey extends string> =
  ReturnType<typeof createBrowsePage<Item, FacetKey, SortKey>>;

export function createBrowsePage<Item extends {id: number}, FacetKey extends string, SortKey extends string>(
  config: BrowsePageConfig<Item, FacetKey, SortKey>,
) {
  const {source, fields, urlState, density} = config;
  const queryClient = inject(QueryClient);
  const layout = inject(LayoutService);
  const localStorage = inject(LocalStorageService);

  let view: BrowsePageView | null = null;

  const isMobile = computed(() => !layout.isDesktop());
  const mobileSelectMode = linkedSignal<boolean, boolean>({
    source: isMobile,
    computation: (mobile, previous) => mobile && (previous?.value ?? false),
  });

  const railOpen = signal(localStorage.get<boolean>(RAIL_OPEN_STORAGE_KEY) !== false);
  const railVisible = computed(() => !isMobile() && railOpen());

  const gridDensity = createGridDensity(localStorage, {
    useFixedColumns: isMobile,
    screenWidth: () => window.innerWidth,
    storageKey: density.mobileColumnsStorageKey,
    defaultColumns: DEFAULT_MOBILE_COLUMNS,
    minColumns: MIN_MOBILE_COLUMNS,
    maxColumns: MAX_MOBILE_COLUMNS,
    scale: density.scale.scaleFactor,
    minScale: density.scale.minScale,
    maxScale: density.scale.maxScale,
    gap: GRID_GAP,
    baseWidth: () => density.baseWidth,
    setScale: scale => density.scale.setScale(scale),
  });

  const filterSettings = createBrowseFilterSettings(urlState);
  const queries = createBrowseFacetQueries({
    source,
    fields,
    definitions: config.facetDefinitions,
    selection: urlState.facets,
    query: urlState.query,
    pinned: config.pinned,
    enabled: railVisible,
  });

  const sortOptions = computed(() => browseSortOptions(queries.sortTokens(), fields.sortDefinitions));
  const defaultSortTerms = computed(() =>
    config.defaultSortTerms(new Set(sortOptions().map(option => option.id))));
  const sortTerms = computed<readonly BrowseSortTerm<SortKey>[]>(() => {
    const terms = urlState.sortTerms();
    return terms.length > 0 ? terms : defaultSortTerms();
  });
  const isDefaultSort = (terms: readonly BrowseSortTerm<SortKey>[]): boolean =>
    sortTermsToken(terms) === sortTermsToken(defaultSortTerms());

  function setSortTerms(terms: readonly BrowseSortTerm<SortKey>[]): void {
    const next = terms.length > 0 ? terms : defaultSortTerms();
    const collapsesMultiSort = sortTerms().length > 1 && next.length === 1;
    urlState.setSortToken(isDefaultSort(next) ? null : sortTermsToken(next), !collapsesMultiSort);
  }

  const searchDraft = createBrowseSearchDraft({
    committed: urlState.query,
    commit: term => urlState.setQuery(term),
  });

  const params = computed<BrowsePageParams<FacetKey, SortKey>>(() => ({
    ...queries.collectionParams(),
    size: PAGE_SIZE,
    sort: sortTerms(),
  }));
  const collectionKeys = computed(() => browseCollectionKeys(params()));
  const shuffleGeneration = signal(0);
  const listKey = computed(() => `${collectionKeys().listKey}:${shuffleGeneration()}`);
  const pageQuery = injectInfiniteQuery(() => source.infinitePage(params()));

  const results = createBrowseResults<Item>({
    query: pageQuery,
    listKey,
    artworkUrls: config.artworkUrls,
    scrollToTop: () => {
      config.scrollToTop?.();
      view?.scrollToTop();
    },
  });

  const chipsBand = heldSignal<BrowseChipsBand<FacetKey>>(
    () => ({
      count: countBrowseFacetValues(urlState.facets()) + (urlState.query() ? 1 : 0),
      query: urlState.query(),
      chips: queries.chips(),
    }),
    results.showingPreviousResults,
  );

  const selection = createBrowseSelection({
    filtersKey: computed(() => collectionKeys().filtersKey),
    listKey,
    items: results.items,
    totalElements: results.total,
  });
  const selectionEnabled = computed(() => !isMobile() || mobileSelectMode());
  const selectionActive = computed(() => selectionEnabled() && (selection.active() || mobileSelectMode()));

  installBrowseSelectionShortcuts({
    enabled: selectionEnabled,
    active: selection.active,
    suspended: config.selectionSuspended,
    clear: () => selection.clear(),
    selectAll: () => selection.selectAll(),
    onEscapeWhileInactive: () => mobileSelectMode.set(false),
    clickAwayExempt: `${config.clickAwayExempt}, ${SELECTION_CLICK_AWAY_EXEMPT}`,
  });

  function clearQuery(): void {
    searchDraft.set('');
    urlState.clearQuery();
  }

  return {
    filterSettings,
    queries,
    sortOptions,
    sortTerms,
    isDefaultSort,
    setSortTerms,
    searchDraft,
    results,
    selection,
    selectionEnabled,
    selectionActive,
    isSelected: (id: number): boolean => selectionEnabled() && selection.isSelected(id),
    fetchMatchingIds: (): Promise<readonly number[]> => queryClient.query(source.ids(params())),
    chipsBand,
    isMobile,
    mobileSelectMode: mobileSelectMode.asReadonly(),
    rail: {
      visible: railVisible,
      toggle(): void {
        if (isMobile()) {
          urlState.openFilterPage();
          return;
        }
        const next = !railOpen();
        railOpen.set(next);
        localStorage.set(RAIL_OPEN_STORAGE_KEY, next);
      },
    },
    density: {
      gap: gridDensity.gap,
      rowGap: GRID_GAP,
      columns: gridDensity.columns,
      minItemWidth: computed(() => isMobile() ? 1 : Math.round(density.baseWidth * density.scale.scaleFactor())),
      smallerDisabled: gridDensity.smallerDisabled,
      largerDisabled: gridDensity.largerDisabled,
      adjust(direction: GridDensityDirection): void {
        const grid = view?.densityGrid();
        if (grid) {
          gridDensity.adjust(direction, grid);
        }
      },
    },

    toggleMobileSelect(): void {
      if (mobileSelectMode()) {
        selection.clear();
      }
      mobileSelectMode.update(active => !active);
    },

    shuffle(): void {
      shuffleGeneration.update(generation => generation + 1);
      void source.restartInfinitePage(params());
    },

    toggleFacet(toggle: BrowseFilterToggle<FacetKey>): void {
      urlState.setFacets(setBrowseFacetValue(
        urlState.facets(),
        toggle.key,
        toggle.value,
        toggle.state,
        filterSettings.matchAll(),
      ));
    },

    commitFacetRange(commit: BrowseFilterRangeCommit<FacetKey>): void {
      urlState.setFacetRange(queries.withRange(urlState.facets(), commit, filterSettings.matchAll()));
    },

    clearQuery,

    clearAllFilters(): void {
      searchDraft.set('');
      urlState.clearFilters();
    },

    attachView(next: BrowsePageView): () => void {
      view = next;
      return () => {
        if (view === next) {
          view = null;
        }
      };
    },
  };
}
