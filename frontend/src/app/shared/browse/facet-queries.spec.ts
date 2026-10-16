import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {describe, expect, it} from 'vitest';

import {createQueryClientHarness} from '../../core/testing/query-testing';
import {createBrowseFacetQueries, type BrowseFacetQueriesOptions} from './facet-queries';
import {type BrowseFacetSelection} from './facets';

type Key = 'genre' | 'has_photo' | 'has_asin';

const KEYS: readonly Key[] = ['genre', 'has_photo', 'has_asin'];

describe('createBrowseFacetQueries', () => {
  it('opens and closes the Metadata facets together, starting open when any is selected', () => {
    TestBed.configureTestingModule({providers: createQueryClientHarness().providers});
    const queries = TestBed.runInInjectionContext(() => createBrowseFacetQueries<Key>({
      source: {
        facetIndex: () => ({queryKey: ['index'], queryFn: () => new Promise(() => undefined)}),
        facet: (key: Key) => ({queryKey: ['facet', key], queryFn: () => new Promise(() => undefined)}),
      } as unknown as BrowseFacetQueriesOptions<Key>['source'],
      fields: {facetKeys: KEYS, openRailFacets: new Set<Key>()},
      definitions: signal({
        order: KEYS,
        isKey: (key): key is Key => KEYS.includes(key as Key),
        field: key => (key === 'genre' ? {labelKey: key} : {labelKey: key, kind: 'flag'}),
      }),
      selection: signal<BrowseFacetSelection<Key>>({has_asin: ['true']}),
      query: signal(''),
    }));

    expect([...queries.openKeys()]).toEqual(['has_photo', 'has_asin']);
    queries.setOpen({key: 'has_photo', open: false});
    expect([...queries.openKeys()]).toEqual([]);
    queries.setOpen({key: 'has_photo', open: true});
    expect([...queries.openKeys()]).toEqual(['has_photo', 'has_asin']);
  });
});
