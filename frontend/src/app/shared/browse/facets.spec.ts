import {describe, expect, it} from 'vitest';

import {browseFacetMatchAll, setBrowseFacetValue, withBrowseFacetMatchAll, type BrowseFacetSelection} from './facets';

type Key = 'genre' | 'tag';

describe('per-section match all', () => {
  it('marks only that section\'s included values as required, and unmarks them again', () => {
    const selection: BrowseFacetSelection<Key> = {genre: ['Fantasy', 'Horror'], '-genre': ['Romance'], tag: ['Dragons', 'Magic']};

    const all = withBrowseFacetMatchAll<Key>(selection, 'genre', true);

    expect(all).toEqual({'+genre': ['Fantasy', 'Horror'], '-genre': ['Romance'], tag: ['Dragons', 'Magic']});
    expect(browseFacetMatchAll(all, 'genre')).toBe(true);
    expect(browseFacetMatchAll<Key>(all, 'tag')).toBe(false);
    expect(withBrowseFacetMatchAll(all, 'genre', false)).toEqual(selection);
  });

  it('adds a newly ticked value as required only in a section set to all', () => {
    const selection: BrowseFacetSelection<Key> = {'+genre': ['Fantasy'], tag: ['Dragons']};

    const next = setBrowseFacetValue<Key>(setBrowseFacetValue(selection, 'genre', 'Horror', 'included', true), 'tag', 'Magic', 'included', false);

    expect(next).toEqual({'+genre': ['Fantasy', 'Horror'], tag: ['Dragons', 'Magic']});
  });
});
