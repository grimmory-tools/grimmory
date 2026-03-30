import {describe, expect, it} from 'vitest';

import {
  AGE_RATING_OPTIONS,
  CONTENT_RATINGS,
  ContentRating,
  ContentRestriction,
  ContentRestrictionMode,
  ContentRestrictionType
} from './content-restriction.model';

describe('content-restriction.model', () => {
  it('exposes supported restriction enums and derived content ratings', () => {
    expect(ContentRestrictionType.CATEGORY).toBe('CATEGORY');
    expect(ContentRestrictionMode.ALLOW_ONLY).toBe('ALLOW_ONLY');
    expect(CONTENT_RATINGS).toEqual(Object.values(ContentRating));
  });

  it('defines age rating options in ascending order', () => {
    expect(AGE_RATING_OPTIONS[0]).toEqual({value: '0', label: 'All Ages'});
    expect(AGE_RATING_OPTIONS.at(-1)).toEqual({value: '21', label: '21+'});
  });

  it('supports persisted restriction records', () => {
    const restriction: ContentRestriction = {
      id: 1,
      userId: 99,
      restrictionType: ContentRestrictionType.TAG,
      mode: ContentRestrictionMode.EXCLUDE,
      value: 'spoilers',
      createdAt: '2026-03-26T10:00:00Z'
    };

    expect(restriction.value).toBe('spoilers');
    expect(restriction.mode).toBe(ContentRestrictionMode.EXCLUDE);
  });
});
