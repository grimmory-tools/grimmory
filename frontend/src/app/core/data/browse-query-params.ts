import {HttpParams} from '@angular/common/http';

import {browseFacetEntries, withBrowseFacetValues, type BrowseFacetSelection, type BrowseScope} from '../../shared/browse/facets';
import {BrowseSortTerm} from './browse.models';

export interface BrowseCollectionFilterParams<FacetKey extends string> {
  query?: string;
  facets: BrowseFacetSelection<FacetKey>;
  scope?: BrowseScope<FacetKey>;
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

function facetTokens(facets: BrowseFacetSelection): string[] {
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

export function facetQueryParams(facets: BrowseFacetSelection) {
  const tokens = facetTokens(facets);
  return {facet: tokens.length > 0 ? tokens : null};
}

export function parseFacetParams<FacetKey extends string>(
  tokens: readonly string[],
  isFacetKey: (key: string) => key is FacetKey,
): BrowseFacetSelection<FacetKey> {
  const facets: Partial<Record<FacetKey, {include: string[]; exclude: string[]; matchAll?: true}>> = {};
  for (const token of tokens) {
    const separator = token.indexOf(':');
    const mark = token.startsWith('+') || token.startsWith('-') ? token[0] : '';
    const key = token.slice(mark.length, separator);
    const value = token.slice(separator + 1).trim();
    if (separator <= mark.length || !value || !isFacetKey(key)) {
      continue;
    }
    const facet = facets[key] ??= {include: [], exclude: []};
    const values = mark === '-' ? facet.exclude : facet.include;
    if (!values.includes(value)) {
      values.push(value);
    }
    if (mark === '+') {
      facet.matchAll = true;
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
