import {HttpParams} from '@angular/common/http';

import {
  BrowseFacetLogic,
  BrowseSortTerm,
} from './browse.models';

export type BrowseFacetValueMap<Key extends string> =
  Readonly<Partial<Record<Key, readonly string[]>>>;

export interface BrowseCollectionFilterParams<FacetKey extends string> {
  query?: string;
  facets: BrowseFacetValueMap<FacetKey>;
  facetLogic: BrowseFacetLogic;
}

export interface BrowseQueryParams<FacetKey extends string, SortKey extends string>
  extends BrowseCollectionFilterParams<FacetKey> {
  sort: readonly BrowseSortTerm<SortKey>[];
}

export interface BrowsePageParams<FacetKey extends string, SortKey extends string>
  extends BrowseQueryParams<FacetKey, SortKey> {
  size: number;
}

export function normalizeBrowsePageParams<FacetKey extends string, SortKey extends string>(
  params: BrowsePageParams<FacetKey, SortKey>,
): BrowsePageParams<FacetKey, SortKey> {
  return {
    ...normalizeBrowseQueryParams(params),
    size: params.size,
  };
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
  const facets = normalizeFacetValueMap(params.facets);

  return {
    ...(query ? {query} : {}),
    facets,
    facetLogic: params.facetLogic,
  };
}

export function toBrowsePageHttpParams<FacetKey extends string, SortKey extends string>(
  params: BrowsePageParams<FacetKey, SortKey>,
): HttpParams {
  return appendSortParam(toBrowseCollectionHttpParams(params), params.sort)
    .set('size', params.size.toString());
}

export function toBrowseIdsHttpParams<FacetKey extends string, SortKey extends string>(
  params: BrowseQueryParams<FacetKey, SortKey>,
): HttpParams {
  return appendSortParam(toBrowseCollectionHttpParams(params), params.sort);
}

export function toBrowseCollectionHttpParams<FacetKey extends string>(
  params: BrowseCollectionFilterParams<FacetKey>,
): HttpParams {
  let httpParams = new HttpParams().set('facet_logic', params.facetLogic);

  if (params.query) {
    httpParams = httpParams.set('query', params.query);
  }

  return appendFacetParams(httpParams, params.facets);
}

function appendSortParam<SortKey extends string>(
  httpParams: HttpParams,
  sort: readonly BrowseSortTerm<SortKey>[],
): HttpParams {
  return sort.length === 0 ? httpParams : httpParams.set('sort', serializeSort(sort));
}

function normalizeFacetValueMap<Key extends string>(
  facets: BrowseFacetValueMap<Key>,
): BrowseFacetValueMap<Key> {
  const normalized: Partial<Record<Key, readonly string[]>> = {};
  const keys = (Object.keys(facets) as Key[]).sort(compareCodeUnits);

  for (const key of keys) {
    const values = [...new Set((facets[key] ?? [])
      .map(value => value.trim())
      .filter(Boolean))].sort(compareCodeUnits);
    if (values.length > 0) {
      normalized[key] = values;
    }
  }

  return normalized;
}

function appendFacetParams<Key extends string>(
  httpParams: HttpParams,
  facets: BrowseFacetValueMap<Key>,
): HttpParams {
  let result = httpParams;
  for (const key of Object.keys(facets) as Key[]) {
    for (const value of facets[key] ?? []) {
      result = result.append('facet', `${key}:${value}`);
    }
  }

  return result;
}

function serializeSort<SortKey extends string>(
  sort: readonly BrowseSortTerm<SortKey>[],
): string {
  return sort
    .map(term => `${term.direction === 'desc' ? '-' : ''}${term.key}`)
    .join(',');
}

function compareCodeUnits(first: string, second: string): number {
  return first < second ? -1 : first > second ? 1 : 0;
}
