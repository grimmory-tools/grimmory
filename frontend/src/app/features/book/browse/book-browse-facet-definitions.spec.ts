import {describe, expect, it} from 'vitest';

import {browseFilterGroups, browseFrozenFacetOrders, withBrowseFacetRange} from '../../../shared/browse/facets';
import {type BrowseFacetGroup} from '../../../core/data/browse.models';
import {bookFacetDefinitions} from './book-browse-facet-definitions';

function group(key: string, values: [string, number][]): BrowseFacetGroup {
  return {
    key,
    title: key,
    values: values.map(([value, count]) => ({value, title: value, count, selected: false})),
  };
}

const definitions = bookFacetDefinitions({
  shelf: () => undefined,
  library: () => undefined,
  translate: (key: string) => key,
});

describe('book browse facets', () => {
  it('keeps the frozen value order when the server re-ranks, narrows or grows a group', () => {
    const frozen = browseFrozenFacetOrders([group('genre', [['Gothic', 40], ['Comedy', 30], ['Drama', 20]])], definitions);

    const reranked = browseFilterGroups(
      [group('genre', [['Drama', 90], ['Gothic', 5], ['Comedy', 2]])],
      frozen,
      definitions,
      {},
    );
    expect(reranked[0].values.map(item => [item.value, item.count]))
      .toEqual([['Gothic', 5], ['Comedy', 2], ['Drama', 90]]);

    const narrowed = browseFilterGroups([group('genre', [['Comedy', 7]])], frozen, definitions, {});
    expect(narrowed[0].values.map(item => [item.value, item.count]))
      .toEqual([['Comedy', 7], ['Gothic', 0], ['Drama', 0]]);

    const selectedAtZero = browseFilterGroups([group('genre', [['Comedy', 7], ['Gothic', 0]])], frozen, definitions, {genre: ['Gothic']});
    expect(selectedAtZero[0].values.map(item => item.value)).toEqual(['Gothic', 'Comedy', 'Drama']);

    const grown = browseFilterGroups(
      [group('genre', [['Farce', 3], ['Gothic', 40], ['Comedy', 30], ['Drama', 20]])],
      frozen,
      definitions,
      {},
    );
    expect(grown[0].values.map(item => item.value)).toEqual(['Gothic', 'Comedy', 'Drama', 'Farce']);
  });

  it('replaces a numeric range token rather than stacking it, and keeps band selections', () => {
    let selection = withBrowseFacetRange({}, 'page_count', 100, 400, definitions);
    expect(selection).toEqual({page_count: ['100..400']});
    selection = withBrowseFacetRange(selection, 'page_count', null, 200, definitions);
    expect(selection).toEqual({page_count: ['*..200']});
    expect(withBrowseFacetRange(selection, 'page_count', null, null, definitions)).toEqual({});
    expect(withBrowseFacetRange({match_score: ['70..80']}, 'match_score', 30, 90, definitions))
      .toEqual({match_score: ['70..80', '30..90']});
  });
});
