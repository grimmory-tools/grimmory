export function createBrowseQueryKeys<CollectionParams, QueryParams, PageParams>(resource: string) {
  const all = () => [resource, 'query'] as const;
  const collections = () => [...all(), 'collection'] as const;
  const boundedPages = () => [...collections(), 'page', 'bounded'] as const;
  const infinitePages = () => [...collections(), 'page', 'infinite'] as const;
  const facetQueries = () => [...collections(), 'facets'] as const;
  const idQueries = () => [...collections(), 'ids'] as const;

  return {
    all,
    collections,
    boundedPages,
    boundedPage: (params: PageParams) => [...boundedPages(), params] as const,
    infinitePages,
    infinitePage: (params: PageParams) => [...infinitePages(), params] as const,
    facetQueries,
    facets: (params: CollectionParams) => [...facetQueries(), params] as const,
    idQueries,
    ids: (params: QueryParams) => [...idQueries(), params] as const,
  };
}
