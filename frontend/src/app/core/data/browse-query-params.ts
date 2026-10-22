import {HttpParams} from '@angular/common/http';

import {
  browseFacetSelectionKeys,
  browseFacetValues,
  unmarkedBrowseFacetKey,
  type BrowseFacetKey,
  type BrowseFacetSelection,
} from '../../shared/browse/facets';
import {BrowseSortTerm} from './browse.models';

export interface BrowseCollectionFilterParams<FacetKey extends string> {
  query?: string;
  facets: BrowseFacetSelection<FacetKey>;
}

export interface BrowseQueryParams<FacetKey extends string, SortKey extends string>
  extends BrowseCollectionFilterParams<FacetKey> {
  sort: readonly BrowseSortTerm<SortKey>[];
}

export interface BrowsePageParams<FacetKey extends string, SortKey extends string>
  extends BrowseQueryParams<FacetKey, SortKey> {
  size: number;
}

export function sortTermsToken(terms: readonly BrowseSortTerm[]): string {
  return terms
    .map(term => term.direction === 'desc' ? `-${term.key}` : term.key)
    .join(',');
}

export function normalizeBrowsePageParams<FacetKey extends string, SortKey extends string>(
  params: BrowsePageParams<FacetKey, SortKey>,
): BrowsePageParams<FacetKey, SortKey> {
  return {
    ...normalizeBrowseQueryParams(params),
    size: params.size,
  };
}

export function toPageHttpParams<FacetKey extends string, SortKey extends string>(
  params: BrowsePageParams<FacetKey, SortKey>,
): HttpParams {
  return appendSortParam(toCollectionHttpParams(params), params.sort)
    .set('size', params.size.toString());
}

export function toIdsHttpParams<FacetKey extends string, SortKey extends string>(
  params: BrowseQueryParams<FacetKey, SortKey>,
): HttpParams {
  return appendSortParam(toCollectionHttpParams(params), params.sort);
}

function appendSortParam(httpParams: HttpParams, sort: readonly BrowseSortTerm[]): HttpParams {
  return sort.length === 0 ? httpParams : httpParams.set('sort', sortTermsToken(sort));
}

export function normalizeBrowseQueryParams<FacetKey extends string, SortKey extends string>(
  params: BrowseQueryParams<FacetKey, SortKey>,
): BrowseQueryParams<FacetKey, SortKey> {
  return {
    ...normalizeBrowseCollectionFilterParams(params),
    sort: params.sort,
  };
}

export function normalizeBrowseCollectionFilterParams<FacetKey extends string>(
  params: BrowseCollectionFilterParams<FacetKey>,
): BrowseCollectionFilterParams<FacetKey> {
  const query = params.query?.trim();
  const facets = normalizeFacetSelection(params.facets);

  return {
    ...(query ? {query} : {}),
    facets,
  };
}

export function toCollectionHttpParams<FacetKey extends string>(
  params: BrowseCollectionFilterParams<FacetKey>,
): HttpParams {
  let httpParams = new HttpParams();

  if (params.query) {
    httpParams = httpParams.set('query', params.query);
  }

  return appendFacetParams(httpParams, params.facets);
}

function compareCodeUnits(first: string, second: string): number {
  return first < second ? -1 : first > second ? 1 : 0;
}

function normalizeFacetSelection<FacetKey extends string>(
  facets: BrowseFacetSelection<FacetKey>,
): BrowseFacetSelection<FacetKey> {
  const normalized: Partial<Record<BrowseFacetKey<FacetKey>, readonly string[]>> = {};
  for (const key of browseFacetSelectionKeys(facets).sort(compareCodeUnits)) {
    const values = [...new Set(browseFacetValues(facets, key)
      .map(value => value.trim())
      .filter(Boolean))].sort(compareCodeUnits);
    if (values.length > 0) {
      normalized[key] = values;
    }
  }

  return normalized;
}

function facetTokens<FacetKey extends string>(facets: BrowseFacetSelection<FacetKey>): string[] {
  return Object.entries<readonly string[] | undefined>(facets)
    .flatMap(([key, values = []]) => values.map(value => `${key}:${value}`));
}

function appendFacetParams<FacetKey extends string>(
  httpParams: HttpParams,
  facets: BrowseFacetSelection<FacetKey>,
): HttpParams {
  return facetTokens(facets).reduce((result, token) => result.append('facet', token), httpParams);
}

export function facetQueryParams<FacetKey extends string>(facets: BrowseFacetSelection<FacetKey>) {
  const tokens = facetTokens(facets);
  return {facet: tokens.length > 0 ? tokens : null};
}

function isMarkedFacetKey<FacetKey extends string>(
  value: string,
  isFacetKey: (key: string) => key is FacetKey,
): value is BrowseFacetKey<FacetKey> {
  return isFacetKey(unmarkedBrowseFacetKey(value));
}

export function parseFacetParams<FacetKey extends string>(
  tokens: readonly string[],
  isFacetKey: (key: string) => key is FacetKey,
): BrowseFacetSelection<FacetKey> {
  const facets: Partial<Record<BrowseFacetKey<FacetKey>, string[]>> = {};
  for (const token of tokens) {
    const separator = token.indexOf(':');
    if (separator <= 0) {
      continue;
    }
    const key = token.slice(0, separator);
    if (!isMarkedFacetKey(key, isFacetKey)) {
      continue;
    }
    const value = token.slice(separator + 1).trim();
    const values = facets[key] ?? [];
    if (value && !values.includes(value)) {
      values.push(value);
    }
    if (values.length > 0) {
      facets[key] = values;
    }
  }
  return facets;
}

export function parseSortTermsToken<SortKey extends string>(
  token: string | null,
  isSortKey: (key: string) => key is SortKey,
): BrowseSortTerm<SortKey>[] {
  if (!token) {
    return [];
  }

  const seen = new Set<string>();
  const terms: BrowseSortTerm<SortKey>[] = [];
  for (const rawTerm of token.split(',')) {
    const term = rawTerm.trim();
    if (!term) {
      continue;
    }
    const descending = term.startsWith('-');
    const key = (descending ? term.slice(1) : term).trim();
    if (!isSortKey(key) || seen.has(key)) {
      continue;
    }
    seen.add(key);
    terms.push({key, direction: descending ? 'desc' : 'asc'});
  }
  return terms;
}
