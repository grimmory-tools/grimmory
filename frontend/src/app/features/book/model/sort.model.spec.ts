import {describe, expect, expectTypeOf, it} from 'vitest';

import {SortDirection, SortOption} from './sort.model';

describe('sort.model', () => {
  it('exposes the supported sort directions', () => {
    expect(SortDirection).toEqual({
      ASCENDING: 'ASCENDING',
      DESCENDING: 'DESCENDING'
    });
  });

  it('keeps sort options structurally typed', () => {
    const sortOption: SortOption = {
      label: 'Title',
      field: 'title',
      direction: SortDirection.ASCENDING
    };

    expect(sortOption.field).toBe('title');
    expectTypeOf(sortOption.direction).toEqualTypeOf<SortDirection>();
  });
});
