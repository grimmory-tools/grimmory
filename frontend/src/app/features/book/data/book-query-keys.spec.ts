import {describe, expect, it} from 'vitest';

import {
  normalizeBrowseCollectionFilterParams,
  normalizeBrowsePageParams,
  normalizeBrowseQueryParams,
} from '../../../core/data/browse-query-params';
import {bookQueryKeys} from './book-query-keys';

describe('book query keys', () => {
  const query = {
    query: 'dune',
    facets: {genre: ['Fantasy']} as const,
    facetLogic: 'or' as const,
    sort: [{key: 'title', direction: 'asc'}] as const,
  };
  const page = normalizeBrowsePageParams({...query, size: 20});

  it('roots every read under the unified book-query prefix', () => {
    const keys = [
      bookQueryKeys.boundedPage(page),
      bookQueryKeys.infinitePage(page),
      bookQueryKeys.facets(normalizeBrowseCollectionFilterParams(query)),
      bookQueryKeys.ids(normalizeBrowseQueryParams(query)),
      bookQueryKeys.detail(12, true),
      bookQueryKeys.recommendation(12, 20),
    ];

    expect(bookQueryKeys.all()).toEqual(['books', 'query']);
    for (const key of keys) {
      expect(key.slice(0, 2)).toEqual(bookQueryKeys.all());
    }
  });

  it('nests every leaf under the prefix its invalidation targets', () => {
    const detailPrefix = bookQueryKeys.detailQueries(12);
    expect(bookQueryKeys.detail(12, true).slice(0, detailPrefix.length)).toEqual([...detailPrefix]);

    const recommendationPrefix = bookQueryKeys.recommendationQueries(12);
    expect(bookQueryKeys.recommendation(12, 20).slice(0, recommendationPrefix.length))
      .toEqual([...recommendationPrefix]);
  });
});
