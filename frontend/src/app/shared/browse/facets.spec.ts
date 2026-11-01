import {describe, expect, it} from 'vitest';

import {withBrowseFacetMatchAll, withBrowseFacetState, type BrowseFacetSelection} from './facets';

type Key = 'genre' | 'tag';

describe('per-facet match all', () => {
  it('requires that facet\'s ticked values, adds new ticks as required, and unmarks them again', () => {
    const selection: BrowseFacetSelection<Key> = {genre: {include: ['Fantasy'], exclude: ['Romance']}, tag: {include: ['Dragons'], exclude: []}};

    const all = withBrowseFacetMatchAll<Key>(selection, 'genre', true);
    const ticked = withBrowseFacetState<Key>(withBrowseFacetState(all, 'genre', 'Horror', 'included', true), 'tag', 'Magic', 'included', false);

    expect(ticked).toEqual({genre: {include: ['Fantasy', 'Horror'], exclude: ['Romance'], matchAll: true}, tag: {include: ['Dragons', 'Magic'], exclude: []}});
    expect(withBrowseFacetMatchAll(ticked, 'genre', false)).toEqual({genre: {include: ['Fantasy', 'Horror'], exclude: ['Romance']}, tag: {include: ['Dragons', 'Magic'], exclude: []}});
  });
});
