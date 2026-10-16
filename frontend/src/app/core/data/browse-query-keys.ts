import {hashKey} from '@tanstack/angular-query-experimental';

import {
  BrowseCollectionFilterParams,
  BrowsePageParams,
  BrowseQueryParams,
  normalizeBrowseCollectionFilterParams,
  normalizeBrowseQueryParams,
} from './browse-query-params';

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
    boundedPages,
    boundedPage: (params: BrowsePageParams<FacetKey, SortKey>) => [...boundedPages(), params] as const,
    infinitePages,
    infinitePage: (params: BrowsePageParams<FacetKey, SortKey>) => [...infinitePages(), params] as const,
    facetQueries,
    facetIndex: (params: BrowseCollectionFilterParams<FacetKey>) => [...facetQueries(), 'index', params] as const,
    facet: (key: FacetKey, params: BrowseCollectionFilterParams<FacetKey>, search: string) =>
      [...facetQueries(), 'facet', key, params, search] as const,
    idQueries,
    ids: (params: BrowseQueryParams<FacetKey, SortKey>) => [...idQueries(), params] as const,
  };
}

export type BrowseQueryKeys<FacetKey extends string, SortKey extends string> =
  ReturnType<typeof browseQueryKeys<FacetKey, SortKey>>;

export function browseCollectionKeys<FacetKey extends string, SortKey extends string>(
  params: BrowseQueryParams<FacetKey, SortKey>,
) {
  return {
    filtersKey: hashKey([normalizeBrowseCollectionFilterParams(params)]),
    listKey: hashKey([normalizeBrowseQueryParams(params)]),
  };
}
