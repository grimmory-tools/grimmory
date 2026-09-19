import {describe, expect, expectTypeOf, it} from 'vitest';
import type {MetadataProviderId} from '../../../../shared/metadata/metadata-providers';

import {
  FieldOptions,
  FieldProvider,
  MetadataRefreshOptions,
  MetadataReplaceMode
} from './metadata-refresh-options.model';

const provider = (value: MetadataProviderId | null): FieldProvider => ({
  p4: value,
  p3: value,
  p2: value,
  p1: value
});

describe('metadata-refresh-options.model', () => {
  it('supports replace-mode driven refresh settings', () => {
    const fieldOptions: FieldOptions = {
      openlibraryId: provider('OpenLibrary'),
      title: provider('Google'),
      description: provider('Google'),
      authors: provider('Google'),
      categories: provider('Google'),
      cover: provider('Google'),
      subtitle: provider('Google'),
      publisher: provider('Google'),
      publishedDate: provider('Google'),
      seriesName: provider('Google'),
      seriesNumber: provider('Google'),
      seriesTotal: provider('Google'),
      isbn13: provider('Google'),
      isbn10: provider('Google'),
      language: provider('Google'),
      pageCount: provider('Google'),
      asin: provider('Google'),
      goodreadsId: provider('GoodReads'),
      comicvineId: provider(null),
      hardcoverId: provider('Hardcover'),
      hardcoverBookId: provider('Hardcover'),
      googleId: provider('Google'),
      lubimyczytacId: provider(null),
      amazonRating: provider('Amazon'),
      amazonReviewCount: provider('Amazon'),
      goodreadsRating: provider('GoodReads'),
      goodreadsReviewCount: provider('GoodReads'),
      hardcoverRating: provider('Hardcover'),
      hardcoverReviewCount: provider('Hardcover'),
      lubimyczytacRating: provider(null),
      ranobedbId: provider(null),
      ranobedbRating: provider(null),
      audibleId: provider('Audible'),
      audibleRating: provider('Audible'),
      audibleReviewCount: provider('Audible'),
      applebooksId: provider('AppleBooks'),
      applebooksRating: provider('AppleBooks'),
      applebooksReviewCount: provider('AppleBooks'),
      moods: provider('Google'),
      tags: provider('Google')
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
        openlibraryId: true,
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
        applebooksId: true,
        applebooksRating: true,
        applebooksReviewCount: true,
        moods: true,
        tags: true
      }
    };

    expect(options.fieldOptions?.title.p4).toBe('Google');
    expect(options.enabledFields?.subtitle).toBe(false);
    expectTypeOf(options.replaceMode).toEqualTypeOf<MetadataReplaceMode | undefined>();
  });
});
