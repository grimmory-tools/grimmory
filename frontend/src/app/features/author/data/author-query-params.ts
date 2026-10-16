import {type BrowseSortTerm} from '../../../core/data/browse.models';

export const AUTHOR_QUERY_FACET_KEYS = [
  'genre',
  'language',
  'library',
  'read_status',
  'book_count',
  'has_photo',
  'has_asin',
  'has_description',
] as const;

export const AUTHOR_QUERY_SORT_KEYS = [
  'name',
  'sortName',
  'bookCount',
  'seriesCount',
  'addedOn',
  'lastReadTime',
  'personalRating',
  'amazonRating',
  'goodreadsRating',
  'hardcoverRating',
  'ranobedbRating',
] as const;

export type AuthorQueryFacetKey = typeof AUTHOR_QUERY_FACET_KEYS[number];
export type AuthorQuerySortKey = typeof AUTHOR_QUERY_SORT_KEYS[number];

export const DEFAULT_AUTHOR_SORT_TERMS: readonly BrowseSortTerm<AuthorQuerySortKey>[] = [{key: 'name', direction: 'asc'}];
const AUTHOR_QUERY_FACET_KEY_SET = new Set<string>(AUTHOR_QUERY_FACET_KEYS);
const AUTHOR_QUERY_SORT_KEY_SET = new Set<string>(AUTHOR_QUERY_SORT_KEYS);

export function isAuthorQueryFacetKey(value: string): value is AuthorQueryFacetKey {
  return AUTHOR_QUERY_FACET_KEY_SET.has(value);
}

export function isAuthorQuerySortKey(value: string): value is AuthorQuerySortKey {
  return AUTHOR_QUERY_SORT_KEY_SET.has(value);
}

export interface AuthorMetadataSearchParams {
  readonly query: string;
  readonly region: string;
  readonly asin: string;
}
