import {describe, expect, it} from 'vitest';

import {bookColumnOptions, bookVisibleColumnOptions} from './book-browse-columns';

function columnPreferences(
  fields: readonly {field: string; visible: boolean; order: number}[],
): readonly {field: string; visible: boolean; order: number}[] {
  return fields;
}

describe('book browse table column options', () => {
  const basePreferences = columnPreferences([
    {field: 'title', visible: true, order: 0},
    {field: 'pageCount', visible: true, order: 1},
    {field: 'amazonRating', visible: true, order: 2},
    {field: 'amazonReviewCount', visible: true, order: 3},
    {field: 'goodreadsRating', visible: true, order: 4},
    {field: 'goodreadsReviewCount', visible: true, order: 5},
  ]);

  it('excludes provider-specific columns when the setting disables them', () => {
    const options = bookColumnOptions(basePreferences, {
      amazonRating: false,
      amazonReviewCount: false,
      goodreadsRating: false,
      goodreadsReviewCount: false,
    });
    const fields = options.map(option => option.field);
    expect(fields).not.toContain('amazonRating');
    expect(fields).not.toContain('amazonReviewCount');
    expect(fields).not.toContain('goodreadsRating');
    expect(fields).not.toContain('goodreadsReviewCount');
    expect(fields).toContain('title');
    expect(fields).toContain('pageCount');
  });

  it('keeps provider-specific columns when the setting enables them', () => {
    const options = bookColumnOptions(basePreferences, {amazonRating: true, goodreadsRating: true});
    const fields = options.map(option => option.field);
    expect(fields).toContain('amazonRating');
    expect(fields).toContain('goodreadsRating');
    expect(fields).toContain('title');
  });

  it('keeps standard metadata columns when provider columns are disabled', () => {
    const options = bookColumnOptions(basePreferences, {amazonRating: false});
    const fields = options.map(option => option.field);
    expect(fields).toContain('title');
    expect(fields).toContain('pageCount');
    expect(fields).not.toContain('amazonRating');
  });

  it('treats missing provider metadata as enabled by default', () => {
    const options = bookColumnOptions(basePreferences);
    const fields = options.map(option => option.field);
    expect(fields).toContain('amazonRating');
    expect(fields).toContain('title');
  });

  it('applies the setting to visible column options as well', () => {
    const options = bookVisibleColumnOptions(basePreferences, {amazonRating: false});
    const fields = options.map(option => option.field);
    expect(fields).not.toContain('amazonRating');
    expect(fields).toContain('title');
  });
});
