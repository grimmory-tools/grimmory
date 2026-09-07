import {
  BrowseCollectionFilterParams,
  BrowseFacetValueMap,
  BrowsePageParams as SharedBrowsePageParams,
  BrowseQueryParams as SharedBrowseQueryParams,
} from '../../../core/data/browse-query-params';
import {
  BrowseFacetLogic,
  BrowseSortDirection,
  BrowseSortTerm,
} from '../../../core/data/browse.models';

export const BOOK_QUERY_FACET_KEYS = [
  'author',
  'series',
  'genre',
  'tag',
  'mood',
  'language',
  'publisher',
  'library',
  'shelf',
  'file_type',
  'read_status',
  'personal_rating',
  'amazon_rating',
  'goodreads_rating',
  'hardcover_rating',
  'ranobedb_rating',
  'age_rating',
  'content_rating',
  'match_score',
  'published_year',
  'file_size',
  'page_count',
  'shelf_status',
  'comic_character',
  'comic_team',
  'comic_location',
  'comic_creator',
] as const;

export const BOOK_QUERY_SORT_KEYS = [
  'addedOn',
  'title',
  'seriesName',
  'seriesNumber',
  'publisher',
  'publishedDate',
  'amazonRating',
  'amazonReviewCount',
  'goodreadsRating',
  'goodreadsReviewCount',
  'hardcoverRating',
  'hardcoverReviewCount',
  'ranobedbRating',
  'narrator',
  'pageCount',
  'language',
  'personalRating',
  'lastReadTime',
  'readStatus',
  'dateFinished',
  'readingProgress',
] as const;

export type BookQueryFacetKey = typeof BOOK_QUERY_FACET_KEYS[number];
export type BookQuerySortKey = typeof BOOK_QUERY_SORT_KEYS[number];
export type FacetLogic = BrowseFacetLogic;
export type FacetValueMap = BrowseFacetValueMap<BookQueryFacetKey>;
export type SortDirection = BrowseSortDirection;

export const EMPTY_FACET_SELECTION: FacetValueMap = {};

export type BookSortTerm = BrowseSortTerm<BookQuerySortKey>;

export type BookCollectionFilterParams = BrowseCollectionFilterParams<BookQueryFacetKey>;

export type BookQueryParams = SharedBrowseQueryParams<BookQueryFacetKey, BookQuerySortKey>;

export type BookPageParams = SharedBrowsePageParams<BookQueryFacetKey, BookQuerySortKey>;

export interface BookDescriptionOptions {
  withDescription: boolean;
}

export const DEFAULT_BOOK_SORT_TERMS: readonly BookSortTerm[] = [{key: 'title', direction: 'asc'}];
const BOOK_QUERY_FACET_KEY_SET = new Set<string>(BOOK_QUERY_FACET_KEYS);
const BOOK_QUERY_SORT_KEY_SET = new Set<string>(BOOK_QUERY_SORT_KEYS);

export function isBookQueryFacetKey(value: string): value is BookQueryFacetKey {
  return BOOK_QUERY_FACET_KEY_SET.has(value);
}

export function isBookQuerySortKey(value: string): value is BookQuerySortKey {
  return BOOK_QUERY_SORT_KEY_SET.has(value);
}
