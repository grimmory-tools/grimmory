import {HttpParams} from '@angular/common/http';

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
export type FacetValueMap = Readonly<Partial<Record<BookQueryFacetKey, readonly string[]>>>;
export type SortDirection = BrowseSortDirection;

export const EMPTY_FACET_SELECTION: FacetValueMap = {};

export type BookSortTerm = BrowseSortTerm<BookQuerySortKey>;

export interface BookCollectionFilterParams {
  query?: string;
  facets: FacetValueMap;
  facetLogic: FacetLogic;
}

export interface BookQueryParams extends BookCollectionFilterParams {
  sort: readonly BookSortTerm[];
}

export interface BookPageParams extends BookQueryParams {
  size: number;
}

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

export function normalizeBookPageParams(params: BookPageParams): BookPageParams {
  return {
    ...normalizeBookQueryParams(params),
    size: params.size,
  };
}

export function toPageHttpParams(params: BookPageParams): HttpParams {
  return appendSortParam(toCollectionHttpParams(params), params.sort)
    .set('size', params.size.toString());
}

export function toIdsHttpParams(params: BookQueryParams): HttpParams {
  return appendSortParam(toCollectionHttpParams(params), params.sort);
}

function appendSortParam(httpParams: HttpParams, sort: readonly BookSortTerm[]): HttpParams {
  return sort.length === 0 ? httpParams : httpParams.set('sort', serializeSort(sort));
}

export function normalizeBookQueryParams(params: BookQueryParams): BookQueryParams {
  return {
    ...normalizeBookCollectionFilterParams(params),
    sort: params.sort,
  };
}

export function normalizeBookCollectionFilterParams(
  params: BookCollectionFilterParams,
): BookCollectionFilterParams {
  const query = params.query?.trim();
  const facets = normalizeFacetValueMap(params.facets);

  return {
    ...(query ? {query} : {}),
    facets,
    facetLogic: params.facetLogic,
  };
}

export function toCollectionHttpParams(params: BookCollectionFilterParams): HttpParams {
  let httpParams = new HttpParams().set('facet_logic', params.facetLogic);

  if (params.query) {
    httpParams = httpParams.set('query', params.query);
  }

  return appendFacetParams(httpParams, params.facets);
}

function compareCodeUnits(first: string, second: string): number {
  return first < second ? -1 : first > second ? 1 : 0;
}

function normalizeFacetValueMap(facets: FacetValueMap): FacetValueMap {
  const normalized = Object.entries(facets)
    .sort(([first], [second]) => compareCodeUnits(first, second))
    .map(([key, values]) => [key, [...new Set(values
      .map(value => value.trim())
      .filter(Boolean))].sort(compareCodeUnits)] as const)
    .filter(([, values]) => values.length > 0);

  return Object.fromEntries(normalized);
}

function appendFacetParams(httpParams: HttpParams, facets: FacetValueMap): HttpParams {
  let result = httpParams;
  for (const [key, values] of Object.entries(facets)) {
    for (const value of values) {
      result = result.append('facet', `${key}:${value}`);
    }
  }

  return result;
}

function serializeSort(sort: readonly BookSortTerm[]): string {
  return sort
    .map(term => `${term.direction === 'desc' ? '-' : ''}${term.key}`)
    .join(',');
}
