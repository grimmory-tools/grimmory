import {HttpTestingController} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {InfiniteData, QueryClient} from '@tanstack/angular-query-experimental';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';

import {createAuthServiceStub, createQueryClientHarness, flushSignalAndQueryEffects} from '../../../core/testing/query-testing';
import {AuthService} from '../../../shared/service/auth.service';
import {AppBookSummary, AppPageResponse} from '../model/app-book.model';
import {AppBooksApiService, summaryToBook} from './app-books-api.service';

function summary(id: number): AppBookSummary {
  return {
    id,
    title: `Book ${id}`,
    authors: [],
    thumbnailUrl: null,
    readStatus: null,
    personalRating: null,
    seriesName: null,
    seriesNumber: null,
    libraryId: 1,
    addedOn: null,
    lastReadTime: null,
    readProgress: null,
    primaryFileId: null,
    primaryFileType: null,
    primaryFileName: null,
    coverUpdatedOn: null,
    audiobookCoverUpdatedOn: null,
    isPhysical: false,
    publisher: null,
    categories: null,
    tags: null,
    moods: null,
    language: null,
    narrator: null,
    isbn13: null,
    isbn10: null,
    publishedDate: null,
    pageCount: null,
    ageRating: null,
    contentRating: null,
    metadataMatchScore: null,
    fileSizeKb: null,
    amazonRating: null,
    amazonReviewCount: null,
    goodreadsRating: null,
    goodreadsReviewCount: null,
    hardcoverRating: null,
    hardcoverReviewCount: null,
    ranobedbRating: null,
    lubimyczytacRating: null,
    audibleRating: null,
    audibleReviewCount: null,
    allMetadataLocked: null,
  };
}

describe('AppBooksApiService', () => {
  let service: AppBooksApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    const queryHarness = createQueryClientHarness();
    TestBed.configureTestingModule({
      providers: [
        ...queryHarness.providers,
        AppBooksApiService,
        {provide: AuthService, useValue: createAuthServiceStub()},
      ],
    });
    service = TestBed.inject(AppBooksApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    TestBed.resetTestingModule();
  });

  it('does not report loading while the paginated query is disabled', () => {
    expect(service.isLoading()).toBe(false);
  });

  it('does not refetch the heavy book list when the window regains focus', () => {
    const queryClient = TestBed.inject(QueryClient);
    service.setBooksEnabled(true);
    flushSignalAndQueryEffects();
    const query = queryClient.getQueryCache().findAll({queryKey: ['app-books']})[0];
    const options = query?.options as {refetchOnWindowFocus?: unknown} | undefined;

    expect(options?.refetchOnWindowFocus).toBe(false);

    const request = http.expectOne(req => req.url.endsWith('/api/v1/app/books'));
    request.flush({
      content: [],
      page: 0,
      size: 50,
      totalElements: 0,
      totalPages: 0,
      hasNext: false,
      hasPrevious: false,
    });
  });

  it('loads summaries in bounded sequential batches and preserves requested order', () => {
    const ids = Array.from({length: 501}, (_, index) => index + 1);
    let result: number[] = [];

    service.getBooksByIds(ids).subscribe(books => {
      result = books.map(book => book.id);
    });

    const first = http.expectOne(request => request.url.endsWith('/api/v1/app/books/summaries'));
    expect(first.request.body).toEqual(ids.slice(0, 500));
    first.flush(ids.slice(0, 500).reverse().map(summary));

    const second = http.expectOne(request => request.url.endsWith('/api/v1/app/books/summaries'));
    expect(second.request.body).toEqual([501]);
    second.flush([summary(501)]);

    expect(result).toEqual(ids);
  });

  it('uses the bounded quick-search endpoint without requesting a page count', () => {
    let titles: (string | null)[] = [];

    service.quickSearchBooks('  Dune  ', 80).subscribe(results => {
      titles = results.map(result => result.title);
    });

    const request = http.expectOne(req => req.url.endsWith('/api/v1/app/books/quick-search'));
    expect(request.request.params.get('q')).toBe('Dune');
    expect(request.request.params.get('limit')).toBe('50');
    request.flush([{
      id: 1,
      title: 'Dune',
      authors: ['Frank Herbert'],
      seriesName: null,
      seriesNumber: null,
      publishedDate: null,
      primaryFileType: 'EPUB',
      primaryFileName: 'Dune.epub',
      coverUpdatedOn: null,
      audiobookCoverUpdatedOn: null,
    }]);

    expect(titles).toEqual(['Dune']);
  });

  it('loads all series books with one count-free request', () => {
    let result: number[] = [];

    service.getSeriesBooks('A/B Series').subscribe(books => {
      result = books.map(book => book.id);
    });

    const request = http.expectOne(req => req.url.endsWith('/api/v1/app/series/books/all'));
    expect(request.request.params.get('name')).toBe('A/B Series');
    request.flush([summary(1), summary(2)]);

    expect(result).toEqual([1, 2]);
  });

  it('preserves the document parse status when adapting paginated summaries', () => {
    const unreadable = summary(7);
    unreadable.primaryFileId = 70;
    unreadable.primaryFileType = 'DOC';
    unreadable.primaryFileName = 'report.docx';
    unreadable.primaryFileDocumentParseStatus = 'UNREADABLE';

    expect(summaryToBook(unreadable).primaryFile).toMatchObject({
      id: 70,
      bookType: 'DOC',
      documentParseStatus: 'UNREADABLE',
    });
  });

  it('windows the loaded range from the first retained page so callers can offset virtual indices', () => {
    // With maxPages, earlier pages are evicted as the user scrolls down, so the accumulated
    // books() array is a sliding window rather than a prefix from index 0. Simulate a window
    // whose first retained page is page 3 (books 150..).
    const queryClient = TestBed.inject(QueryClient);
    const page: AppPageResponse<AppBookSummary> = {
      content: [summary(151), summary(152)],
      page: 3,
      size: 50,
      totalElements: 2000,
      totalPages: 40,
      hasNext: true,
      hasPrevious: true,
    };
    const data: InfiniteData<AppPageResponse<AppBookSummary>> = {pages: [page], pageParams: [3]};

    // Key mirrors booksQuery's default filters/sort/search so the observer adopts this data.
    queryClient.setQueryData(['app-books', {}, {field: 'addedOn', dir: 'desc'}, ''], data);
    flushSignalAndQueryEffects();

    expect(service.firstLoadedIndex()).toBe(150);
    expect(service.books().map(book => book.id)).toEqual([151, 152]);
  });

  it('preserves book object identity when query data is refreshed with unchanged summaries', () => {
    const queryClient = TestBed.inject(QueryClient);
    const firstPage: AppPageResponse<AppBookSummary> = {
      content: [summary(1), summary(2)],
      page: 0,
      size: 50,
      totalElements: 2,
      totalPages: 1,
      hasNext: false,
      hasPrevious: false,
    };
    const firstData: InfiniteData<AppPageResponse<AppBookSummary>> = {pages: [firstPage], pageParams: [0]};
    const queryKey = ['app-books', {}, {field: 'addedOn', dir: 'desc'}, ''];

    queryClient.setQueryData(queryKey, firstData);
    flushSignalAndQueryEffects();
    const firstBooks = service.books();

    const refreshedPage: AppPageResponse<AppBookSummary> = {
      ...firstPage,
      content: firstPage.content.map(item => ({...item})),
    };
    queryClient.setQueryData(queryKey, {pages: [refreshedPage], pageParams: [0]});
    flushSignalAndQueryEffects();

    expect(service.books()[0]).toBe(firstBooks[0]);
    expect(service.books()[1]).toBe(firstBooks[1]);
  });
});
