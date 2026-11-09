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

  it('excludes provider-specific sort options when the setting disables them', () => {
    const options = bookSortOptions(
      ['amazonRating', '-amazonRating', 'title', '-title', 'amazonReviewCount', '-amazonReviewCount'],
      {amazonRating: false, amazonReviewCount: false},
    );
    expect(options.map(option => option.id)).toEqual(['title']);
  });

  it('keeps provider-specific sort options when the setting enables them', () => {
    const options = bookSortOptions(
      ['amazonRating', '-amazonRating', 'title', '-title'],
      {amazonRating: true},
    );
    expect(options.map(option => option.id)).toEqual(['title', 'amazonRating']);
  });

  it('keeps standard metadata sort options when provider sorts are disabled', () => {
    const options = bookSortOptions(
      ['personalRating', '-personalRating', 'title', '-title'],
      {amazonRating: false},
    );
    expect(options.map(option => option.id)).toEqual(['title', 'personalRating']);
  });

  it('treats missing provider metadata as enabled by default', () => {
    const options = bookSortOptions(['amazonRating', '-amazonRating', 'title', '-title']);
    expect(options.map(option => option.id)).toEqual(['title', 'amazonRating']);
  });
});
