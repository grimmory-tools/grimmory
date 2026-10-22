import {computed, inject} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
import {TranslocoService} from '@jsverse/transloco';

import {browseFacetEntityLookup} from '../../../shared/browse/entity-facets';
import {type BrowseFacetDefinitions} from '../../../shared/browse/facets';
import {LibraryService} from '../../book/service/library.service';
import {type AuthorQueryFacetKey} from '../data/author-query-params';
import {AUTHOR_FIELDS} from './author-browse-fields';

export function createAuthorBrowseLabels() {
  const transloco = inject(TranslocoService);
  const libraryService = inject(LibraryService);
  const activeLang = toSignal(transloco.langChanges$, {initialValue: transloco.getActiveLang()});

  return {
    facetDefinitions: computed<BrowseFacetDefinitions<AuthorQueryFacetKey>>(() => {
      activeLang();
      return AUTHOR_FIELDS.facetDefinitions({
        library: browseFacetEntityLookup(libraryService.libraries()),
        translate: key => transloco.translate(key),
      });
    }),
    title: computed(() => {
      activeLang();
      return transloco.translate('layout.menu.authors');
    }),
    searchHintKey: 'browse.rail.searchScope.authors',
  };
}
