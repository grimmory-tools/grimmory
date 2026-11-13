import {computed, inject, type Signal} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
import {TranslocoService} from '@jsverse/transloco';
import {injectQuery} from '@tanstack/angular-query-experimental';

import {browseFacetEntityLookup} from '../../../shared/browse/entity-facets';
import {type BrowseFacetDefinitions} from '../../../shared/browse/facets';
import {type LibraryShelfMenuTarget} from '../../../shared/layout/navigation/library-shelf-menu-target.model';
import {MagicShelfService} from '../../magic-shelf/service/magic-shelf.service';
import {UserService} from '../../settings/user-management/user.service';
import {libraryShelfMenuAvailable} from '../components/library-shelf-menu/library-shelf-menu-items.component';
import {type BookQueryFacetKey} from '../data/book-query-params';
import {ShelfDefinitionQueryService} from '../data/shelf-definition-query.service';
import {LibraryService} from '../service/library.service';
import {BOOK_FIELDS} from './book-browse-fields';
import {bookBrowseScopeMenuTarget, bookBrowseScopeTitle, type BookBrowseScope} from './book-browse-scope';

export function createBookBrowseScopeTitle(scope: () => BookBrowseScope | null): Signal<string> {
  const transloco = inject(TranslocoService);
  const libraryService = inject(LibraryService);
  const magicShelfService = inject(MagicShelfService);
  const shelfDefinitionQuery = inject(ShelfDefinitionQueryService);
  const shelvesQuery = injectQuery(() => shelfDefinitionQuery.definitions());
  const activeLang = toSignal(transloco.langChanges$, {initialValue: transloco.getActiveLang()});
  return computed(() => {
    activeLang();
    return bookBrowseScopeTitle(scope(), libraryService.libraries(), shelvesQuery.data() ?? [],
      magicShelfService.shelves(), key => transloco.translate(key));
  });
}

export function createBookBrowseLabels(scope: Signal<BookBrowseScope | null>) {
  const transloco = inject(TranslocoService);
  const libraryService = inject(LibraryService);
  const magicShelfService = inject(MagicShelfService);
  const shelfDefinitionQuery = inject(ShelfDefinitionQueryService);
  const userService = inject(UserService);
  const activeLang = toSignal(transloco.langChanges$, {initialValue: transloco.getActiveLang()});

  const shelfDefinitionsQuery = injectQuery(() => shelfDefinitionQuery.definitions());
  const shelfDefinitions = computed(() => shelfDefinitionsQuery.data() ?? []);

  return {
    facetDefinitions: computed<BrowseFacetDefinitions<BookQueryFacetKey>>(() => {
      activeLang();
      return BOOK_FIELDS.facetDefinitions({
        shelf: browseFacetEntityLookup([
          ...shelfDefinitions(),
          ...magicShelfService.shelves().map(shelf => ({...shelf, id: `magic:${shelf.id}`})),
        ]),
        library: browseFacetEntityLookup(libraryService.libraries()),
        translate: key => transloco.translate(key),
      });
    }),
    title: createBookBrowseScopeTitle(scope),
    searchHintKey: computed(() => `browse.rail.searchScope.${scope()?.kind ?? 'allBooks'}`),
    actionTarget: computed<LibraryShelfMenuTarget | null>(() => {
      const target = bookBrowseScopeMenuTarget(
        scope(),
        libraryService.libraries(),
        shelfDefinitions(),
        magicShelfService.shelves(),
      );
      return target && libraryShelfMenuAvailable(target, userService.currentUser()) ? target : null;
    }),
  };
}
