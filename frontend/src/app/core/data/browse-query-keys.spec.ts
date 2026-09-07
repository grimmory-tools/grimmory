import {describe, expect, it} from 'vitest';

import {
  BrowseCollectionFilterParams,
  BrowsePageParams,
  BrowseQueryParams,
  normalizeBrowseCollectionFilterParams,
  normalizeBrowsePageParams,
} from './browse-query-params';
import {createBrowseQueryKeys} from './browse-query-keys';

type TestCollectionParams = BrowseCollectionFilterParams<'genre'>;
type TestQueryParams = BrowseQueryParams<'genre', 'title'>;
type TestPageParams = BrowsePageParams<'genre', 'title'>;

const query: TestQueryParams = {
  query: 'dune',
  facets: {genre: ['Fantasy']},
  facetLogic: 'or',
  sort: [{key: 'title', direction: 'asc'}],
};
const page = normalizeBrowsePageParams({...query, size: 20});
const keys = createBrowseQueryKeys<
  TestCollectionParams,
  TestQueryParams,
  TestPageParams
>('items');

describe('browse query keys', () => {
  it('keeps bounded and infinite data shapes on different leaves', () => {
    expect(keys.boundedPage(page)).not.toEqual(keys.infinitePage(page));
    expect(keys.boundedPage(page).at(-1)).toBe(page);
    expect(keys.infinitePage(page).at(-1)).toBe(page);
  });

  it('keeps facet selection as part of query identity', () => {
    const genreSelected = normalizeBrowseCollectionFilterParams(query);
    const unfiltered = normalizeBrowseCollectionFilterParams({...query, facets: {}});

    expect(keys.facets(genreSelected)).not.toEqual(keys.facets(unfiltered));
  });
});
