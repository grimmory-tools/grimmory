import {describe, expect, it} from 'vitest';

import {
  normalizeBrowseCollectionFilterParams,
  normalizeBrowsePageParams,
  normalizeBrowseQueryParams,
  toCollectionHttpParams,
  toIdsHttpParams,
  toPageHttpParams,
} from './browse-query-params';

const isKey = (key: string): key is string => key !== '';

describe('browse query parameters', () => {
  it('normalizes equivalent queries and facet selections', () => {
    const first = normalizeBrowsePageParams({
      query: '  dune  ',
      facets: {
        language: {include: [' French ', 'English'], exclude: []},
        genre: {include: [' Science Fiction ', 'Fantasy', 'Science Fiction', ' '], exclude: []},
      },
      sort: [{key: 'title', direction: 'asc'}],
      size: 40,
    }, isKey);
    const second = normalizeBrowsePageParams({
      query: 'dune',
      facets: {
        genre: {include: ['Fantasy', 'Science Fiction'], exclude: []},
        language: {include: ['English', 'French'], exclude: []},
      },
      sort: [{key: 'title', direction: 'asc'}],
      size: 40,
    }, isKey);

    expect(first).toEqual(second);
    expect(first.facets).toEqual({
      genre: {include: ['Fantasy', 'Science Fiction'], exclude: []},
      language: {include: ['English', 'French'], exclude: []},
    });
  });

  it('passes an empty sort through without imposing a default', () => {
    const normalized = normalizeBrowseQueryParams({
      facets: {},
      sort: [],
    }, isKey);

    expect(normalized.sort).toEqual([]);
  });

  it('serializes page parameters using the backend vocabulary', () => {
    const params = toPageHttpParams(normalizeBrowsePageParams({
      query: 'dune',
      facets: {
        genre: {include: ['Science Fiction'], exclude: ['Romance']},
        shelf: {include: ['magic:12'], exclude: [], matchAll: true},
      },
      sort: [
        {key: 'seriesName', direction: 'asc'},
        {key: 'seriesNumber', direction: 'desc'},
      ],
      size: 50,
    }, isKey));

    expect(params.get('query')).toBe('dune');
    expect(params.getAll('facet')).toEqual(['genre:Science Fiction', '-genre:Romance', '+shelf:magic:12']);
    expect(params.get('sort')).toBe('seriesName,-seriesNumber');
    expect(params.get('size')).toBe('50');
    expect(params.has('page')).toBe(false);
  });

  it('excludes sort and size from facet requests', () => {
    const params = toCollectionHttpParams(normalizeBrowseCollectionFilterParams({
      query: 'dune',
      facets: {genre: {include: ['Fantasy'], exclude: []}},
    }, isKey));

    expect(params.get('query')).toBe('dune');
    expect(params.getAll('facet')).toEqual(['genre:Fantasy']);
    expect(params.has('sort')).toBe(false);
    expect(params.has('size')).toBe(false);
  });

  it('does not emit facet parameters for an empty selection', () => {
    const params = toCollectionHttpParams(normalizeBrowseCollectionFilterParams({
      facets: {},
    }, isKey));

    expect(params.has('facet')).toBe(false);
  });

  it('includes sort but excludes size from ID requests', () => {
    const params = toIdsHttpParams(normalizeBrowseQueryParams({
      facets: {},
      sort: [{key: 'title', direction: 'desc'}],
    }, isKey));

    expect(params.get('sort')).toBe('-title');
    expect(params.has('size')).toBe(false);
  });
});
