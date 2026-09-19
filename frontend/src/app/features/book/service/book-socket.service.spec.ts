import {TestBed} from '@angular/core/testing';
import {QueryClient} from '@tanstack/angular-query-experimental';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {observeActiveQuery} from '../../../core/testing/query-testing';
import {Book} from '../model/book.model';
import {TaskStatus} from '../../settings/task-management/task.service';
import {bookQueryKeys} from '../data/book-query-keys';
import {BookDetail} from '../data/book-response.models';
import {normalizeBookPageParams} from '../data/book-query-params';
import {BOOKS_QUERY_KEY, bookDetailQueryKey, bookRecommendationsQueryKey} from './book-query-keys';
import {BookSocketService} from './book-socket.service';

const PAGE_QUERY_KEY = bookQueryKeys.boundedPage(normalizeBookPageParams({
  size: 20,
  facets: {},
  facetLogic: 'or',
  sort: [],
}));

function makeBook(id: number, overrides: Partial<Book> = {}): Book {
  return {
    id,
    libraryId: 1,
    libraryName: 'Library',
    metadata: {
      bookId: id,
      title: `Book ${id}`,
      coverUpdatedOn: '2026-03-01T00:00:00Z',
    },
    ...overrides,
  };
}

describe('BookSocketService', () => {
  let service: BookSocketService;
  let queryClient: QueryClient;

  beforeEach(() => {
    vi.useFakeTimers();
    queryClient = new QueryClient();

    TestBed.configureTestingModule({
      providers: [
        BookSocketService,
        {provide: QueryClient, useValue: queryClient},
      ],
    });

    service = TestBed.inject(BookSocketService);
  });

  afterEach(() => {
    queryClient.clear();
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  function flushSocketChanges(): void {
    vi.advanceTimersToNextTimer();
  }

  function isInvalidated(queryKey: readonly unknown[]): boolean | undefined {
    return queryClient.getQueryState(queryKey)?.isInvalidated;
  }

  it('upserts a newly created book without refetching the legacy list', () => {
    const existing = makeBook(1);
    const created = makeBook(2);
    queryClient.setQueryData<Book[]>(BOOKS_QUERY_KEY, [existing]);
    queryClient.setQueryData(PAGE_QUERY_KEY, {content: [existing]});
    queryClient.setQueryData(bookQueryKeys.detail(2, false), {id: 2});

    service.handleNewlyCreatedBook(created);

    expect(queryClient.getQueryData<Book[]>(BOOKS_QUERY_KEY)).toEqual([existing, created]);
    expect(isInvalidated(BOOKS_QUERY_KEY)).toBe(false);
    expect(isInvalidated(PAGE_QUERY_KEY)).toBe(true);
    expect(isInvalidated(bookQueryKeys.detail(2, false))).toBe(true);
  });

  it('does not manufacture a legacy list when none is cached', () => {
    service.handleNewlyCreatedBook(makeBook(2));

    expect(queryClient.getQueryData<Book[]>(BOOKS_QUERY_KEY)).toBeUndefined();
  });

  it('patches an updated book into the legacy list without touching clean query data', () => {
    const original = makeBook(7, {libraryName: 'Original'});
    const updated = makeBook(7, {libraryName: 'Updated'});
    const cleanDetail: BookDetail = {
      id: 7,
      libraryId: 1,
      libraryName: 'Clean library',
      metadata: {bookId: 7, title: 'Clean detail'},
    };
    queryClient.setQueryData<Book[]>(BOOKS_QUERY_KEY, [original]);
    queryClient.setQueryData(bookQueryKeys.detail(7, false), cleanDetail);

    service.handleBookUpdate(updated);

    expect(queryClient.getQueryData<Book[]>(BOOKS_QUERY_KEY)).toEqual([updated]);
    expect(isInvalidated(BOOKS_QUERY_KEY)).toBe(false);
    expect(queryClient.getQueryData(bookQueryKeys.detail(7, false))).toBe(cleanDetail);
    expect(isInvalidated(bookQueryKeys.detail(7, false))).toBe(true);
  });

  it('accepts the watcher ID-array update shape and invalidates instead of caching it as a book', () => {
    const original = makeBook(8);
    queryClient.setQueryData<Book[]>(BOOKS_QUERY_KEY, [original]);
    queryClient.setQueryData(bookDetailQueryKey(8, false), original);

    service.handleBookUpdate([8]);
    flushSocketChanges();

    expect(queryClient.getQueryData<Book[]>(BOOKS_QUERY_KEY)).toEqual([original]);
    expect(isInvalidated(BOOKS_QUERY_KEY)).toBe(true);
    expect(isInvalidated(bookDetailQueryKey(8, false))).toBe(true);
  });

  it('patches cover timestamps into the legacy list and invalidates the affected detail', () => {
    const first = makeBook(3);
    const second = makeBook(4);
    queryClient.setQueryData<Book[]>(BOOKS_QUERY_KEY, [first, second]);
    queryClient.setQueryData(bookDetailQueryKey(3, false), first);

    service.handleMultipleBookCoverPatches([{id: 3, coverUpdatedOn: '2026-03-26T12:34:00Z'}]);

    expect(queryClient.getQueryData<Book[]>(BOOKS_QUERY_KEY)).toEqual([
      {...first, metadata: {...first.metadata, coverUpdatedOn: '2026-03-26T12:34:00Z'}},
      second,
    ]);
    expect(isInvalidated(BOOKS_QUERY_KEY)).toBe(false);
    expect(isInvalidated(bookDetailQueryKey(3, false))).toBe(true);
  });

  it('coalesces socket bursts and lets deletion win over changes for the same book', async () => {
    const legacyBooks = observeActiveQuery(queryClient, BOOKS_QUERY_KEY, [makeBook(7), makeBook(8)]);
    const modernPage = observeActiveQuery(
      queryClient,
      PAGE_QUERY_KEY,
      {content: [makeBook(7), makeBook(8)]},
    );
    const changedDetail = observeActiveQuery(
      queryClient,
      bookQueryKeys.detail(8, false),
      {id: 8},
    );
    const deletedDetailKey = bookQueryKeys.detail(7, false);
    queryClient.setQueryData(deletedDetailKey, {id: 7});

    for (let index = 0; index < 100; index += 1) {
      service.handleBookUpdate([7]);
    }
    service.handleRemovedBookIds([7]);
    service.handleBookUpdate([7]);
    service.handleBookUpdate([8]);

    expect(legacyBooks.fetchCount()).toBe(0);
    expect(modernPage.fetchCount()).toBe(0);
    flushSocketChanges();

    await vi.waitFor(() => {
      expect(legacyBooks.fetchCount()).toBe(1);
      expect(modernPage.fetchCount()).toBe(1);
      expect(changedDetail.fetchCount()).toBe(1);
    });
    expect(legacyBooks.abortCount()).toBe(0);
    expect(modernPage.abortCount()).toBe(0);
    expect(changedDetail.abortCount()).toBe(0);
    expect(queryClient.getQueryData(deletedDetailKey)).toBeUndefined();

    legacyBooks.finish();
    modernPage.finish();
    changedDetail.finish();
  });

  it('invalidates recommendations when recommendation refresh completes', () => {
    queryClient.setQueryData(bookQueryKeys.recommendation(1, 20), [makeBook(2)]);
    queryClient.setQueryData(bookRecommendationsQueryKey(1, 20), [makeBook(2)]);

    service.handleTaskProgress({
      taskId: 'task-1',
      taskType: 'UPDATE_BOOK_RECOMMENDATIONS',
      taskStatus: TaskStatus.COMPLETED,
      progress: 100,
      message: 'Done',
    });

    expect(isInvalidated(bookQueryKeys.recommendation(1, 20))).toBe(true);
    expect(isInvalidated(bookRecommendationsQueryKey(1, 20))).toBe(true);
  });

  it.each([
    {taskId: 'task-1', taskType: 'UPDATE_BOOK_RECOMMENDATIONS', taskStatus: TaskStatus.IN_PROGRESS, progress: 50, message: 'Working'},
    {taskId: 'task-1', taskType: 'REFRESH_LIBRARY_METADATA', taskStatus: TaskStatus.COMPLETED, progress: 100, message: 'Done'},
  ])('does not reconcile books for an irrelevant task event', payload => {
    queryClient.setQueryData(bookRecommendationsQueryKey(1, 20), [makeBook(2)]);

    service.handleTaskProgress(payload);

    expect(isInvalidated(bookRecommendationsQueryKey(1, 20))).toBe(false);
  });

  it('broadly invalidates clean and legacy book caches after reconnect', () => {
    const book = makeBook(1);
    queryClient.setQueryData<Book[]>(BOOKS_QUERY_KEY, [book]);
    queryClient.setQueryData(bookDetailQueryKey(1, false), book);
    queryClient.setQueryData(bookRecommendationsQueryKey(1, 20), [book]);
    queryClient.setQueryData(bookQueryKeys.detail(1, false), book);

    service.handleReconnect();

    expect(isInvalidated(BOOKS_QUERY_KEY)).toBe(true);
    expect(isInvalidated(bookDetailQueryKey(1, false))).toBe(true);
    expect(isInvalidated(bookRecommendationsQueryKey(1, 20))).toBe(true);
    expect(isInvalidated(bookQueryKeys.detail(1, false))).toBe(true);
  });
});
