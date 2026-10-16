import {HttpParams} from '@angular/common/http';

import {browseFacetEntries, withBrowseFacetValues, type BrowseFacetSelection} from '../../shared/browse/facets';
import {BrowseSortTerm} from './browse.models';

export interface BrowseCollectionFilterParams<FacetKey extends string> {
  query?: string;
  facets: BrowseFacetSelection<FacetKey>;
  scope?: {readonly facetKey: FacetKey; readonly facetValue: string};
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
  isKey: (key: string) => key is FacetKey,
): BrowsePageParams<FacetKey, SortKey> {
  return {
    ...normalizeBrowseQueryParams(params, isKey),
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
  isKey: (key: string) => key is FacetKey,
): BrowseQueryParams<FacetKey, SortKey> {
  return {
    ...normalizeBrowseCollectionFilterParams(params, isKey),
    sort: params.sort,
  };
}

export function normalizeBrowseCollectionFilterParams<FacetKey extends string>(
  params: BrowseCollectionFilterParams<FacetKey>,
  isKey: (key: string) => key is FacetKey,
): BrowseCollectionFilterParams<FacetKey> {
  const query = params.query?.trim();
  const facets = normalizeFacetSelection(params.facets, isKey);

  return {
    ...(query ? {query} : {}),
    facets,
    ...(params.scope ? {scope: {facetKey: params.scope.facetKey, facetValue: params.scope.facetValue}} : {}),
  };
}

export function toCollectionHttpParams<FacetKey extends string>(
  params: BrowseCollectionFilterParams<FacetKey>,
): HttpParams {
  let httpParams = new HttpParams();

  if (params.query) {
    httpParams = httpParams.set('query', params.query);
  }

  const scope = params.scope ? [`+${params.scope.facetKey}:${params.scope.facetValue}`] : [];
  return [...scope, ...facetTokens(params.facets)].reduce((result, token) => result.append('facet', token), httpParams);
}

export function facetTokens(facets: BrowseFacetSelection): string[] {
  return Object.entries(facets).flatMap(([key, values]) => values ? [
    ...values.include.map(value => `${values.matchAll ? '+' : ''}${key}:${value}`),
    ...values.exclude.map(value => `-${key}:${value}`),
  ] : []);
}

function compareCodeUnits(first: string, second: string): number {
  return first < second ? -1 : first > second ? 1 : 0;
}

function normalizeValues(values: readonly string[]): string[] {
  return [...new Set(values.map(value => value.trim()).filter(Boolean))].sort(compareCodeUnits);
}

function normalizeFacetSelection<FacetKey extends string>(
  facets: BrowseFacetSelection<FacetKey>,
  isKey: (key: string) => key is FacetKey,
): BrowseFacetSelection<FacetKey> {
  return browseFacetEntries(facets, isKey).reduce((normalized, [key, values]) => withBrowseFacetValues(normalized, key, {
    ...values,
    include: normalizeValues(values.include),
    exclude: normalizeValues(values.exclude),
  }), facets);
}
