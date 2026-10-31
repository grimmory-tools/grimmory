import {describe, expect, it} from 'vitest';

import {
  BOOKS_QUERY_KEY,
  bookDetailQueryKey,
  bookDetailQueryPrefix,
} from './book-query-keys';
import {LIBRARIES_QUERY_KEY, libraryFormatCountsQueryKey} from './library-query-keys';

describe('book query keys', () => {
  it('builds stable keys for book queries', () => {
    expect(BOOKS_QUERY_KEY).toEqual(['books']);
    expect(bookDetailQueryKey(12, true)).toEqual(['books', 'detail', 12, true]);
    expect(bookDetailQueryPrefix(12)).toEqual(['books', 'detail', 12]);
  });

  it('builds stable keys for library queries', () => {
    expect(LIBRARIES_QUERY_KEY).toEqual(['libraries']);
    expect(libraryFormatCountsQueryKey(7)).toEqual(['libraries', 'format-counts', 7]);
  });
});
