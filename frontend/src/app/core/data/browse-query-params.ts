import {HttpParams} from '@angular/common/http';

import {browseFacetValues, type BrowseFacetKey, type BrowseFacetSelection} from '../../shared/browse/facets';
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
  for (const key of (Object.keys(facets) as BrowseFacetKey<FacetKey>[]).sort(compareCodeUnits)) {
    const values = [...new Set(browseFacetValues(facets, key)
      .map(value => value.trim())
      .filter(Boolean))].sort(compareCodeUnits);
    if (values.length > 0) {
      normalized[key] = values;
    }
  }

  return normalized;
}

function appendFacetParams<FacetKey extends string>(
  httpParams: HttpParams,
  facets: BrowseFacetSelection<FacetKey>,
): HttpParams {
  let result = httpParams;
  for (const [key, values = []] of Object.entries<readonly string[] | undefined>(facets)) {
    for (const value of values) {
      result = result.append('facet', `${key}:${value}`);
    }
  }

  return result;
}
