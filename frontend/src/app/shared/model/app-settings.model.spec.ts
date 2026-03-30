import {describe, expect, expectTypeOf, it} from 'vitest';

import {
  AppSettingKey,
  AppSettings,
  MetadataMatchWeights,
  OidcTestResult
} from './app-settings.model';

describe('app-settings.model', () => {
  it('exposes stable keys for persisted app settings', () => {
    expect(AppSettingKey.AUTO_BOOK_SEARCH).toBe('AUTO_BOOK_SEARCH');
    expect(AppSettingKey.METADATA_PROVIDER_SETTINGS).toBe('METADATA_PROVIDER_SETTINGS');
    expect(AppSettingKey.OIDC_FORCE_ONLY_MODE).toBe('OIDC_FORCE_ONLY_MODE');
  });

  it('keeps metadata match weights numerically typed', () => {
    const weights: MetadataMatchWeights = {
      title: 10,
      subtitle: 5,
      description: 3,
      authors: 10,
      publisher: 2,
      publishedDate: 2,
      seriesName: 4,
      seriesNumber: 4,
      seriesTotal: 2,
      isbn13: 8,
      isbn10: 8,
      language: 2,
      pageCount: 1,
      categories: 2,
      amazonRating: 1,
      amazonReviewCount: 1,
      goodreadsRating: 1,
      goodreadsReviewCount: 1,
      hardcoverRating: 1,
      hardcoverReviewCount: 1,
      doubanRating: 1,
      doubanReviewCount: 1,
      lubimyczytacRating: 1,
      ranobedbRating: 1,
      audibleRating: 1,
      audibleReviewCount: 1,
      coverImage: 6
    };

    expect(weights.title).toBeGreaterThan(weights.coverImage);
    expectTypeOf(weights.coverImage).toEqualTypeOf<number>();
  });

  it('supports OIDC test results and app-setting contract typing', () => {
    const result: OidcTestResult = {
      success: false,
      checks: [
        {name: 'issuer', status: 'PASS', message: 'reachable'},
        {name: 'jwks', status: 'WARN', message: 'slow'}
      ]
    };

    expect(result.checks[1].status).toBe('WARN');
    expectTypeOf<AppSettings['diskType']>().toEqualTypeOf<string>();
    expectTypeOf<AppSettings['oidcSessionDurationHours']>().toEqualTypeOf<number | null>();
  });
});
