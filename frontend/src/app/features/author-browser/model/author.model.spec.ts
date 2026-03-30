import {describe, expect, expectTypeOf, it} from 'vitest';

import {
  AuthorDetails,
  AuthorFilters,
  AuthorMatchRequest,
  AuthorSummary,
  AuthorUpdateRequest,
  DEFAULT_AUTHOR_FILTERS,
  EnrichedAuthor
} from './author.model';

describe('author.model', () => {
  it('provides all-author defaults for author filtering', () => {
    expect(DEFAULT_AUTHOR_FILTERS).toEqual({
      matchStatus: 'all',
      photoStatus: 'all',
      readStatus: 'all',
      bookCount: 'all',
      library: 'all',
      genre: 'all'
    } satisfies AuthorFilters);
  });

  it('supports enriched authors extending the summary shape', () => {
    const enriched: EnrichedAuthor = {
      id: 7,
      name: 'Ursula K. Le Guin',
      bookCount: 12,
      hasPhoto: true,
      libraryIds: new Set([1, 2]),
      libraryNames: ['Main', 'Archive'],
      categories: ['Fantasy', 'Sci-Fi'],
      readStatus: 'some-read',
      hasSeries: true,
      seriesCount: 2,
      latestAddedOn: '2026-03-26',
      lastReadTime: '2026-03-25T20:15:00Z',
      readingProgress: 75,
      avgPersonalRating: 4.5
    };

    expect(enriched.libraryIds.has(2)).toBe(true);
    expect(enriched.readStatus).toBe('some-read');
    expectTypeOf(enriched).toMatchTypeOf<AuthorSummary>();
  });

  it('keeps author request and update payloads structurally typed', () => {
    const matchRequest: AuthorMatchRequest = {
      source: 'hardcover',
      asin: 'B001234',
      region: 'US'
    };
    const updateRequest: AuthorUpdateRequest = {
      name: 'New Name',
      descriptionLocked: true
    };
    const details: AuthorDetails = {
      id: 4,
      name: 'Author',
      nameLocked: false,
      descriptionLocked: true,
      asinLocked: false,
      photoLocked: false
    };

    expect(matchRequest.region).toBe('US');
    expect(updateRequest.descriptionLocked).toBe(true);
    expect(details.nameLocked).toBe(false);
    expectTypeOf(updateRequest.name).toEqualTypeOf<string | undefined>();
  });
});
