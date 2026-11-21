import {hashKey} from '@tanstack/angular-query-experimental';

import {
  BrowseCollectionFilterParams,
  BrowsePageParams,
  BrowseQueryParams,
  normalizeBrowseCollectionFilterParams,
  normalizeBrowseQueryParams,
} from './browse-query-params';

export const DEFAULT_FACET_PAGE_SIZE = 100;

export interface BrowseFacetPageOptions {
  readonly page?: number;
  readonly size?: number;
}

export function browseQueryKeys<FacetKey extends string, SortKey extends string>(root: string) {
  const all = () => [root, 'query'] as const;
  const collections = () => [...all(), 'collection'] as const;
  const boundedPages = () => [...collections(), 'page', 'bounded'] as const;
  const infinitePages = () => [...collections(), 'page', 'infinite'] as const;
  const facetQueries = () => [...collections(), 'facets'] as const;
  const idQueries = () => [...collections(), 'ids'] as const;

  return {
    all,
    collections,
    boundedPage: (params: BrowsePageParams<FacetKey, SortKey>) => [...boundedPages(), params] as const,
    infinitePage: (params: BrowsePageParams<FacetKey, SortKey>) => [...infinitePages(), params] as const,
    facetIndex: (params: BrowseCollectionFilterParams<FacetKey>) => [...facetQueries(), 'index', params] as const,
    facet: (
      key: FacetKey,
      params: BrowseCollectionFilterParams<FacetKey>,
      search: string,
      {page = 0, size = DEFAULT_FACET_PAGE_SIZE}: BrowseFacetPageOptions = {},
    ) => [...facetQueries(), 'facet', key, params, search, {page, size}] as const,
    ids: (params: BrowseQueryParams<FacetKey, SortKey>) => [...idQueries(), params] as const,
  };
}

export type BrowseQueryKeys<FacetKey extends string, SortKey extends string> =
  ReturnType<typeof browseQueryKeys<FacetKey, SortKey>>;

export function browseCollectionKeys<FacetKey extends string, SortKey extends string>(
  params: BrowseQueryParams<FacetKey, SortKey>,
  isKey: (key: string) => key is FacetKey,
) {
  return {
    filtersKey: hashKey([normalizeBrowseCollectionFilterParams(params, isKey)]),
    listKey: hashKey([normalizeBrowseQueryParams(params, isKey)]),
  };
}
