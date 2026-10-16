import {describe, expect, it} from 'vitest';

import {
  normalizeBrowseCollectionFilterParams,
  normalizeBrowsePageParams,
  normalizeBrowseQueryParams,
  toCollectionHttpParams,
  toIdsHttpParams,
  toPageHttpParams,
} from './browse-query-params';

describe('browse query parameters', () => {
  it('normalizes equivalent queries and facet selections', () => {
    const first = normalizeBrowsePageParams({
      query: '  dune  ',
      facets: {
        language: [' French ', 'English'],
        genre: [' Science Fiction ', 'Fantasy', 'Science Fiction', ' '],
      },
      sort: [{key: 'title', direction: 'asc'}],
      size: 40,
    });
    const second = normalizeBrowsePageParams({
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

  it('passes an empty sort through without imposing a default', () => {
    const normalized = normalizeBrowseQueryParams({
      facets: {},
      sort: [],
    });

    expect(normalized.sort).toEqual([]);
  });

  it('serializes page parameters using the backend vocabulary', () => {
    const params = toPageHttpParams(normalizeBrowsePageParams({
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
    const params = toCollectionHttpParams(normalizeBrowseCollectionFilterParams({
      query: 'dune',
      facets: {genre: ['Fantasy']},
    }));

    expect(params.get('query')).toBe('dune');
    expect(params.getAll('facet')).toEqual(['genre:Fantasy']);
    expect(params.has('sort')).toBe(false);
    expect(params.has('size')).toBe(false);
  });

  it('does not emit facet parameters for an empty selection', () => {
    const params = toCollectionHttpParams(normalizeBrowseCollectionFilterParams({
      facets: {},
    }));

    expect(params.has('facet')).toBe(false);
  });

  it('includes sort but excludes size from ID requests', () => {
    const params = toIdsHttpParams(normalizeBrowseQueryParams({
      facets: {},
      sort: [{key: 'title', direction: 'desc'}],
    }));

    expect(params.get('sort')).toBe('-title');
    expect(params.has('size')).toBe(false);
  });
});
