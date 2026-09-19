import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {QueryClient} from '@tanstack/angular-query-experimental';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {ResetProgressTypes} from '../../../shared/constants/reset-progress-type';
import {Book, ReadStatus} from '../model/book.model';
import {BOOKS_QUERY_KEY} from './book-query-keys';
import {BookPatchService} from './book-patch.service';

function buildBook(id: number, overrides: Partial<Book> = {}): Book {
  return {
    id,
    libraryId: 1,
    libraryName: 'Library',
    ...overrides,
  };
}

describe('BookPatchService', () => {
  let service: BookPatchService;
  let httpTestingController: HttpTestingController;
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient();

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        BookPatchService,
        {provide: QueryClient, useValue: queryClient},
      ],
    });

    service = TestBed.inject(BookPatchService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    queryClient.clear();
    TestBed.resetTestingModule();
  });

  function cachedBook(id: number): Book {
    return queryClient.getQueryData<Book[]>(BOOKS_QUERY_KEY)!.find(book => book.id === id)!;
  }

  it('posts PDF progress with file progress and patches the cached book', () => {
    queryClient.setQueryData<Book[]>(BOOKS_QUERY_KEY, [buildBook(11, {pdfProgress: {page: 1, percentage: 2}})]);

    service.savePdfProgress(11, 23, 74, 88).subscribe();

    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/books/progress'));
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      bookId: 11,
      pdfProgress: {
        page: 23,
        percentage: 74,
      },
      fileProgress: {
        bookFileId: 88,
        positionData: '23',
        progressPercent: 74,
      },
    });
    request.flush(null);

    expect(cachedBook(11).pdfProgress).toEqual({page: 23, percentage: 74});
  });

  it('deduplicates identical EPUB progress updates before posting', () => {
    queryClient.setQueryData<Book[]>(BOOKS_QUERY_KEY, [buildBook(7)]);

    service.saveEpubProgress(7, 'epubcfi(/6/2)', 'chapter-1.xhtml', 15, 31);
    service.saveEpubProgress(7, 'epubcfi(/6/2)', 'chapter-1.xhtml', 15, 31);

    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/books/progress'));
    expect(request.request.body).toEqual({
      bookId: 7,
      epubProgress: {
        cfi: 'epubcfi(/6/2)',
        href: 'chapter-1.xhtml',
        percentage: 15,
      },
      fileProgress: {
        bookFileId: 31,
        positionData: 'epubcfi(/6/2)',
        positionHref: 'chapter-1.xhtml',
        progressPercent: 15,
      },
    });
    request.flush(null);

    httpTestingController.expectNone(req => req.url.endsWith('/api/v1/books/progress'));
    expect(cachedBook(7).epubProgress).toEqual({cfi: 'epubcfi(/6/2)', percentage: 15});
  });

  it('clears only kobo progress on a kobo reset, ignoring the fabricated status fields', () => {
    queryClient.setQueryData<Book[]>(BOOKS_QUERY_KEY, [
      buildBook(1, {
        koboProgress: {percentage: 40},
        epubProgress: {cfi: 'epubcfi(/6/2)', percentage: 15},
        readStatus: ReadStatus.READING,
        lastReadTime: '2026-02-01T00:00:00Z',
      }),
    ]);

    service.resetProgress([1], ResetProgressTypes.KOBO).subscribe();

    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/books/reset-progress'));
    expect(request.request.method).toBe('POST');
    expect(request.request.params.get('type')).toBe(ResetProgressTypes.KOBO);
    expect(request.request.body).toEqual([1]);
    request.flush([
      {bookId: 1, readStatus: null, readStatusModifiedTime: null, dateFinished: null},
    ]);

    const book = cachedBook(1);
    expect(book.koboProgress).toBeUndefined();
    expect(book.epubProgress).toEqual({cfi: 'epubcfi(/6/2)', percentage: 15});
    expect(book.readStatus).toBe(ReadStatus.READING);
    expect(book.lastReadTime).toBe('2026-02-01T00:00:00Z');
  });

  it('clears reading progress and applies the returned status on a grimmory reset', () => {
    queryClient.setQueryData<Book[]>(BOOKS_QUERY_KEY, [
      buildBook(1, {
        epubProgress: {cfi: 'epubcfi(/6/2)', percentage: 15},
        koboProgress: {percentage: 40},
        readStatus: ReadStatus.READ,
        dateFinished: '2026-01-01',
        lastReadTime: '2026-02-01T00:00:00Z',
      }),
    ]);

    service.resetProgress([1], ResetProgressTypes.GRIMMORY).subscribe();

    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/books/reset-progress'));
    expect(request.request.params.get('type')).toBe(ResetProgressTypes.GRIMMORY);
    request.flush([
      {bookId: 1, readStatus: ReadStatus.UNREAD, readStatusModifiedTime: '2026-03-01T00:00:00Z', dateFinished: null},
    ]);

    const book = cachedBook(1);
    expect(book.epubProgress).toBeUndefined();
    expect(book.koboProgress).toEqual({percentage: 40});
    expect(book.readStatus).toBe(ReadStatus.UNREAD);
    expect(book['readStatusModifiedTime']).toBe('2026-03-01T00:00:00Z');
    expect(book.dateFinished).toBeNull();
    expect(book.lastReadTime).toBeUndefined();
  });

  it('updates cached date finished after the backend accepts the change', () => {
    queryClient.setQueryData<Book[]>(BOOKS_QUERY_KEY, [buildBook(5, {dateFinished: '2026-01-01'})]);

    service.updateDateFinished(5, '2026-03-10').subscribe();

    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/books/progress'));
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      bookId: 5,
      dateFinished: '2026-03-10',
    });
    request.flush(null);

    expect(cachedBook(5).dateFinished).toBe('2026-03-10');
  });

  it('updates the read status for multiple books and patches cached fields', () => {
    queryClient.setQueryData<Book[]>(BOOKS_QUERY_KEY, [buildBook(1), buildBook(2)]);

    service.updateBookReadStatus([1, 2], ReadStatus.READ).subscribe();

    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/books/status'));
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      bookIds: [1, 2],
      status: ReadStatus.READ,
    });
    request.flush([
      {bookId: 1, readStatus: ReadStatus.READ, readStatusModifiedTime: '2026-03-04T00:00:00Z', dateFinished: '2026-03-04'},
      {bookId: 2, readStatus: ReadStatus.READ, readStatusModifiedTime: '2026-03-05T00:00:00Z', dateFinished: '2026-03-05'},
    ]);

    expect(cachedBook(1)).toMatchObject({readStatus: ReadStatus.READ, dateFinished: '2026-03-04'});
    expect(cachedBook(2)).toMatchObject({readStatus: ReadStatus.READ, dateFinished: '2026-03-05'});
  });

  it('updates the cached last read timestamp without calling the backend', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-03-26T12:00:00Z'));
    queryClient.setQueryData<Book[]>(BOOKS_QUERY_KEY, [buildBook(3)]);

    service.updateLastReadTime(3);

    expect(cachedBook(3).lastReadTime).toBe('2026-03-26T12:00:00.000Z');
    httpTestingController.expectNone(req => req.url.includes('/api/v1/books'));

    vi.useRealTimers();
  });
});
