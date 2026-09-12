import {bookGrimmoryProgress, bookProgressPercentage} from '../book/data/book-actions';
import {bookSortTermsFromCriteria} from '../book/browse/book-browse-sort';
import {
  type BookPageParams,
  type BookSortTerm,
  DEFAULT_BOOK_SORT_TERMS,
} from '../book/data/book-query-params';
import {
  BOOK_FILE_TYPES,
  type BookFileResponse,
  type BookFileType,
  type BookSummary,
  type KnownBookReadStatus,
} from '../book/data/book-response.models';
import {DEFAULT_MAX_ITEMS, type ScrollerConfig, ScrollerType} from './models/dashboard-config.model';

const IN_PROGRESS_STATUSES: readonly KnownBookReadStatus[] = ['READING', 'RE_READING', 'PAUSED'];
const RANDOM_EXCLUDED_STATUSES: readonly KnownBookReadStatus[] = [
  'READ',
  'PARTIALLY_READ',
  'READING',
  'PAUSED',
  'WONT_READ',
  'ABANDONED',
];
const EBOOK_FILE_TYPES: readonly BookFileType[] = BOOK_FILE_TYPES.filter(type => type !== 'AUDIOBOOK');
const AUDIOBOOK_FILE_TYPES: readonly BookFileType[] = ['AUDIOBOOK'];
const PROGRESS_QUERY_SIZE_MULTIPLIER = 2;

interface DashboardRowBook {
  readonly book: BookSummary;
  readonly file?: BookFileResponse;
}

function progressFileTypes(type: ScrollerType): readonly BookFileType[] | null {
  switch (type) {
    case ScrollerType.LAST_READ:
      return EBOOK_FILE_TYPES;
    case ScrollerType.LAST_LISTENED:
      return AUDIOBOOK_FILE_TYPES;
    default:
      return null;
  }
}

export function dashboardRowQueryParams(config: ScrollerConfig): BookPageParams | null {
  const size = rowSize(config);
  const fileTypes = progressFileTypes(config.type);
  if (fileTypes !== null) {
    return inProgressParams(fileTypes, size);
  }

  switch (config.type) {
    case ScrollerType.LATEST_ADDED:
      return {
        facets: {},
        facetLogic: 'or',
        sort: [{key: 'addedOn', direction: 'desc'}],
        size,
      };
    case ScrollerType.RANDOM:
      return {
        facets: {read_status: RANDOM_EXCLUDED_STATUSES},
        facetLogic: 'not',
        sort: [{key: 'random', direction: 'asc'}],
        size,
      };
    case ScrollerType.MAGIC_SHELF:
      if (config.magicShelfId == null) {
        return null;
      }
      return {
        facets: {shelf: [`magic:${config.magicShelfId}`]},
        facetLogic: 'or',
        sort: magicShelfSort(config),
        size,
      };
    default:
      return null;
  }
}

export function dashboardRowBooks(
  config: ScrollerConfig,
  books: readonly BookSummary[],
): readonly DashboardRowBook[] {
  const fileTypes = progressFileTypes(config.type);
  if (fileTypes === null) {
    return books.map(book => ({book}));
  }

  return books
    .flatMap(book => {
      const file = startedFile(book, fileTypes);
      return file ? [{book, file}] : [];
    })
    .slice(0, rowSize(config));
}

function startedFile(book: BookSummary, fileTypes: readonly string[]): BookFileResponse | undefined {
  const files = [book.primaryFile, ...(book.alternativeFormats ?? [])]
    .filter(file => file?.bookType != null && fileTypes.includes(file.bookType));

  return files.find(file => bookGrimmoryProgress(book, file) !== null)
    ?? files.find(file => bookProgressPercentage(book, file) !== null);
}

function rowSize(config: ScrollerConfig): number {
  return config.maxItems || DEFAULT_MAX_ITEMS;
}

function inProgressParams(fileTypes: readonly BookFileType[], rowSize: number): BookPageParams {
  return {
    facets: {read_status: IN_PROGRESS_STATUSES, file_type: fileTypes},
    facetLogic: 'or',
    sort: [{key: 'lastReadTime', direction: 'desc'}],
    size: rowSize * PROGRESS_QUERY_SIZE_MULTIPLIER,
  };
}

function magicShelfSort(config: ScrollerConfig): readonly BookSortTerm[] {
  if (!config.sortField) {
    return DEFAULT_BOOK_SORT_TERMS;
  }

  const terms = bookSortTermsFromCriteria([{
    field: config.sortField,
    direction: config.sortDirection === 'desc' ? 'DESC' : 'ASC',
  }]);
  return terms.length > 0 ? terms : DEFAULT_BOOK_SORT_TERMS;
}
