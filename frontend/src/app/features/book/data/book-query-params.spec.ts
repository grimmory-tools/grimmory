import {describe, expect, it} from 'vitest';

import {
  bookFacetQueryParams,
  normalizeBookCollectionFilterParams,
  normalizeBookQueryParams,
  normalizeBookPageParams,
  parseFacetParams,
  toCollectionHttpParams,
  toIdsHttpParams,
  toPageHttpParams,
} from './book-query-params';

describe('book query parameters', () => {
  it('normalizes equivalent queries and facet selections', () => {
    const first = normalizeBookPageParams({
      query: '  dune  ',
      facets: {
        language: [' French ', 'English'],
        genre: [' Science Fiction ', 'Fantasy', 'Science Fiction', ' '],
      },
      sort: [{key: 'title', direction: 'asc'}],
      size: 40,
    });
    const second = normalizeBookPageParams({
      query: 'dune',
      facets: {
        genre: ['Fantasy', 'Science Fiction'],
        language: ['English', 'French'],
      },
      sort: [{key: 'title', direction: 'asc'}],
      size: 40,
    });

    expect(first).toEqual(second);
    expect(first.facets).toEqual({
      genre: ['Fantasy', 'Science Fiction'],
      language: ['English', 'French'],
    });
  });

  it('round-trips a selection through route params and drops unknown keys', () => {
    const selection = parseFacetParams([
      'genre:Comedy', 'genre:Drama', 'tag:Anthology', 'future_group:a:b', '__proto__:READ',
    ]);
    expect(selection).toEqual({genre: ['Comedy', 'Drama'], tag: ['Anthology']});
    expect(bookFacetQueryParams(selection)).toEqual({facet: ['genre:Comedy', 'genre:Drama', 'tag:Anthology']});
    expect(bookFacetQueryParams({})).toEqual({facet: null});
  });

  it('passes an empty sort through without imposing a default', () => {
    const normalized = normalizeBookQueryParams({
      facets: {},
      sort: [],
    });

    expect(normalized.sort).toEqual([]);
  });

  it('serializes page parameters using the backend vocabulary', () => {
    const params = toPageHttpParams(normalizeBookPageParams({
      query: 'dune',
      facets: {
        genre: ['Science Fiction'],
        '-genre': ['Romance'],
        '+shelf': ['magic:12'],
      },
      sort: [
        {key: 'seriesName', direction: 'asc'},
        {key: 'seriesNumber', direction: 'desc'},
      ],
      size: 50,
    }));

    expect(params.get('query')).toBe('dune');
    expect(params.getAll('facet')).toEqual(['+shelf:magic:12', '-genre:Romance', 'genre:Science Fiction']);
    expect(params.get('sort')).toBe('seriesName,-seriesNumber');
    expect(params.get('size')).toBe('50');
    expect(params.has('page')).toBe(false);
  });

  it('excludes sort and size from facet requests', () => {
    const params = toCollectionHttpParams(normalizeBookCollectionFilterParams({
      query: 'dune',
      facets: {genre: ['Fantasy']},
    }));

    expect(params.get('query')).toBe('dune');
    expect(params.getAll('facet')).toEqual(['genre:Fantasy']);
    expect(params.has('sort')).toBe(false);
    expect(params.has('size')).toBe(false);
  });

  it('does not emit facet parameters for an empty selection', () => {
    const params = toCollectionHttpParams(normalizeBookCollectionFilterParams({
      facets: {},
    }));

    expect(params.has('facet')).toBe(false);
  });

  it('includes sort but excludes size from ID requests', () => {
    const params = toIdsHttpParams(normalizeBookQueryParams({
      facets: {},
      sort: [{key: 'title', direction: 'desc'}],
    }));

    expect(params.get('sort')).toBe('-title');
    expect(params.has('size')).toBe(false);
  });
});
