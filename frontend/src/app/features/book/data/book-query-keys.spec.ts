import {describe, expect, it} from 'vitest';

import {
  normalizeBookCollectionFilterParams,
  normalizeBookQueryParams,
  normalizeBookPageParams,
  type BookPageParams,
} from './book-query-params';
import {bookCollectionKeys, bookQueryKeys} from './book-query-keys';

describe('book query keys', () => {
  const query = {
    query: 'dune',
    facets: {genre: ['Fantasy']} as const,
    facetLogic: 'or' as const,
    sort: [{key: 'title', direction: 'asc'}] as const,
  };
  const page = normalizeBookPageParams({...query, size: 20});

  it('roots every read under the unified book-query prefix', () => {
    const keys = [
      bookQueryKeys.boundedPage(page),
      bookQueryKeys.infinitePage(page),
      bookQueryKeys.facets(normalizeBookCollectionFilterParams(query)),
      bookQueryKeys.ids(normalizeBookQueryParams(query)),
      bookQueryKeys.detail(12, true),
      bookQueryKeys.recommendation(12, 20),
    ];

    expect(bookQueryKeys.all()).toEqual(['books', 'query']);
    for (const key of keys) {
      expect(key.slice(0, 2)).toEqual(bookQueryKeys.all());
    }
  });

  it('keeps bounded and infinite data shapes on different leaves', () => {
    expect(bookQueryKeys.boundedPage(page)).not.toEqual(bookQueryKeys.infinitePage(page));
    expect(bookQueryKeys.boundedPage(page).at(-1)).toBe(page);
    expect(bookQueryKeys.infinitePage(page).at(-1)).toBe(page);
  });

  it('keeps facet selection as part of query identity', () => {
    const genreSelected = normalizeBookCollectionFilterParams(query);
    const unfiltered = normalizeBookCollectionFilterParams({...query, facets: {}});

    expect(bookQueryKeys.facets(genreSelected)).not.toEqual(bookQueryKeys.facets(unfiltered));
  });

  it('nests every leaf under the prefix its invalidation targets', () => {
    const detailPrefix = bookQueryKeys.detailQueries(12);
    expect(bookQueryKeys.detail(12, true).slice(0, detailPrefix.length)).toEqual([...detailPrefix]);

    const recommendationPrefix = bookQueryKeys.recommendationQueries(12);
    expect(bookQueryKeys.recommendation(12, 20).slice(0, recommendationPrefix.length))
      .toEqual([...recommendationPrefix]);
  });
});

const PARAMS: BookPageParams = {
  query: 'dune',
  facets: {genre: ['Science Fiction']},
  facetLogic: 'or',
  sort: [{key: 'title', direction: 'asc'}],
  size: 20,
};

describe('bookCollectionKeys', () => {
  it('normalizes the params and keeps membership identity apart from ordering identity', () => {
    const collection = bookCollectionKeys(PARAMS);
    const equivalent = bookCollectionKeys({
      ...PARAMS,
      query: '  dune  ',
      facets: {genre: ['Science Fiction', 'Science Fiction']},
    });
    expect(equivalent.filtersKey).toBe(collection.filtersKey);
    expect(equivalent.listKey).toBe(collection.listKey);

    const largerPage: BookPageParams = {...PARAMS, size: 50};
    const resized = bookCollectionKeys(largerPage);
    expect(resized.filtersKey).toBe(collection.filtersKey);
    expect(resized.listKey).toBe(collection.listKey);

    const reordered = bookCollectionKeys({...PARAMS, sort: [{key: 'title', direction: 'desc'}]});
    expect(reordered.filtersKey).toBe(collection.filtersKey);
    expect(reordered.listKey).not.toBe(collection.listKey);

    const refiltered = bookCollectionKeys({...PARAMS, query: 'foundation'});
    expect(refiltered.filtersKey).not.toBe(collection.filtersKey);
    expect(refiltered.listKey).not.toBe(collection.listKey);
  });
});
