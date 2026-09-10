import {computed, inject, type Signal} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
import {TranslocoService} from '@jsverse/transloco';
import {injectQuery} from '@tanstack/angular-query-experimental';

import {normalizeRemoteSearchTerm} from '../../../shared/util/search-terms';
import {MagicShelfService} from '../../magic-shelf/service/magic-shelf.service';
import {
  EMPTY_FACET_SELECTION,
  type BookCollectionFilterParams,
  type BookQueryFacetKey,
  type FacetValueMap,
} from '../data/book-query-params';
import {type BrowseFacetResult} from '../../../core/data/browse.models';
import {BookQueryService} from '../data/book-query.service';
import {ShelfDefinitionQueryService} from '../data/shelf-definition-query.service';
import {LibraryService} from '../service/library.service';
import {UserService} from '../../settings/user-management/user.service';
import {type LibraryShelfMenuTarget} from '../../../shared/layout/navigation/library-shelf-menu-target.model';
import {libraryShelfMenuAvailable} from '../components/library-shelf-menu/library-shelf-menu-items.component';
import {
  browseFilterGroups,
  browseFilterChips,
  browseFrozenFacetOrders,
  type BrowseFacetDefinitions,
  type BrowseFilterChip,
  type BrowseFilterGroup,
  type BrowseFrozenFacetOrders,
} from '../../../shared/browse/facets';
import {bookFacetDefinitions, bookFacetLabelDeps} from './book-browse-facet-definitions';
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
    facetLogic: 'or',
    query: normalizeRemoteSearchTerm(query()) || undefined,
  }));
  const scopeFacetsQuery = injectQuery(() => bookQuery.facets({
    facets: scopedFacetSelection(EMPTY_FACET_SELECTION, scope()),
    facetLogic: 'or',
  }));
  const facetsQuery = injectQuery(() => ({
    ...bookQuery.facets(collectionParams()),
    enabled: enabled?.() ?? true,
    placeholderData: (previous: BrowseFacetResult | undefined) => previous,
  }));
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
  const frozen = computed<BrowseFrozenFacetOrders | undefined>(() => {
    const data = scopeFacetsQuery.data();
    return data ? browseFrozenFacetOrders(data.facets, definitions()) : undefined;
  });
  const railGroups = computed<BrowseFilterGroup<BookQueryFacetKey>[]>(() =>
    browseFilterGroups(facetsQuery.data()?.facets ?? [], frozen(), definitions(), selection()));
  const chips = computed<BrowseFilterChip<BookQueryFacetKey>[]>(() =>
    browseFilterChips(scopeFacetsQuery.data()?.facets ?? [], frozen(), definitions(), selection()));
  const title = computed(() => {
    activeLang();
    return bookBrowseScopeTitle(scope(), libraryService.libraries(), shelfDefinitions(), magicShelfService.shelves(), {
      allBooks: transloco.translate('book.browser.labels.allBooks'),
      unshelved: transloco.translate('book.browser.labels.unshelvedBooks'),
    });
  });
  const searchHint = computed(() => {
    activeLang();
    return transloco.translate('browse.rail.search', {scope: title()});
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
    sortTokens: computed<readonly string[]>(() => scopeFacetsQuery.data()?.sortTokens ?? []),
    pending: computed(() => facetsQuery.isPending()),
    chips,
    railGroups,
    title,
    searchHint,
    actionTarget,
  };
}
