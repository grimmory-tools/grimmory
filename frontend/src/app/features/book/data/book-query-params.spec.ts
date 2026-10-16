import {describe, expect, it} from 'vitest';

import {
  bookFacetQueryParams,
  parseFacetParams,
} from './book-query-params';

describe('book query parameters', () => {
  it('round-trips a selection through route params and drops unknown keys', () => {
    const selection = parseFacetParams([
      'genre:Comedy', 'genre:Drama', 'tag:Anthology', 'future_group:a:b', '__proto__:READ',
    ]);
    expect(selection).toEqual({genre: ['Comedy', 'Drama'], tag: ['Anthology']});
    expect(bookFacetQueryParams(selection)).toEqual({facet: ['genre:Comedy', 'genre:Drama', 'tag:Anthology']});
    expect(bookFacetQueryParams({})).toEqual({facet: null});
  });
});
