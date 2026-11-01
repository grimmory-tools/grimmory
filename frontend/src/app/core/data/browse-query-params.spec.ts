import {describe, expect, it} from 'vitest';

import {
  facetQueryParams,
  normalizeBrowseCollectionFilterParams,
  normalizeBrowsePageParams,
  normalizeBrowseQueryParams,
  parseFacetParams,
  parseSortTermsToken,
  toCollectionHttpParams,
  toIdsHttpParams,
  toPageHttpParams,
} from './browse-query-params';

const isKey = (key: string): key is string => key !== '';
const isFacetKey = (key: string): key is 'genre' | 'tag' => key === 'genre' || key === 'tag';
const isSortKey = (key: string): key is 'title' | 'addedOn' => key === 'title' || key === 'addedOn';

describe('browse query parameters', () => {
  it('round-trips a selection through route params and drops unknown keys', () => {
    const selection = parseFacetParams([
      'genre:Comedy', 'genre:Drama', '+tag:Anthology', 'future_group:a:b', '__proto__:READ', 'genre:',
    ], isFacetKey);
    expect(selection).toEqual({
      genre: {include: ['Comedy', 'Drama'], exclude: []},
      tag: {include: ['Anthology'], exclude: [], matchAll: true},
    });
    expect(facetQueryParams(selection)).toEqual({facet: ['genre:Comedy', 'genre:Drama', '+tag:Anthology']});
    expect(facetQueryParams({})).toEqual({facet: null});
  });

  it('parses a sort token, dropping unknown and repeated keys', () => {
    expect(parseSortTermsToken('-addedOn, title,random,title', isSortKey)).toEqual([
      {key: 'addedOn', direction: 'desc'},
      {key: 'title', direction: 'asc'},
    ]);
    expect(parseSortTermsToken(null, isSortKey)).toEqual([]);
  });

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
