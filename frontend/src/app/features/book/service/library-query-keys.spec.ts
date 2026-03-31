import {describe, expect, it} from 'vitest';

import {libraryFormatCountsQueryKey, LIBRARIES_QUERY_KEY} from './library-query-keys';

describe('library-query-keys', () => {
  it('uses a stable key for the libraries collection', () => {
    expect(LIBRARIES_QUERY_KEY).toEqual(['libraries']);
  });

  it('derives per-library format count keys', () => {
    expect(libraryFormatCountsQueryKey(42)).toEqual(['libraries', 'format-counts', 42]);
  });
});
