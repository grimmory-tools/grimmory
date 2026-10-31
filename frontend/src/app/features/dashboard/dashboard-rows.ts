import {inject} from '@angular/core';
import {RedirectCommand, Router, type Params, type ResolveFn} from '@angular/router';

import {
  type BookBrowseScope,
  libraryBrowseScope,
  magicShelfBrowseScope,
  shelfBrowseScope,
} from '../book/browse/book-browse-scope';
import {type BookRowCard} from '../book/components/book-row/book-row.component';
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

const DASHBOARD_ROW_SIZE = 24;

const IN_PROGRESS_STATUSES: readonly KnownBookReadStatus[] = ['READING', 'RE_READING', 'PAUSED'];
const DISCOVER_EXCLUDED_STATUSES: readonly KnownBookReadStatus[] =
  ['READ', 'PARTIALLY_READ', 'READING', 'PAUSED', 'WONT_READ', 'ABANDONED'];
const EBOOK_FILE_TYPES: readonly BookFileType[] = BOOK_FILE_TYPES.filter(type => type !== 'AUDIOBOOK');
const AUDIOBOOK_FILE_TYPES: readonly BookFileType[] = ['AUDIOBOOK'];

interface DashboardRowLink {
  readonly route: readonly (string | number)[];
  readonly queryParams: Params | null;
}

export interface DashboardRow {
  readonly id: string;
  readonly link: DashboardRowLink | null;
  readonly scope: BookBrowseScope;
  readonly sort: readonly BookSortTerm[];
  readonly querySize: number;
  readonly cards: (books: readonly BookSummary[]) => BookRowCard[];
}

interface DashboardRowEntity {
  readonly field: 'libraryId' | 'shelfId' | 'magicShelfId';
  readonly route: string;
  readonly scope: (id: number) => BookBrowseScope;
}

interface DashboardRowType {
  readonly type: ScrollerType;
  readonly titleKey: string;
  readonly entity: DashboardRowEntity | null;
  readonly build: (scroller: ScrollerConfig) => DashboardRow | null;
}

const LIBRARY_ENTITY: DashboardRowEntity = {field: 'libraryId', route: '/library', scope: libraryBrowseScope};
const SHELF_ENTITY: DashboardRowEntity = {field: 'shelfId', route: '/shelf', scope: shelfBrowseScope};
const MAGIC_SHELF_ENTITY: DashboardRowEntity = {field: 'magicShelfId', route: '/magic-shelf', scope: magicShelfBrowseScope};

export const DASHBOARD_ROW_TYPES: readonly DashboardRowType[] = [
  presetRowType(ScrollerType.LAST_READ, (id, titleKey) => continueRow(id, titleKey, EBOOK_FILE_TYPES)),
  presetRowType(ScrollerType.LAST_LISTENED, (id, titleKey) => continueRow(id, titleKey, AUDIOBOOK_FILE_TYPES)),
  presetRowType(ScrollerType.LATEST_ADDED, (id, titleKey) =>
    presetRow(id, titleKey, [{key: 'addedOn', direction: 'desc'}], {})),
  presetRowType(ScrollerType.RECENTLY_FINISHED, (id, titleKey) =>
    presetRow(id, titleKey, [{key: 'dateFinished', direction: 'desc'}], {read_status: {include: ['READ'], exclude: []}})),
  presetRowType(ScrollerType.RANDOM, (id, titleKey) =>
    presetRow(id, titleKey, [{key: 'random', direction: 'asc'}], {read_status: {include: [], exclude: DISCOVER_EXCLUDED_STATUSES}})),
  entityRowType(ScrollerType.LIBRARY, LIBRARY_ENTITY),
  entityRowType(ScrollerType.SHELF, SHELF_ENTITY),
  entityRowType(ScrollerType.MAGIC_SHELF, MAGIC_SHELF_ENTITY),
];

export function dashboardRowType(type: ScrollerType): DashboardRowType | undefined {
  return DASHBOARD_ROW_TYPES.find(rowType => rowType.type === type);
}

export const dashboardRowScopeResolver: ResolveFn<BookBrowseScope> = route => {
  const rowId = route.paramMap.get('rowId');
  const row = dashboardRows(inject(UserService).currentUser()?.userSettings.dashboardConfig)
    .find(candidate => candidate.id === rowId);
  return row?.link ? row.scope : new RedirectCommand(inject(Router).parseUrl('/dashboard'));
};

export function dashboardRows(saved: DashboardConfig | undefined): DashboardRow[] {
  return dashboardConfigOrDefault(saved).scrollers
    .filter(scroller => scroller.enabled)
    .flatMap(scroller => dashboardRowType(scroller.type)?.build(scroller) ?? []);
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

function rowTypeTitleKey(type: ScrollerType): string {
  return `settingsView.dashboardScrollers.scrollerTypes.${type}`;
}

function presetRowType(type: ScrollerType, build: (id: string, titleKey: string) => DashboardRow): DashboardRowType {
  const titleKey = rowTypeTitleKey(type);
  return {type, titleKey, entity: null, build: scroller => build(scroller.id, titleKey)};
}

function entityRowType(type: ScrollerType, entity: DashboardRowEntity): DashboardRowType {
  return {type, titleKey: rowTypeTitleKey(type), entity, build: scroller => entityRow(scroller, entity)};
}

function bookCards(books: readonly BookSummary[]): BookRowCard[] {
  return books.map(book => ({book}));
}

function continueRow(id: string, titleKey: string, formats: readonly BookFileType[]): DashboardRow {
  return {
    ...presetRow(id, titleKey, [{key: 'lastReadTime', direction: 'desc'}],
      {read_status: {include: IN_PROGRESS_STATUSES, exclude: []}, file_type: {include: formats, exclude: []}}),
    link: null,
    querySize: DASHBOARD_ROW_SIZE * 2,
    cards: books => books.flatMap(book => {
      const file = dashboardContinueFile(book, formats);
      return file ? [{book, file}] : [];
    }).slice(0, DASHBOARD_ROW_SIZE),
  };
}

function presetRow(id: string, titleKey: string, sort: readonly BookSortTerm[], facets: FacetValueMap): DashboardRow {
  return {
    id,
    link: {route: ['/dashboard', id, 'books'], queryParams: {sort: sortTermsToken(sort)}},
    scope: {kind: 'dashboardRow', titleKey, facets},
    sort,
    querySize: DASHBOARD_ROW_SIZE,
    cards: bookCards,
  };
}

function entityRow(scroller: ScrollerConfig, entity: DashboardRowEntity): DashboardRow | null {
  const id = scroller[entity.field];
  if (id == null) {
    return null;
  }
  const sort = scroller.sortField
    ? bookSortTermsFromCriteria([{field: scroller.sortField, direction: scroller.sortDirection === 'desc' ? 'DESC' : 'ASC'}])
    : [];
  return {
    id: scroller.id,
    link: {route: [entity.route, id, 'books'], queryParams: sort.length > 0 ? {sort: sortTermsToken(sort)} : null},
    scope: entity.scope(id),
    sort: sort.length > 0 ? sort : DEFAULT_BOOK_SORT_TERMS,
    querySize: DASHBOARD_ROW_SIZE,
    cards: bookCards,
  };
}
