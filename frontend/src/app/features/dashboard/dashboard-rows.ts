import {inject} from '@angular/core';
import {RedirectCommand, Router, type Params, type ResolveFn} from '@angular/router';

import {
  type BookBrowseScope,
  libraryBrowseScope,
  magicShelfBrowseScope,
  shelfBrowseScope,
} from '../book/browse/book-browse-scope';
import {bookGrimmoryProgress, bookProgressPercentage} from '../book/data/book-actions';
import {bookSortTermsFromCriteria} from '../book/browse/book-browse-sort';
import {
  type BookSortTerm,
  DEFAULT_BOOK_SORT_TERMS,
  type FacetValueMap,
  sortTermsToken,
} from '../book/data/book-query-params';
import {
  BOOK_FILE_TYPES,
  type BookFileResponse,
  type BookFileType,
  type BookSummary,
  type KnownBookReadStatus,
} from '../book/data/book-response.models';
import {UserService} from '../settings/user-management/user.service';
import {
  type DashboardConfig,
  dashboardConfigOrDefault,
  type ScrollerConfig,
  ScrollerType,
} from './models/dashboard-config.model';

export const DASHBOARD_ROW_SIZE = 24;

const IN_PROGRESS_STATUSES: readonly KnownBookReadStatus[] = ['READING', 'RE_READING', 'PAUSED'];
const DISCOVER_EXCLUDED_STATUSES: readonly KnownBookReadStatus[] =
  ['READ', 'PARTIALLY_READ', 'READING', 'PAUSED', 'WONT_READ', 'ABANDONED'];
const EBOOK_FILE_TYPES: readonly BookFileType[] = BOOK_FILE_TYPES.filter(type => type !== 'AUDIOBOOK');
const AUDIOBOOK_FILE_TYPES: readonly BookFileType[] = ['AUDIOBOOK'];

export interface DashboardRow {
  readonly id: string;
  readonly route: readonly (string | number)[];
  readonly queryParams: Params | null;
  readonly scope: BookBrowseScope;
  readonly sort: readonly BookSortTerm[];
  readonly fileTypes: readonly BookFileType[] | null;
}

export const dashboardRowScopeResolver: ResolveFn<BookBrowseScope> = route => {
  const rowId = route.paramMap.get('rowId');
  const row = dashboardRows(inject(UserService).currentUser()?.userSettings.dashboardConfig)
    .find(candidate => candidate.id === rowId);
  return row?.scope ?? new RedirectCommand(inject(Router).parseUrl('/dashboard'));
};

export function dashboardRows(saved: DashboardConfig | undefined): DashboardRow[] {
  return dashboardConfigOrDefault(saved).scrollers.filter(scroller => scroller.enabled).flatMap(scroller => scrollerRow(scroller) ?? []);
}

export function dashboardContinueFile(
  book: BookSummary,
  fileTypes: readonly BookFileType[],
): BookFileResponse | undefined {
  const files = [book.primaryFile, ...(book.alternativeFormats ?? [])].filter((file): file is BookFileResponse =>
    file?.bookType != null && fileTypes.includes(file.bookType));
  return files.find(file => bookGrimmoryProgress(book, file) !== null)
    ?? files.find(file => bookProgressPercentage(book, file) !== null);
}

function scrollerRow(scroller: ScrollerConfig): DashboardRow | null {
  const {id, libraryId, shelfId, magicShelfId} = scroller;
  switch (scroller.type) {
    case ScrollerType.LAST_READ:
      return progressRow(id, 'dashboard.scroller.continueReading', EBOOK_FILE_TYPES);
    case ScrollerType.LAST_LISTENED:
      return progressRow(id, 'dashboard.scroller.continueListening', AUDIOBOOK_FILE_TYPES);
    case ScrollerType.LATEST_ADDED:
      return presetRow(id, 'dashboard.scroller.recentlyAdded', [{key: 'addedOn', direction: 'desc'}], {});
    case ScrollerType.RECENTLY_FINISHED:
      return presetRow(id, 'dashboard.scroller.recentlyFinished', [{key: 'dateFinished', direction: 'desc'}],
        {read_status: ['READ']});
    case ScrollerType.RANDOM:
      return presetRow(id, 'dashboard.scroller.discoverNew', [{key: 'random', direction: 'asc'}],
        {'-read_status': DISCOVER_EXCLUDED_STATUSES});
    case ScrollerType.LIBRARY:
      return libraryId == null ? null : entityRow(scroller, ['/library', libraryId, 'books'], libraryBrowseScope(libraryId));
    case ScrollerType.SHELF:
      return shelfId == null ? null : entityRow(scroller, ['/shelf', shelfId, 'books'], shelfBrowseScope(shelfId));
    case ScrollerType.MAGIC_SHELF:
      return magicShelfId == null ? null : entityRow(scroller, ['/magic-shelf', magicShelfId, 'books'],
        magicShelfBrowseScope(magicShelfId));
    default:
      return null;
  }
}

function progressRow(id: string, titleKey: string, fileTypes: readonly BookFileType[]): DashboardRow {
  return {
    ...presetRow(id, titleKey, [{key: 'lastReadTime', direction: 'desc'}],
      {read_status: IN_PROGRESS_STATUSES, file_type: fileTypes}),
    fileTypes,
  };
}

function presetRow(id: string, titleKey: string, sort: readonly BookSortTerm[], facets: FacetValueMap): DashboardRow {
  return {
    id,
    route: ['/dashboard', id, 'books'],
    queryParams: {sort: sortTermsToken(sort)},
    scope: {kind: 'dashboardRow', rowId: id, titleKey, facets},
    sort,
    fileTypes: null,
  };
}

function entityRow(
  scroller: ScrollerConfig,
  route: readonly (string | number)[],
  scope: BookBrowseScope,
): DashboardRow {
  const sort = scroller.sortField
    ? bookSortTermsFromCriteria([{field: scroller.sortField, direction: scroller.sortDirection === 'desc' ? 'DESC' : 'ASC'}])
    : [];
  return {
    id: scroller.id,
    route,
    queryParams: sort.length > 0 ? {sort: sortTermsToken(sort)} : null,
    scope,
    sort: sort.length > 0 ? sort : DEFAULT_BOOK_SORT_TERMS,
    fileTypes: null,
  };
}
