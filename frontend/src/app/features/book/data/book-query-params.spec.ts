import {describe, expect, it} from 'vitest';

import {
  normalizeBookCollectionFilterParams,
  normalizeBookQueryParams,
  normalizeBookPageParams,
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
      facetLogic: 'or',
      sort: [{key: 'title', direction: 'asc'}],
      size: 40,
    });
    const second = normalizeBookPageParams({
      query: 'dune',
      facets: {
        genre: ['Fantasy', 'Science Fiction'],
        language: ['English', 'French'],
      },
      facetLogic: 'or',
      sort: [{key: 'title', direction: 'asc'}],
      size: 40,
    });

    expect(first).toEqual(second);
    expect(first.facets).toEqual({
      genre: ['Fantasy', 'Science Fiction'],
      language: ['English', 'French'],
    });
  });

  it('passes an empty sort through without imposing a default', () => {
    const normalized = normalizeBookQueryParams({
      facets: {},
      facetLogic: 'or',
      sort: [],
    });

    expect(normalized.sort).toEqual([]);
  });

  it.each(['and'] as const)('preserves explicit %s facet logic', facetLogic => {
    const normalized = normalizeBookQueryParams({facets: {}, facetLogic, sort: []});

    expect(normalized.facetLogic).toBe(facetLogic);
    expect(toIdsHttpParams(normalized).get('facet_logic')).toBe(facetLogic);
  });

  it('serializes page parameters using the backend vocabulary', () => {
    const params = toPageHttpParams(normalizeBookPageParams({
      query: 'dune',
      facets: {
        genre: ['Science Fiction'],
        shelf: ['magic:12'],
      },
      facetLogic: 'not',
      sort: [
        {key: 'seriesName', direction: 'asc'},
        {key: 'seriesNumber', direction: 'desc'},
      ],
      size: 50,
    }));

    expect(params.get('query')).toBe('dune');
    expect(params.getAll('facet')).toEqual(['genre:Science Fiction', 'shelf:magic:12']);
    expect(params.get('facet_logic')).toBe('not');
    expect(params.get('sort')).toBe('seriesName,-seriesNumber');
    expect(params.get('size')).toBe('50');
    expect(params.has('page')).toBe(false);
  });

  it('excludes sort and size from facet requests', () => {
    const params = toCollectionHttpParams(normalizeBookCollectionFilterParams({
      query: 'dune',
      facets: {genre: ['Fantasy']},
      facetLogic: 'and',
    }));

    expect(params.get('query')).toBe('dune');
    expect(params.getAll('facet')).toEqual(['genre:Fantasy']);
    expect(params.get('facet_logic')).toBe('and');
    expect(params.has('sort')).toBe(false);
    expect(params.has('size')).toBe(false);
  });

  it('does not emit facet parameters for an empty selection', () => {
    const params = toCollectionHttpParams(normalizeBookCollectionFilterParams({
      facets: {},
      facetLogic: 'or',
    }));

    expect(params.has('facet')).toBe(false);
  });

  it('includes sort but excludes size from ID requests', () => {
    const params = toIdsHttpParams(normalizeBookQueryParams({
      facets: {},
      facetLogic: 'or',
      sort: [{key: 'title', direction: 'desc'}],
    }));

    expect(params.get('sort')).toBe('-title');
    expect(params.has('size')).toBe(false);
  });
});
