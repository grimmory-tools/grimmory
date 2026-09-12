import {type LucideIconData} from '@lucide/angular';

import {
  browseSortDirectionIcon,
  browseSortOptions,
  browseSortTermsFromCriteria,
  type BrowseSortCriterion,
  type BrowseSortField,
  type BrowseSortOption,
  type BrowseSortDefinitions,
} from '../../../shared/browse/sort';
import {
  BOOK_QUERY_SORT_KEYS,
  isBookQuerySortKey,
  parseSortTermsToken,
  type BookQuerySortKey,
  type BookSortTerm,
  type SortDirection,
} from '../data/book-query-params';
import {SORT_FIELDS, type BookDetailLineSortKey} from './book-browse-fields';

export type BookSortOption = BrowseSortOption<BookQuerySortKey>;

export function bookSortField(key: BookQuerySortKey): BrowseSortField {
  const {labelKey, sort} = SORT_FIELDS.get(key)!;
  return {
    labelKey,
    group: sort.group,
    defaultDirection: sort.defaultDirection,
    kind: sort.kind,
    directions: sort.directions,
  };
}

const BOOK_SORT_DEFINITIONS: BrowseSortDefinitions<BookQuerySortKey> = {
  order: BOOK_QUERY_SORT_KEYS,
  field: bookSortField,
  parseToken: parseSortTermsToken,
};

export function bookSortOptions(serverSortTokens: readonly string[]): BookSortOption[] {
  return browseSortOptions(serverSortTokens, BOOK_SORT_DEFINITIONS);
}

const LEGACY_SORT_KEYS: Readonly<Record<string, BookQuerySortKey>> = {
  author: 'authorName',
  authorSurnameVorname: 'authorSortName',
};

export function bookSortTermsFromCriteria(criteria: readonly BrowseSortCriterion[]): BookSortTerm[] {
  return browseSortTermsFromCriteria(
    criteria.map(criterion => ({...criterion, field: LEGACY_SORT_KEYS[criterion.field] ?? criterion.field})),
    isBookQuerySortKey,
  );
}

export function bookSortDirectionIcon(
  key: BookQuerySortKey,
  direction: SortDirection,
): LucideIconData {
  return browseSortDirectionIcon(bookSortField(key).kind, direction);
}

export function bookSortHasDetailLine(key: BookQuerySortKey): key is BookDetailLineSortKey {
  const field = SORT_FIELDS.get(key);
  return field !== undefined && field.sort.detailLine !== false;
}
