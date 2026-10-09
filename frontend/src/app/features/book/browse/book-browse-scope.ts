import {inject} from '@angular/core';
import {Router, type CanActivateChildFn, type ParamMap} from '@angular/router';

import {browseFacetValues, withBrowseFacetValues, type BrowseFacetKey} from '../../../shared/browse/facets';
import {type LibraryShelfMenuTarget} from '../../../shared/layout/navigation/library-shelf-menu-target.model';
import {type PageHeaderBreadcrumb} from '../../../shared/layout/page-header/page-header.service';
import {type EntityViewPreferenceContext} from '../../settings/user-management/entity-view-preferences';
import {type BookQueryFacetKey, type FacetValueMap} from '../data/book-query-params';

export type BookBrowseScope = {readonly facets: FacetValueMap} & (
  | {readonly kind: 'library'; readonly entityId: number}
  | {readonly kind: 'shelf'; readonly entityId: number}
  | {readonly kind: 'magicShelf'; readonly entityId: number}
  | {readonly kind: 'unshelved'}
  | {readonly kind: 'dashboardRow'; readonly rowId: string; readonly titleKey: string}
);

export interface BookBrowseRouteData {
  browseScope?: BookBrowseScope;
}

export const validBookBrowseScope: CanActivateChildFn = route => {
  const invalid = ['libraryId', 'shelfId', 'magicShelfId'].some(key =>
    route.paramMap.has(key) && positiveId(route.paramMap.get(key)) === null);
  return invalid ? inject(Router).createUrlTree(['/dashboard']) : true;
};

export const UNSHELVED_BROWSE_SCOPE: BookBrowseScope =
  {kind: 'unshelved', facets: {'+shelf_status': ['unshelved']}};

export function libraryBrowseScope(entityId: number): BookBrowseScope {
  return {kind: 'library', entityId, facets: {'+library': [`${entityId}`]}};
}

export function shelfBrowseScope(entityId: number): BookBrowseScope {
  return {kind: 'shelf', entityId, facets: {'+shelf': [`${entityId}`]}};
}

export function magicShelfBrowseScope(entityId: number): BookBrowseScope {
  return {kind: 'magicShelf', entityId, facets: {'+shelf': [`magic:${entityId}`]}};
}

export function bookBrowseScope(
  paramMap: ParamMap,
  routeData: BookBrowseRouteData,
): BookBrowseScope | null {
  const libraryId = positiveId(paramMap.get('libraryId'));
  if (libraryId !== null) {
    return libraryBrowseScope(libraryId);
  }

  const shelfId = positiveId(paramMap.get('shelfId'));
  if (shelfId !== null) {
    return shelfBrowseScope(shelfId);
  }

  const magicShelfId = positiveId(paramMap.get('magicShelfId'));
  if (magicShelfId !== null) {
    return magicShelfBrowseScope(magicShelfId);
  }

  return routeData.browseScope ?? null;
}

export function scopedFacetSelection(
  selection: FacetValueMap,
  scope: BookBrowseScope | null,
): FacetValueMap {
  if (!scope) {
    return selection;
  }
  const fixed = bookBrowseScopeFixedFacets(scope);
  let next = selection;
  for (const [key, values] of Object.entries(scope.facets) as [BrowseFacetKey<BookQueryFacetKey>, readonly string[]][]) {
    next = withBrowseFacetValues(next, key, fixed.has(key) ? values : [...new Set([...browseFacetValues(next, key), ...values])]);
  }
  return next;
}

export function bookBrowseScopeFixedFacets(scope: BookBrowseScope | null): ReadonlySet<string> {
  return new Set(Object.keys(scope?.facets ?? {}).filter(key => !key.startsWith('+') && !key.startsWith('-')));
}

export function bookBrowseScopeTitle(
  scope: BookBrowseScope | null,
  libraries: readonly {id?: number | null; name: string}[],
  shelves: readonly {id?: number | null; name: string}[],
  magicShelves: readonly {id?: number | null; name: string}[],
  translate: (key: string) => string,
): string {
  switch (scope?.kind) {
    case 'library':
      return libraries.find(library => library.id === scope.entityId)?.name ?? '';
    case 'shelf':
      return shelves.find(shelf => shelf.id === scope.entityId)?.name ?? '';
    case 'magicShelf':
      return magicShelves.find(shelf => shelf.id === scope.entityId)?.name ?? '';
    case 'unshelved':
      return translate('book.browser.labels.unshelvedBooks');
    case 'dashboardRow':
      return translate(scope.titleKey);
    case undefined:
      return translate('book.browser.labels.allBooks');
  }
}

export function bookBrowseScopeBreadcrumbs(
  scope: BookBrowseScope | null,
  translate: (key: string) => string,
): PageHeaderBreadcrumb[] {
  return scope?.kind === 'dashboardRow' ? [{label: translate('dashboard.main.pageTitle'), commands: ['/dashboard']}] : [];
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
    case 'dashboardRow':
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
    case 'dashboardRow':
    case undefined:
      return null;
  }
}

function positiveId(raw: string | null): number | null {
  const id = Number(raw);
  return raw !== null && Number.isSafeInteger(id) && id > 0 ? id : null;
}
