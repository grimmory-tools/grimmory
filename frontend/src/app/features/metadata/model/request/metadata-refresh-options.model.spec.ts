import {describe, expect, expectTypeOf, it} from 'vitest';

import {
  FieldOptions,
  FieldProvider,
  MetadataRefreshOptions,
  MetadataReplaceMode
} from './metadata-refresh-options.model';

const provider = (value: string | null): FieldProvider => ({
  p4: value,
  p3: value,
  p2: value,
  p1: value
});

describe('metadata-refresh-options.model', () => {
  it('supports replace-mode driven refresh settings', () => {
    const fieldOptions: FieldOptions = {
      title: provider('google'),
      description: provider('google'),
      authors: provider('google'),
      categories: provider('google'),
      cover: provider('google'),
      subtitle: provider('google'),
      publisher: provider('google'),
      publishedDate: provider('google'),
      seriesName: provider('google'),
      seriesNumber: provider('google'),
      seriesTotal: provider('google'),
      isbn13: provider('google'),
      isbn10: provider('google'),
      language: provider('google'),
      pageCount: provider('google'),
      asin: provider('google'),
      goodreadsId: provider('goodreads'),
      comicvineId: provider(null),
      hardcoverId: provider('hardcover'),
      hardcoverBookId: provider('hardcover'),
      googleId: provider('google'),
      lubimyczytacId: provider(null),
      amazonRating: provider('amazon'),
      amazonReviewCount: provider('amazon'),
      goodreadsRating: provider('goodreads'),
      goodreadsReviewCount: provider('goodreads'),
      hardcoverRating: provider('hardcover'),
      hardcoverReviewCount: provider('hardcover'),
      lubimyczytacRating: provider(null),
      ranobedbId: provider(null),
      ranobedbRating: provider(null),
      audibleId: provider('audible'),
      audibleRating: provider('audible'),
      audibleReviewCount: provider('audible'),
      moods: provider('google'),
      tags: provider('google')
    };

    const options: MetadataRefreshOptions = {
      libraryId: 3,
      refreshCovers: true,
      mergeCategories: false,
      reviewBeforeApply: true,
      replaceMode: 'REPLACE_MISSING',
      fieldOptions,
      enabledFields: {
        title: true,
        description: true,
        authors: true,
        categories: true,
        cover: true,
        subtitle: false,
        publisher: true,
        publishedDate: true,
        seriesName: true,
        seriesNumber: true,
        seriesTotal: true,
        isbn13: true,
        isbn10: true,
        language: true,
        pageCount: true,
        asin: true,
        goodreadsId: true,
        comicvineId: false,
        hardcoverId: true,
        hardcoverBookId: true,
        googleId: true,
        lubimyczytacId: false,
        amazonRating: true,
        amazonReviewCount: true,
        goodreadsRating: true,
        goodreadsReviewCount: true,
        hardcoverRating: true,
        hardcoverReviewCount: true,
        lubimyczytacRating: false,
        ranobedbId: false,
        ranobedbRating: false,
        audibleId: true,
        audibleRating: true,
        audibleReviewCount: true,
        moods: true,
        tags: true
      }
    };

    expect(options.fieldOptions?.title.p4).toBe('google');
    expect(options.enabledFields?.subtitle).toBe(false);
    expectTypeOf(options.replaceMode).toEqualTypeOf<MetadataReplaceMode | undefined>();
  });
});
