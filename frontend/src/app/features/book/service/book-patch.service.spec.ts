import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {QueryClient} from '@tanstack/angular-query-experimental';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {ResetProgressTypes} from '../../../shared/constants/reset-progress-type';
import {ReadStatus} from '../model/book.model';
import {BOOKS_QUERY_KEY} from './book-query-keys';
import {BookPatchService} from './book-patch.service';

describe('BookPatchService', () => {
  let service: BookPatchService;
  let httpTestingController: HttpTestingController;
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient();
    vi.spyOn(queryClient, 'setQueryData');
    vi.spyOn(queryClient, 'invalidateQueries').mockResolvedValue();

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

  it('posts PDF progress with file progress when a book file id is provided', () => {
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

    expect(queryClient.setQueryData).toHaveBeenCalledWith(BOOKS_QUERY_KEY, expect.any(Function));
    expect(queryClient.invalidateQueries).toHaveBeenCalledWith({queryKey: ['books', 'detail', 11]});
  });

  it('deduplicates identical EPUB progress updates before posting', () => {
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
    expect(queryClient.setQueryData).toHaveBeenCalledWith(BOOKS_QUERY_KEY, expect.any(Function));
    expect(queryClient.invalidateQueries).toHaveBeenCalledWith({queryKey: ['books', 'detail', 7]});
  });

  it('patches cached progress fields when resetting kobo progress', () => {
    service.resetProgress([1, 2], ResetProgressTypes.KOBO).subscribe();

    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/books/reset-progress'));
    expect(request.request.method).toBe('POST');
    expect(request.request.params.get('type')).toBe(ResetProgressTypes.KOBO);
    expect(request.request.body).toEqual([1, 2]);
    request.flush([
      {bookId: 1, readStatus: ReadStatus.UNREAD, readStatusModifiedTime: '2026-03-01T00:00:00Z', dateFinished: null},
      {bookId: 2, readStatus: ReadStatus.READING, readStatusModifiedTime: '2026-03-02T00:00:00Z', dateFinished: '2026-03-03'},
    ]);

    expect(queryClient.setQueryData).toHaveBeenCalledWith(BOOKS_QUERY_KEY, expect.any(Function));
    expect(queryClient.invalidateQueries).toHaveBeenCalledWith({queryKey: ['app-books']});
    expect(queryClient.invalidateQueries).toHaveBeenCalledWith({queryKey: ['app-filter-options']});
  });

  it('updates cached date finished after the backend accepts the change', () => {
    service.updateDateFinished(5, '2026-03-10').subscribe();

    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/books/progress'));
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      bookId: 5,
      dateFinished: '2026-03-10',
    });
    request.flush(null);

    expect(queryClient.setQueryData).toHaveBeenCalledWith(BOOKS_QUERY_KEY, expect.any(Function));
    expect(queryClient.invalidateQueries).toHaveBeenCalledWith({queryKey: ['books', 'detail', 5]});
  });

  it('updates the read status for multiple books and patches cached fields', () => {
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

    expect(queryClient.setQueryData).toHaveBeenCalledWith(BOOKS_QUERY_KEY, expect.any(Function));
    expect(queryClient.invalidateQueries).toHaveBeenCalledWith({queryKey: ['app-books']});
    expect(queryClient.invalidateQueries).toHaveBeenCalledWith({queryKey: ['app-filter-options']});
  });

  it('updates the cached last read timestamp without calling the backend', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-03-26T12:00:00Z'));

    service.updateLastReadTime(3);

    expect(queryClient.setQueryData).toHaveBeenCalledWith(BOOKS_QUERY_KEY, expect.any(Function));
    httpTestingController.expectNone(req => req.url.includes('/api/v1/books'));

    vi.useRealTimers();
  });
});
