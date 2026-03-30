import {describe, expect, expectTypeOf, it} from 'vitest';

import {ReadStatus} from '../../book/model/book.model';
import {SeriesSummary} from './series.model';

describe('series.model', () => {
  it('supports summary state for a series browser entry', () => {
    const summary: SeriesSummary = {
      seriesName: 'Earthsea',
      books: [],
      authors: ['Ursula K. Le Guin'],
      categories: ['Fantasy'],
      bookCount: 6,
      readCount: 4,
      progress: 66,
      seriesStatus: ReadStatus.READING,
      nextUnread: null,
      lastReadTime: null,
      coverBooks: [],
      addedOn: '2026-03-01'
    };

    expect(summary.seriesStatus).toBe(ReadStatus.READING);
    expect(summary.authors[0]).toContain('Le Guin');
    expectTypeOf(summary.nextUnread).toEqualTypeOf<SeriesSummary['nextUnread']>();
  });
});
