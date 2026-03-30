import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {afterEach, describe, expect, it} from 'vitest';

import {BookService} from '../../book/service/book.service';
import {ReadStatus, type Book} from '../../book/model/book.model';
import {SeriesDataService} from './series-data.service';

function makeBook(partial: Partial<Book> & Pick<Book, 'id' | 'libraryId' | 'libraryName'>): Book {
  return {
    ...partial,
    libraryId: partial.libraryId,
    libraryName: partial.libraryName,
  } as Book;
}

describe('SeriesDataService', () => {
  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('groups books by normalized series name and derives summary fields', () => {
    const books = signal<Book[]>([
      makeBook({
        id: 1,
        libraryId: 1,
        libraryName: 'Lib',
        readStatus: ReadStatus.UNREAD,
        metadata: {
          bookId: 1,
          seriesName: '  Alpha  ',
          seriesNumber: 2,
          authors: ['Ada', 'Bert'],
          categories: ['Sci-Fi'],
        },
        lastReadTime: '2026-03-25T10:00:00Z',
        addedOn: '2026-03-25T09:00:00Z',
      }),
      makeBook({
        id: 2,
        libraryId: 1,
        libraryName: 'Lib',
        readStatus: ReadStatus.READ,
        metadata: {
          bookId: 2,
          seriesName: 'alpha',
          seriesNumber: 1,
          authors: ['Bert', 'Cy'],
          categories: ['Fantasy', 'Sci-Fi'],
        },
        lastReadTime: '2026-03-26T10:00:00Z',
        addedOn: '2026-03-26T09:00:00Z',
      }),
    ]);

    TestBed.configureTestingModule({
      providers: [
        SeriesDataService,
        {provide: BookService, useValue: {books: books.asReadonly()}},
      ],
    });

    const service = TestBed.inject(SeriesDataService);
    const summaries = service.allSeries();

    expect(summaries).toHaveLength(1);
    expect(summaries[0]).toMatchObject({
      seriesName: 'alpha',
      bookCount: 2,
      readCount: 1,
      progress: 0.5,
      seriesStatus: ReadStatus.PARTIALLY_READ,
      lastReadTime: '2026-03-26T10:00:00Z',
      addedOn: '2026-03-26T09:00:00Z',
    });
    expect(summaries[0].books.map(book => book.id)).toEqual([2, 1]);
    expect(summaries[0].authors).toEqual(['Bert', 'Cy', 'Ada']);
    expect(summaries[0].categories).toEqual(['Fantasy', 'Sci-Fi']);
    expect(summaries[0].nextUnread?.id).toBe(1);
    expect(summaries[0].coverBooks.map(book => book.id)).toEqual([2, 1]);
  });

  it('skips books without a series and honors series status precedence', () => {
    const books = signal<Book[]>([
      makeBook({id: 10, libraryId: 1, libraryName: 'Lib', metadata: {bookId: 10}}),
      makeBook({
        id: 11,
        libraryId: 1,
        libraryName: 'Lib',
        readStatus: ReadStatus.WONT_READ,
        metadata: {bookId: 11, seriesName: 'Wont Read'},
      }),
      makeBook({
        id: 12,
        libraryId: 1,
        libraryName: 'Lib',
        readStatus: ReadStatus.ABANDONED,
        metadata: {bookId: 12, seriesName: 'Abandoned'},
      }),
      makeBook({
        id: 13,
        libraryId: 1,
        libraryName: 'Lib',
        readStatus: ReadStatus.READ,
        metadata: {bookId: 13, seriesName: 'Read'},
      }),
      makeBook({
        id: 14,
        libraryId: 1,
        libraryName: 'Lib',
        readStatus: ReadStatus.READING,
        metadata: {bookId: 14, seriesName: 'Reading'},
      }),
      makeBook({
        id: 15,
        libraryId: 1,
        libraryName: 'Lib',
        readStatus: ReadStatus.READ,
        metadata: {bookId: 15, seriesName: 'Partial'},
      }),
      makeBook({
        id: 16,
        libraryId: 1,
        libraryName: 'Lib',
        readStatus: ReadStatus.UNREAD,
        metadata: {bookId: 16, seriesName: 'Partial'},
      }),
      makeBook({
        id: 17,
        libraryId: 1,
        libraryName: 'Lib',
        readStatus: ReadStatus.UNREAD,
        metadata: {bookId: 17, seriesName: 'Unread'},
      }),
    ]);

    TestBed.configureTestingModule({
      providers: [
        SeriesDataService,
        {provide: BookService, useValue: {books: books.asReadonly()}},
      ],
    });

    const service = TestBed.inject(SeriesDataService);
    const summaries = Object.fromEntries(service.allSeries().map(summary => [summary.seriesName, summary.seriesStatus]));

    expect(summaries).toEqual({
      'Wont Read': ReadStatus.WONT_READ,
      'Abandoned': ReadStatus.ABANDONED,
      'Read': ReadStatus.READ,
      'Reading': ReadStatus.READING,
      'Partial': ReadStatus.PARTIALLY_READ,
      'Unread': ReadStatus.UNREAD,
    });
  });
});
