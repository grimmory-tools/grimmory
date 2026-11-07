import {describe, expect, it} from 'vitest';

import {bookSortOptions} from './book-browse-sort';

describe('book browse sort options', () => {
  it('offers only the sorts the server advertises, in the browser order', () => {
    const options = bookSortOptions([
      'pageCount', '-pageCount',
      'title', '-title',
      'seriesName', '-seriesName',
      'amazonReviewCount', '-amazonReviewCount',
    ]);

    expect(options.map(option => option.id)).toEqual(['title', 'seriesName', 'pageCount', 'amazonReviewCount']);
    expect(options[2]).toMatchObject({group: 'more', defaultDirection: 'desc', directions: ['asc', 'desc']});
    expect(bookSortOptions(['futureScore', '-title']).map(option => option.id)).toEqual(['title']);
    expect(bookSortOptions(['random', '-random'])).toMatchObject([{id: 'random', directions: ['asc']}]);
  });
});
