import {
  BrowseCollectionFilterParams,
  BrowseFacetValueMap,
  BrowsePageParams,
  BrowseQueryParams,
} from '../../../core/data/browse-query-params';
import {BrowseSortTerm} from '../../../core/data/browse.models';

export const AUTHOR_QUERY_FACET_KEYS = [
  'has_asin',
  'has_photo',
  'has_description',
  'read_status',
  'book_count',
  'library',
  'genre',
  'language',
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
export type AuthorFacetValueMap = BrowseFacetValueMap<AuthorQueryFacetKey>;
export type AuthorSortTerm = BrowseSortTerm<AuthorQuerySortKey>;
export type AuthorCollectionFilterParams = BrowseCollectionFilterParams<AuthorQueryFacetKey>;
export type AuthorQueryParams = BrowseQueryParams<AuthorQueryFacetKey, AuthorQuerySortKey>;
export type AuthorPageParams = BrowsePageParams<AuthorQueryFacetKey, AuthorQuerySortKey>;

export const EMPTY_AUTHOR_FACET_SELECTION: AuthorFacetValueMap = {};
export const DEFAULT_AUTHOR_SORT_TERMS: readonly AuthorSortTerm[] = [
  {key: 'name', direction: 'asc'},
];
