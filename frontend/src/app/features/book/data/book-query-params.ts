import {unmarkedBrowseFacetKey, type BrowseFacetKey, type BrowseFacetSelection} from '../../../shared/browse/facets';

import {
  BrowseSortDirection,
  BrowseSortTerm,
} from '../../../core/data/browse.models';
import {
  BrowseCollectionFilterParams,
  BrowsePageParams,
  BrowseQueryParams,
} from '../../../core/data/browse-query-params';
export const BOOK_QUERY_FACET_KEYS = [
  'author',
  'genre',
  'tag',
  'mood',
  'series',
  'publisher',
  'language',
  'narrator',
  'file_type',
  'read_status',
  'personal_rating',
  'library',
  'shelf',
  'age_rating',
  'content_rating',
  'match_score',
  'published_year',
  'file_size',
  'page_count',
  'shelf_status',
  'amazon_rating',
  'goodreads_rating',
  'hardcover_rating',
  'ranobedb_rating',
  'lubimyczytac_rating',
  'audible_rating',
  'applebooks_rating',
  'comic_character',
  'comic_team',
  'comic_location',
  'comic_creator',
] as const;

export const BOOK_QUERY_SORT_KEYS = [
  'random',
  'title',
  'subtitle',
  'authorName',
  'authorSortName',
  'seriesName',
  'addedOn',
  'lastReadTime',
  'publishedDate',
  'personalRating',
  'readingProgress',
  'publisher',
  'seriesNumber',
  'pageCount',
  'narrator',
  'language',
  'readStatus',
  'dateFinished',
  'amazonRating',
  'amazonReviewCount',
  'goodreadsRating',
  'goodreadsReviewCount',
  'hardcoverRating',
  'hardcoverReviewCount',
  'ranobedbRating',
  'lubimyczytacRating',
  'audibleRating',
  'audibleReviewCount',
  'applebooksRating',
  'applebooksReviewCount',
] as const;

export type BookQueryFacetKey = typeof BOOK_QUERY_FACET_KEYS[number];
export type BookQuerySortKey = typeof BOOK_QUERY_SORT_KEYS[number];
export type FacetValueMap = BrowseFacetSelection<BookQueryFacetKey>;
export type SortDirection = BrowseSortDirection;

export const EMPTY_FACET_SELECTION: FacetValueMap = {};

export type BookSortTerm = BrowseSortTerm<BookQuerySortKey>;

export type BookCollectionFilterParams = BrowseCollectionFilterParams<BookQueryFacetKey>;
export type BookQueryParams = BrowseQueryParams<BookQueryFacetKey, BookQuerySortKey>;
export type BookPageParams = BrowsePageParams<BookQueryFacetKey, BookQuerySortKey>;

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

function isMarkedBookQueryFacetKey(value: string): value is BrowseFacetKey<BookQueryFacetKey> {
  return isBookQueryFacetKey(unmarkedBrowseFacetKey(value));
}

export function parseFacetParams(tokens: readonly string[]): FacetValueMap {
  const facets = new Map<BrowseFacetKey<BookQueryFacetKey>, string[]>();
  for (const token of tokens) {
    const separator = token.indexOf(':');
    if (separator <= 0) {
      continue;
    }
    const key = token.slice(0, separator);
    if (!isMarkedBookQueryFacetKey(key)) {
      continue;
    }
    const value = token.slice(separator + 1).trim();
    const values = facets.get(key) ?? [];
    if (value && !values.includes(value)) {
      values.push(value);
    }
    facets.set(key, values);
  }
  return Object.fromEntries([...facets].filter(([, values]) => values.length > 0));
}

export function bookFacetQueryParams(facets: FacetValueMap) {
  const tokens = Object.entries(facets).flatMap(([key, values]) =>
    values.map(value => `${key}:${value}`),
  );
  return {facet: tokens.length > 0 ? tokens : null};
}

export function parseSortTermsToken(token: string | null): BookSortTerm[] {
  if (!token) {
    return [];
  }

  const seen = new Set<string>();
  const terms: BookSortTerm[] = [];
  for (const rawTerm of token.split(',')) {
    const term = rawTerm.trim();
    if (!term) {
      continue;
    }
    const descending = term.startsWith('-');
    const key = (descending ? term.slice(1) : term).trim();
    if (!isBookQuerySortKey(key) || seen.has(key)) {
      continue;
    }
    seen.add(key);
    terms.push({key, direction: descending ? 'desc' : 'asc'});
  }
  return terms;
}
