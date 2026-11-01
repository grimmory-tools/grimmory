import {describe, expect, it} from 'vitest';

import {browseCollectionKeys} from './browse-query-keys';
import {type BrowsePageParams} from './browse-query-params';

const isKey = (key: string): key is string => key !== '';

const PARAMS: BrowsePageParams<string, string> = {
  query: 'dune',
  facets: {genre: {include: ['Science Fiction'], exclude: []}},
  sort: [{key: 'title', direction: 'asc'}],
  size: 20,
};

describe('browseCollectionKeys', () => {
  it('normalizes the params and keeps membership identity apart from ordering identity', () => {
    const collection = browseCollectionKeys(PARAMS, isKey);
    const equivalent = browseCollectionKeys({
      ...PARAMS,
      query: '  dune  ',
      facets: {genre: {include: ['Science Fiction', 'Science Fiction'], exclude: []}},
    }, isKey);
    expect(equivalent.filtersKey).toBe(collection.filtersKey);
    expect(equivalent.listKey).toBe(collection.listKey);

    const largerPage: BrowsePageParams<string, string> = {...PARAMS, size: 50};
    const resized = browseCollectionKeys(largerPage, isKey);
    expect(resized.filtersKey).toBe(collection.filtersKey);
    expect(resized.listKey).toBe(collection.listKey);

    const reordered = browseCollectionKeys({...PARAMS, sort: [{key: 'title', direction: 'desc'}]}, isKey);
    expect(reordered.filtersKey).toBe(collection.filtersKey);
    expect(reordered.listKey).not.toBe(collection.listKey);

    const refiltered = browseCollectionKeys({...PARAMS, query: 'foundation'}, isKey);
    expect(refiltered.filtersKey).not.toBe(collection.filtersKey);
    expect(refiltered.listKey).not.toBe(collection.listKey);
  });
});
