import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';

import {LibrariesSummaryService} from './libraries-summary.service';
import {LibraryFilterService} from './library-filter.service';
import {BookService} from '../../../../book/service/book.service';
import {Book} from '../../../../book/model/book.model';

describe('LibrariesSummaryService', () => {
  const books = signal<Book[]>([]);
  const selectedLibrary = signal<number | null>(null);

  beforeEach(() => {
    books.set([]);
    selectedLibrary.set(null);

    TestBed.configureTestingModule({
      providers: [
        LibrariesSummaryService,
        {provide: BookService, useValue: {books}},
        {provide: LibraryFilterService, useValue: {selectedLibrary}},
      ]
    });
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('returns zeroed totals and zero size for an empty library set', () => {
    const service = TestBed.inject(LibrariesSummaryService);

    expect(service.booksSummary()).toEqual({
      totalBooks: 0,
      totalSizeKb: 0,
      totalAuthors: 0,
      totalSeries: 0,
      totalPublishers: 0,
    });
    expect(service.formattedSize()).toBe('0 KB');
  });

  it('aggregates totals for the selected library and formats megabytes', () => {
    books.set([
      {
        id: 1,
        libraryId: 1,
        libraryName: 'Alpha',
        fileSizeKb: 1024,
        metadata: {
          bookId: 1,
          authors: ['Alice', 'Bob'],
          seriesName: 'Series A',
          publisher: 'Publisher X',
        },
      } as Book,
      {
        id: 2,
        libraryId: 1,
        libraryName: 'Alpha',
        fileSizeKb: 1024,
        metadata: {
          bookId: 2,
          authors: ['Alice', 'Carol'],
          publisher: 'Publisher X',
        },
      } as Book,
      {
        id: 3,
        libraryId: 2,
        libraryName: 'Beta',
        fileSizeKb: 10240,
        metadata: {
          bookId: 3,
          authors: ['Dave'],
        },
      } as Book,
    ]);
    selectedLibrary.set(1);

    const service = TestBed.inject(LibrariesSummaryService);

    expect(service.booksSummary()).toEqual({
      totalBooks: 2,
      totalSizeKb: 2048,
      totalAuthors: 3,
      totalSeries: 1,
      totalPublishers: 1,
    });
    expect(service.formattedSize()).toBe('2.00 MB');
  });

  it('uses primary file size when the book does not expose a top-level file size', () => {
    books.set([
      {
        id: 1,
        libraryId: 1,
        libraryName: 'Alpha',
        primaryFile: {
          bookId: 1,
          fileSizeKb: 2048,
        },
        metadata: {
          bookId: 1,
          authors: ['Alice'],
        },
      } as Book,
    ]);

    const service = TestBed.inject(LibrariesSummaryService);

    expect(service.booksSummary()).toEqual({
      totalBooks: 1,
      totalSizeKb: 2048,
      totalAuthors: 1,
      totalSeries: 0,
      totalPublishers: 0,
    });
    expect(service.formattedSize()).toBe('2.00 MB');
  });

});
