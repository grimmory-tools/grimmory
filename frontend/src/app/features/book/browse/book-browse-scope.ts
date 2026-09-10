import {type ParamMap} from '@angular/router';

import {pinBrowseFacetValue} from '../../../shared/browse/facets';
import {type LibraryShelfMenuTarget} from '../../../shared/layout/navigation/library-shelf-menu-target.model';
import {type EntityViewPreferenceContext} from '../../settings/user-management/entity-view-preferences';
import {type FacetValueMap} from '../data/book-query-params';

export type BookBrowseScope =
  | {kind: 'library'; entityId: number; facetKey: 'library'; facetValue: string}
  | {kind: 'shelf'; entityId: number; facetKey: 'shelf'; facetValue: string}
  | {kind: 'magicShelf'; entityId: number; facetKey: 'shelf'; facetValue: string}
  | {kind: 'unshelved'; facetKey: 'shelf_status'; facetValue: 'unshelved'};

export interface BookBrowseRouteData {
  browseScope?: 'unshelved';
}

export function bookBrowseScope(
  paramMap: ParamMap,
  routeData: BookBrowseRouteData,
): BookBrowseScope | null {
  const libraryId = positiveId(paramMap.get('libraryId'));
  if (libraryId !== null) {
    return {kind: 'library', entityId: libraryId, facetKey: 'library', facetValue: `${libraryId}`};
  }

  const shelfId = positiveId(paramMap.get('shelfId'));
  if (shelfId !== null) {
    return {kind: 'shelf', entityId: shelfId, facetKey: 'shelf', facetValue: `${shelfId}`};
  }

  const magicShelfId = positiveId(paramMap.get('magicShelfId'));
  if (magicShelfId !== null) {
    return {
      kind: 'magicShelf',
      entityId: magicShelfId,
      facetKey: 'shelf',
      facetValue: `magic:${magicShelfId}`,
    };
  }

  if (routeData.browseScope === 'unshelved') {
    return {kind: 'unshelved', facetKey: 'shelf_status', facetValue: 'unshelved'};
  }

  return null;
}

export function scopedFacetSelection(
  selection: FacetValueMap,
  scope: BookBrowseScope | null,
): FacetValueMap {
  return scope ? pinBrowseFacetValue(selection, scope.facetKey, scope.facetValue) : selection;
}

export function bookBrowseScopeTitle(
  scope: BookBrowseScope | null,
  libraries: readonly {id?: number | null; name: string}[],
  shelves: readonly {id?: number | null; name: string}[],
  magicShelves: readonly {id?: number | null; name: string}[],
  labels: {readonly allBooks: string; readonly unshelved: string},
): string {
  switch (scope?.kind) {
    case 'library':
      return libraries.find(library => library.id === scope.entityId)?.name ?? '';
    case 'shelf':
      return shelves.find(shelf => shelf.id === scope.entityId)?.name ?? '';
    case 'magicShelf':
      return magicShelves.find(shelf => shelf.id === scope.entityId)?.name ?? '';
    case 'unshelved':
      return labels.unshelved;
    case undefined:
      return labels.allBooks;
  }
}

export function bookBrowseScopePreferenceContext(
  scope: BookBrowseScope | null,
): EntityViewPreferenceContext | null {
  switch (scope?.kind) {
    case 'library':
      return {entityType: 'LIBRARY', entityId: scope.entityId};
    case 'shelf':
      return {entityType: 'SHELF', entityId: scope.entityId};
    case 'magicShelf':
      return {entityType: 'MAGIC_SHELF', entityId: scope.entityId};
    case 'unshelved':
    case undefined:
      return null;
  }
}

export function bookBrowseScopeMenuTarget(
  scope: BookBrowseScope | null,
  libraries: readonly {id?: number | null; name: string}[],
  shelves: readonly {id: number; name: string; userId: number; publicShelf?: boolean}[],
  magicShelves: readonly {id?: number | null; name: string; filterJson: string; isPublic?: boolean}[],
): LibraryShelfMenuTarget | null {
  switch (scope?.kind) {
    case 'library': {
      const library = libraries.find(candidate => candidate.id === scope.entityId);
      return library?.id == null
        ? null
        : {type: 'library', entity: {id: library.id, name: library.name}};
    }
    case 'shelf': {
      const shelf = shelves.find(candidate => candidate.id === scope.entityId);
      return shelf
        ? {type: 'shelf', entity: {id: shelf.id, name: shelf.name, userId: shelf.userId, publicShelf: shelf.publicShelf}}
        : null;
    }
    case 'magicShelf': {
      const shelf = magicShelves.find(candidate => candidate.id === scope.entityId);
      return shelf?.id == null
        ? null
        : {
            type: 'magicShelf',
            entity: {id: shelf.id, name: shelf.name, filterJson: shelf.filterJson, isPublic: shelf.isPublic},
          };
    }
    case 'unshelved':
    case undefined:
      return null;
  }
}

function positiveId(raw: string | null): number | null {
  const id = Number(raw);
  return raw !== null && Number.isSafeInteger(id) && id > 0 ? id : null;
}
