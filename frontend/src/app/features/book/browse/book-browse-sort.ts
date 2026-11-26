import {
  browseSortOptions,
  browseSortTermsFromCriteria,
  type BrowseSortCriterion,
  type BrowseSortOption,
} from '../../../shared/browse/sort';
import {
  BOOK_QUERY_SORT_KEYS,
  isBookQuerySortKey,
  type BookQuerySortKey,
  type BookSortTerm,
} from '../data/book-query-params';
import {BOOK_FIELDS, type BookDetailLineSortKey} from './book-browse-fields';

export type BookSortOption = BrowseSortOption<BookQuerySortKey>;

export interface BookCardDetailOption {
  readonly id: BookDetailLineSortKey | null;
  readonly labelKey: string;
}

export function bookSortOptions(serverSortTokens: readonly string[], hidden: ReadonlySet<string>): BookSortOption[] {
  return browseSortOptions(serverSortTokens, BOOK_FIELDS.sortDefinitions).filter(option => !hidden.has(option.id));
}

const LEGACY_SORT_KEYS = new Map<string, BookQuerySortKey>([
  ['author', 'authorName'],
  ['authorSurnameVorname', 'authorSortName'],
]);

export function bookSortTermsFromCriteria(criteria: readonly BrowseSortCriterion[]): BookSortTerm[] {
  return browseSortTermsFromCriteria(
    criteria.map(criterion => ({...criterion, field: LEGACY_SORT_KEYS.get(criterion.field) ?? criterion.field})),
    isBookQuerySortKey,
  );
}

export function bookSortHasDetailLine(key: BookQuerySortKey): key is BookDetailLineSortKey {
  return BOOK_FIELDS.fieldForSort(key).sort.detailLine !== false;
}

const BOOK_CARD_DETAIL_OPTIONS: readonly BookCardDetailOption[] = [
  {id: null, labelKey: 'book.cardDetail.none'},
  ...BOOK_QUERY_SORT_KEYS
    .filter(bookSortHasDetailLine)
    .map(id => ({id, labelKey: BOOK_FIELDS.sortField(id).labelKey})),
];

export function bookCardDetailOptions(hidden: ReadonlySet<string>): readonly BookCardDetailOption[] {
  return BOOK_CARD_DETAIL_OPTIONS.filter(option => option.id === null || !hidden.has(option.id));
}
