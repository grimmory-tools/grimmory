import {computed, inject, linkedSignal, signal, type Signal} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
import {TranslocoService} from '@jsverse/transloco';
import {injectQuery, keepPreviousData} from '@tanstack/angular-query-experimental';

import {debouncedSignal} from '../../../shared/util/debounced-signal';
import {heldSignal} from '../../../shared/util/held-signal';
import {normalizeRemoteSearchTerm, SEARCH_DEBOUNCE_MS} from '../../../shared/util/search-terms';
import {MagicShelfService} from '../../magic-shelf/service/magic-shelf.service';
import {
  BOOK_QUERY_FACET_KEYS,
  EMPTY_FACET_SELECTION,
  type BookCollectionFilterParams,
  type BookQueryFacetKey,
  type FacetValueMap,
} from '../data/book-query-params';
import {BookQueryService} from '../data/book-query.service';
import {ShelfDefinitionQueryService} from '../data/shelf-definition-query.service';
import {LibraryService} from '../service/library.service';
import {UserService} from '../../settings/user-management/user.service';
import {type LibraryShelfMenuTarget} from '../../../shared/layout/navigation/library-shelf-menu-target.model';
import {libraryShelfMenuAvailable} from '../components/library-shelf-menu/library-shelf-menu-items.component';
import {
  browseFilterGroups,
  browseFilterChips,
  browseFacetPicks,
  hasBrowseFacetValues,
  withBrowseFacetRange,
  type BrowseFacetDefinitions,
  type BrowseFilterChip,
  type BrowseFilterGroup,
  type BrowseFilterOpen,
  type BrowseFilterRangeCommit,
  type BrowseFilterSearch,
} from '../../../shared/browse/facets';
import {bookFacetDefinitions, bookFacetLabelDeps} from './book-browse-facet-definitions';
import {FACET_FIELDS, OPEN_RAIL_FACETS} from './book-browse-fields';
import {
  bookBrowseScopeMenuTarget,
  bookBrowseScopeTitle,
  scopedFacetSelection,
  type BookBrowseScope,
} from './book-browse-scope';

export interface BookBrowseQueriesOptions {
  readonly selection: Signal<FacetValueMap>;
  readonly query: Signal<string>;
  readonly scope: Signal<BookBrowseScope | null>;
  readonly enabled?: Signal<boolean>;
}

export function createBookBrowseQueries({selection, query, scope, enabled}: BookBrowseQueriesOptions) {
  const bookQuery = inject(BookQueryService);
  const transloco = inject(TranslocoService);
  const libraryService = inject(LibraryService);
  const magicShelfService = inject(MagicShelfService);
  const shelfDefinitionQuery = inject(ShelfDefinitionQueryService);
  const userService = inject(UserService);
  const activeLang = toSignal(transloco.langChanges$, {initialValue: transloco.getActiveLang()});

  const collectionParams = computed<BookCollectionFilterParams>(() => ({
    facets: scopedFacetSelection(selection(), scope()),
    query: normalizeRemoteSearchTerm(query()) || undefined,
  }));
  const scopeParams = computed<BookCollectionFilterParams>(() => ({
    facets: scopedFacetSelection(EMPTY_FACET_SELECTION, scope()),
  }));
  const isEnabled = () => enabled?.() ?? true;

  const indexQuery = injectQuery(() => bookQuery.facetIndex(scopeParams()));
  const available = computed<ReadonlySet<string>>(() => new Set(indexQuery.data()?.facetKeys));

  const selectedKeys = BOOK_QUERY_FACET_KEYS.filter(key => hasBrowseFacetValues(selection(), key));
  const openKeys = signal<ReadonlySet<BookQueryFacetKey>>(new Set([...OPEN_RAIL_FACETS, ...selectedKeys]));
  const searchTerms = signal<Readonly<Partial<Record<BookQueryFacetKey, string>>>>({});
  const debouncedSearchTerms = debouncedSignal(searchTerms, SEARCH_DEBOUNCE_MS);

  function facetQueries(key: BookQueryFacetKey) {
    const open = computed(() => isEnabled() && openKeys().has(key) && available().has(key));
    const searchTerm = computed(() => (debouncedSearchTerms()[key] ?? '').trim());

    const list = injectQuery(() => ({
      ...bookQuery.facet(key, collectionParams()),
      enabled: open(),
      placeholderData: keepPreviousData,
    }));

    const searchOnServer = computed(() => searchTerm() !== '' && list.data()?.complete === false);
    const search = injectQuery(() => ({
      ...bookQuery.facet(key, collectionParams(), searchTerm()),
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

  const facets = [...FACET_FIELDS.keys()].map(facetQueries);

  const served = computed(() => facets.flatMap(facet => facet.served() ?? []));
  const stale = computed(() => facets.some(facet => facet.stale()));
  const loadingKeys = computed(() => new Set(facets.filter(facet => facet.loading()).map(facet => facet.key)));
  const railReady = linkedSignal<boolean, boolean>({
    source: () => isEnabled() && !indexQuery.isPending() && loadingKeys().size === 0,
    computation: (loaded, previous) => loaded || (previous?.value ?? false),
  });

  const shelfDefinitionsQuery = injectQuery(() => shelfDefinitionQuery.definitions());
  const shelfDefinitions = computed(() => shelfDefinitionsQuery.data() ?? []);

  const definitions = computed<BrowseFacetDefinitions<BookQueryFacetKey>>(() => {
    activeLang();
    return bookFacetDefinitions(bookFacetLabelDeps(
      shelfDefinitions(),
      libraryService.libraries(),
      key => transloco.translate(key),
    ));
  });
  const frame = heldSignal(
    () => browseFilterGroups(available(), served(), definitions(), selection()),
    stale,
  );
  const railGroups = computed<BrowseFilterGroup<BookQueryFacetKey>[]>(() =>
    frame().map(group => ({
      ...group,
      picks: browseFacetPicks(selection(), group.key),
      loading: loadingKeys().has(group.key),
    })));
  const chips = computed<BrowseFilterChip<BookQueryFacetKey>[]>(() =>
    browseFilterChips(served(), definitions(), selection()));
  const title = computed(() => {
    activeLang();
    return bookBrowseScopeTitle(scope(), libraryService.libraries(), shelfDefinitions(), magicShelfService.shelves(), {
      allBooks: transloco.translate('book.browser.labels.allBooks'),
      unshelved: transloco.translate('book.browser.labels.unshelvedBooks'),
    });
  });
  const searchHint = computed(() => {
    activeLang();
    return transloco.translate(`browse.rail.searchScope.${scope()?.kind ?? 'allBooks'}`);
  });
  const actionTarget = computed<LibraryShelfMenuTarget | null>(() => {
    const target = bookBrowseScopeMenuTarget(
      scope(),
      libraryService.libraries(),
      shelfDefinitions(),
      magicShelfService.shelves(),
    );
    return target && libraryShelfMenuAvailable(target, userService.currentUser()) ? target : null;
  });

  return {
    collectionParams,
    definitions,
    sortTokens: computed<readonly string[]>(() => indexQuery.data()?.sortTokens ?? []),
    pending: computed(() => !railReady()),
    chips,
    railGroups,
    openKeys: openKeys.asReadonly(),
    searchTerms: searchTerms.asReadonly(),
    title,
    searchHint,
    actionTarget,
    setOpen: ({key, open}: BrowseFilterOpen<BookQueryFacetKey>) => openKeys.update(keys =>
      open ? new Set([...keys, key]) : new Set([...keys].filter(openKey => openKey !== key))),
    setSearch: ({key, term}: BrowseFilterSearch<BookQueryFacetKey>) =>
      searchTerms.update(terms => ({...terms, [key]: term})),
    withRange: (current: FacetValueMap, {key, min, max}: BrowseFilterRangeCommit<BookQueryFacetKey>, matchAll: boolean) => {
      const bands = served().find(group => group.key === key)?.values ?? [];
      return withBrowseFacetRange(current, key, min, max, new Set(bands.map(value => value.value)), matchAll);
    },
  };
}
