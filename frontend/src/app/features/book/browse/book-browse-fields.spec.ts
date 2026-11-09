import {describe, expect, it} from 'vitest';

import {
  COLUMNS_BY_KEY,
  SORT_FIELDS,
  filterProviderSpecificFields,
  isFacetKeyEnabled,
  isProviderFieldKeyEnabled,
  isProviderSpecificFieldEnabled,
  type MetadataProviderSpecificFields,
} from './book-browse-fields';

const amazonRatingField = SORT_FIELDS.get('amazonRating')!;
const amazonReviewCountField = SORT_FIELDS.get('amazonReviewCount')!;
const goodreadsRatingField = COLUMNS_BY_KEY.get('goodreadsRating')!;
const personalRatingField = SORT_FIELDS.get('personalRating')!;

function allEnabled(): Partial<MetadataProviderSpecificFields> {
  return {};
}

describe('book browse provider-specific field filtering', () => {
  it('excludes provider-specific rating fields when the setting disables them', () => {
    expect(isProviderSpecificFieldEnabled(amazonRatingField, {amazonRating: false})).toBe(false);
    expect(isProviderFieldKeyEnabled('amazonReviewCount', {amazonReviewCount: false})).toBe(false);
    expect(isProviderSpecificFieldEnabled(goodreadsRatingField, {goodreadsRating: false})).toBe(false);
  });

  it('keeps provider-specific rating fields when the setting enables them', () => {
    expect(isProviderSpecificFieldEnabled(amazonRatingField, {amazonRating: true})).toBe(true);
    expect(isProviderSpecificFieldEnabled(amazonReviewCountField, {amazonReviewCount: true})).toBe(true);
    expect(isProviderFieldKeyEnabled('goodreadsRating', allEnabled())).toBe(true);
  });

  it('keeps standard metadata fields available when provider fields are disabled', () => {
    expect(isProviderSpecificFieldEnabled(personalRatingField, {amazonRating: false})).toBe(true);
    expect(isProviderFieldKeyEnabled('personalRating', {amazonRating: false})).toBe(true);
    expect(isProviderSpecificFieldEnabled(amazonRatingField, {})).toBe(true);
  });

  it('makes filter facet options respect the setting', () => {
    expect(isFacetKeyEnabled('goodreads_rating', {goodreadsRating: false})).toBe(false);
    expect(isFacetKeyEnabled('goodreads_rating', {goodreadsRating: true})).toBe(true);
    expect(isFacetKeyEnabled('amazon_rating', {amazonRating: false})).toBe(false);
    expect(isFacetKeyEnabled('genre', {goodreadsRating: false})).toBe(true);
    expect(isFacetKeyEnabled('personal_rating', {goodreadsRating: false})).toBe(true);
  });

  it('treats missing provider metadata as enabled (safe default)', () => {
    expect(isProviderSpecificFieldEnabled(amazonRatingField, {})).toBe(true);
    expect(isProviderFieldKeyEnabled('applebooksRating', {})).toBe(true);
    expect(isFacetKeyEnabled('douban_rating', {})).toBe(true);
    expect(filterProviderSpecificFields([...SORT_FIELDS.values()], {}).length).toBe(
      [...SORT_FIELDS.values()].filter(entry => 'sort' in entry).length,
    );
  });

  it('filters the registered field list by the setting', () => {
    const all = filterProviderSpecificFields([...SORT_FIELDS.values()], {});
    const disabled = filterProviderSpecificFields([...SORT_FIELDS.values()], {
      amazonRating: false,
      goodreadsRating: false,
    });
    expect(disabled.length).toBeLessThan(all.length);
    expect(disabled.every(field => field.sort?.key !== 'amazonRating')).toBe(true);
    expect(disabled.every(field => field.sort?.key !== 'goodreadsRating')).toBe(true);
    expect(disabled.some(field => field.sort?.key === 'personalRating')).toBe(true);
    expect(disabled.some(field => field.sort?.key === 'title')).toBe(true);
  });
});
