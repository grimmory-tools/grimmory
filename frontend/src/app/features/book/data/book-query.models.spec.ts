import {describe, expect, it} from 'vitest';

import {BookPage, flattenBookPages} from './book-query.models';

function page(ids: number[]): BookPage {
  return {
    content: ids.map(id => ({id, libraryId: 1, libraryName: 'Library'})),
    page: {
      number: 0,
      size: ids.length,
      totalElements: ids.length,
      totalPages: 1,
      cursor: 'opaque-cursor',
    },
    links: [],
  };
}

describe('flattenBookPages', () => {
  it('returns an empty list without data', () => {
    expect(flattenBookPages(undefined)).toEqual([]);
  });

  it('flattens pages in order', () => {
    const flattened = flattenBookPages({
      pages: [page([1, 2]), page([3, 4])],
      pageParams: [null, 'next'],
    });

    expect(flattened.map(book => book.id)).toEqual([1, 2, 3, 4]);
  });

  it('keeps the first occurrence when an offset shift re-serves a book', () => {
    const flattened = flattenBookPages({
      pages: [page([1, 2]), page([2, 3])],
      pageParams: [null, 'next'],
    });

    expect(flattened.map(book => book.id)).toEqual([1, 2, 3]);
  });
});
