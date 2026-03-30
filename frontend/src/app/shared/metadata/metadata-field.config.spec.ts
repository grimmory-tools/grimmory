import {describe, expect, it} from 'vitest';

import {
  ALL_COMIC_METADATA_FIELDS,
  ALL_METADATA_FIELDS,
  COMIC_FORM_TO_MODEL_LOCK,
  getArrayFields,
  getBookDetailsFields,
  getBottomFields,
  getProviderFields,
  getSeriesFields,
  getTextareaFields,
  getTopFields
} from './metadata-field.config';

describe('metadata-field.config', () => {
  it('exposes stable grouped field selectors', () => {
    expect(getTopFields().map((field) => field.controlName)).toEqual([
      'title',
      'subtitle',
      'publisher',
      'publishedDate'
    ]);
    expect(getSeriesFields().map((field) => field.controlName)).toEqual([
      'seriesName',
      'seriesNumber',
      'seriesTotal'
    ]);
    expect(getBookDetailsFields().map((field) => field.controlName)).toEqual([
      'language',
      'isbn10',
      'isbn13',
      'pageCount'
    ]);
  });

  it('splits array and textarea metadata fields correctly', () => {
    expect(getArrayFields().map((field) => field.controlName)).toEqual(['authors', 'categories', 'moods', 'tags']);
    expect(getTextareaFields().map((field) => field.controlName)).toEqual(['description']);
  });

  it('filters provider-backed fields when provider flags are supplied', () => {
    const enabledFields = {
      googleId: true,
      asin: false,
      amazonRating: false,
      amazonReviewCount: false,
      goodreadsId: false,
      goodreadsRating: false,
      goodreadsReviewCount: false,
      hardcoverId: false,
      hardcoverBookId: false,
      hardcoverRating: false,
      hardcoverReviewCount: false,
      comicvineId: false,
      lubimyczytacId: false,
      lubimyczytacRating: false,
      ranobedbId: false,
      ranobedbRating: false,
      audibleId: false,
      audibleRating: false,
      audibleReviewCount: false
    };

    expect(getProviderFields(enabledFields).map((field) => field.controlName)).toEqual(['googleId']);
    expect(getBottomFields(enabledFields).some((field) => field.controlName === 'googleId')).toBe(true);
  });

  it('keeps comic metadata fields and lock mappings aligned', () => {
    expect(ALL_COMIC_METADATA_FIELDS.length).toBeGreaterThan(0);
    expect(COMIC_FORM_TO_MODEL_LOCK['comicIssueNumberLocked']).toBe('issueNumberLocked');
    expect(ALL_METADATA_FIELDS.some((field) => field.controlName === 'title')).toBe(true);
  });
});
