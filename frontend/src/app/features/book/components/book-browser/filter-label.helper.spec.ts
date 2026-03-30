import {describe, expect, it} from 'vitest';

import {FilterLabelHelper} from './filter-label.helper';

describe('FilterLabelHelper', () => {
  it('returns friendly names for known filter types and capitalizes unknown names', () => {
    expect(FilterLabelHelper.getFilterTypeName('personalRating')).toBe('Personal Rating');
    expect(FilterLabelHelper.getFilterTypeName('customFilter')).toBe('CustomFilter');
  });

  it('maps numeric ids to configured range labels', () => {
    expect(FilterLabelHelper.getFilterDisplayValue('fileSize', 1)).toBe('1–10 MB');
    expect(FilterLabelHelper.getFilterDisplayValue('pageCount', 3)).toBe('200–400 pages');
    expect(FilterLabelHelper.getFilterDisplayValue('matchScore', 0)).toBe('Outstanding (95–100%)');
    expect(FilterLabelHelper.getFilterDisplayValue('personalRating', 7)).toBe('7');
    expect(FilterLabelHelper.getFilterDisplayValue('amazonRating', 5)).toBe('4.5+');
  });

  it('falls back to the raw value when a numeric id is not recognized', () => {
    expect(FilterLabelHelper.getFilterDisplayValue('fileSize', 999)).toBe('999');
    expect(FilterLabelHelper.getFilterDisplayValue('tag', 'favorite')).toBe('favorite');
  });
});
