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
    sort: [{key: 'title', direction: 'asc'}] as const,
  };
  const page = normalizeBrowsePageParams({...query, size: 20});

  it('roots every read under the unified book-query prefix', () => {
    const keys = [
      bookQueryKeys.boundedPage(page),
      bookQueryKeys.infinitePage(page),
      bookQueryKeys.facet('genre', normalizeBrowseCollectionFilterParams(query), ''),
      bookQueryKeys.ids(normalizeBrowseQueryParams(query)),
      bookQueryKeys.detail(12, true),
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
    const genreSelected = normalizeBrowseCollectionFilterParams(query);
    const unfiltered = normalizeBrowseCollectionFilterParams({...query, facets: {}});

    expect(bookQueryKeys.facet('author', genreSelected, '')).not.toEqual(bookQueryKeys.facet('author', unfiltered, ''));
  });

  it('nests every leaf under the prefix its invalidation targets', () => {
    const detailPrefix = bookQueryKeys.detailQueries(12);
    expect(bookQueryKeys.detail(12, true).slice(0, detailPrefix.length)).toEqual([...detailPrefix]);
  });
});
