import {describe, expect, it} from 'vitest';

import {ReadStatus} from '../../book/model/book.model';
import {AppSeriesSummary} from '../model/series.model';
import {toApiSeriesSort, toSeriesSummary} from './series-data.service';

describe('SeriesDataService adapters', () => {
  it('maps app series summaries to browser summaries', () => {
    const appSummary: AppSeriesSummary = {
      seriesName: 'Dune',
      authors: ['Frank Herbert'],
      bookCount: 4,
      seriesTotal: 6,
      booksRead: 2,
      latestAddedOn: '2026-03-20T10:00:00Z',
      lastReadTime: '2026-03-21T10:00:00Z',
      seriesStatus: 'PARTIALLY_READ',
      coverBooks: [
        {
          bookId: 10,
          coverUpdatedOn: '2026-03-20T09:00:00Z',
          seriesNumber: 1,
          primaryFileType: 'EPUB',
        },
      ],
    };

    expect(toSeriesSummary(appSummary)).toEqual({
      seriesName: 'Dune',
      authors: ['Frank Herbert'],
      categories: [],
      bookCount: 4,
      readCount: 2,
      progress: 0.5,
      seriesStatus: ReadStatus.PARTIALLY_READ,
      lastReadTime: '2026-03-21T10:00:00Z',
      coverBooks: appSummary.coverBooks,
      addedOn: '2026-03-20T10:00:00Z',
    });
  });

  it('falls back to unread for unknown server statuses', () => {
    const summary = toSeriesSummary({
      seriesName: 'Unknown',
      authors: [],
      bookCount: 0,
      seriesTotal: null,
      booksRead: 0,
      latestAddedOn: null,
      lastReadTime: null,
      seriesStatus: 'SOMETHING_NEW',
      coverBooks: [],
    });

    expect(summary.seriesStatus).toBe(ReadStatus.UNREAD);
  });

  it('maps browser sort values to app API params', () => {
    expect(toApiSeriesSort('name-asc')).toEqual({field: 'name', dir: 'asc'});
    expect(toApiSeriesSort('name-desc')).toEqual({field: 'name', dir: 'desc'});
    expect(toApiSeriesSort('book-count')).toEqual({field: 'bookCount', dir: 'desc'});
    expect(toApiSeriesSort('progress')).toEqual({field: 'readProgress', dir: 'desc'});
    expect(toApiSeriesSort('recently-read')).toEqual({field: 'lastReadTime', dir: 'desc'});
    expect(toApiSeriesSort('recently-added')).toEqual({field: 'recentlyAdded', dir: 'desc'});
  });
});
