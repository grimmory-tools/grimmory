import {
  browseSortOptions,
  browseSortTermsFromCriteria,
  type BrowseSortCriterion,
  type BrowseSortOption,
} from '../../../shared/browse/sort';
import {
  isBookQuerySortKey,
  type BookQuerySortKey,
  type BookSortTerm,
} from '../data/book-query-params';
import {BOOK_FIELDS, type BookDetailLineSortKey} from './book-browse-fields';

export type BookSortOption = BrowseSortOption<BookQuerySortKey>;

export function bookSortOptions(serverSortTokens: readonly string[]): BookSortOption[] {
  return browseSortOptions(serverSortTokens, BOOK_FIELDS.sortDefinitions);
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
