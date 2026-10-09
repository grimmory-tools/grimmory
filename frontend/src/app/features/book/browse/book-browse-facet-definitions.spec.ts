import {describe, expect, it} from 'vitest';

import {browseFilterGroups, withBrowseFacetRange} from '../../../shared/browse/facets';
import {type BrowseFacetGroup} from '../../../core/data/browse.models';
import {bookFacetDefinitions} from './book-browse-facet-definitions';

function group(key: string, values: [string, number][], complete = true): BrowseFacetGroup {
  return {
    key,
    values: values.map(([value, count]) => ({value, title: value, count})),
    complete,
  };
}

const definitions = bookFacetDefinitions({
  shelf: () => undefined,
  library: () => undefined,
  translate: (key: string) => key,
});

const AVAILABLE = new Set(['genre']);

describe('book browse facets', () => {
  it('lists the served values in server order, followed by unserved picks', () => {
    const genres = browseFilterGroups(
      AVAILABLE,
      [group('genre', [['Drama', 90], ['Gothic', 5]])],
      definitions,
      {genre: ['Comedy', 'Farce']},
    );
    expect(genres[0].values.map(item => [item.value, item.count]))
      .toEqual([['Drama', 90], ['Gothic', 5], ['Comedy', 0], ['Farce', 0]]);
  });

  it('leaves the count of an unserved pick unknown when the list is cut off', () => {
    const authors = browseFilterGroups(
      AVAILABLE,
      [group('author', [['Pratchett', 12]], false)],
      definitions,
      {author: ['Lindbergh']},
    ).find(item => item.key === 'author')!;
    expect(authors.values.map(item => [item.value, item.count]))
      .toEqual([['Pratchett', 12], ['Lindbergh', null]]);
  });

  it('replaces a numeric range token rather than stacking it, and keeps band selections', () => {
    let selection = withBrowseFacetRange({}, 'page_count', 100, 400, new Set());
    expect(selection).toEqual({page_count: ['100..400']});
    selection = withBrowseFacetRange(selection, 'page_count', null, 200, new Set());
    expect(selection).toEqual({page_count: ['*..200']});
    expect(withBrowseFacetRange(selection, 'page_count', null, null, new Set())).toEqual({});
    expect(withBrowseFacetRange({match_score: ['70..80', '10..20']}, 'match_score', 30, 90, new Set(['70..80'])))
      .toEqual({match_score: ['70..80', '30..90']});
  });
});
