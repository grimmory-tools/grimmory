import {QueryClient} from '@tanstack/angular-query-experimental';
import {beforeEach, describe, expect, it} from 'vitest';

import {Book} from '../model/book.model';
import {bookQueryKeys} from '../data/book-query-keys';
import {
  invalidateDeletedBookQueries,
  patchBooksInCacheWith,
  reconcileBookCacheChangeSet,
  removeListOnlyBooks,
} from './legacy-book-cache';
import {
  BOOKS_QUERY_KEY,
  bookDetailQueryKey,
  bookRecommendationsQueryKey
} from './book-query-keys';

function makeBook(id: number, overrides: Partial<Book> = {}): Book {
  return {
    id,
    libraryId: 1,
    libraryName: 'Test Library',
    metadata: {
      bookId: id,
      title: `Book ${id}`
    },
    ...overrides
  };
}

describe('legacy book cache adapter', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient();
  });

  function isInvalidated(queryKey: readonly unknown[]): boolean | undefined {
    return queryClient.getQueryState(queryKey)?.isInvalidated;
  }

  it('fans a changeset out to both worlds and removes deleted legacy leaves', async () => {
    queryClient.setQueryData<Book[]>(BOOKS_QUERY_KEY, [makeBook(1), makeBook(201), makeBook(401)]);
    queryClient.setQueryData(bookDetailQueryKey(201, false), makeBook(201));
    queryClient.setQueryData(bookDetailQueryKey(401, false), makeBook(401));
    queryClient.setQueryData(bookRecommendationsQueryKey(1, 20), []);
    queryClient.setQueryData(bookQueryKeys.detail(201, false), makeBook(201));

    await reconcileBookCacheChangeSet(
      queryClient,
      {deletedBookIds: [1], changedBookIds: [201]},
      {legacyList: 'needs-refetch'},
    );

    expect(isInvalidated(BOOKS_QUERY_KEY)).toBe(true);
    expect(isInvalidated(bookDetailQueryKey(201, false))).toBe(true);
    expect(isInvalidated(bookDetailQueryKey(401, false))).toBe(false);
    expect(queryClient.getQueryData(bookRecommendationsQueryKey(1, 20))).toBeUndefined();
    expect(isInvalidated(bookQueryKeys.detail(201, false))).toBe(true);
  });

  it('leaves the surgically patched legacy list fresh while invalidating dependents', () => {
    const original = makeBook(1);
    queryClient.setQueryData<Book[]>(BOOKS_QUERY_KEY, [original]);
    queryClient.setQueryData(bookDetailQueryKey(1, false), original);
    queryClient.setQueryData(bookQueryKeys.detail(1, false), original);

    patchBooksInCacheWith(queryClient, [
      {bookId: 1, updater: book => ({...book, metadata: {...book.metadata!, titleLocked: true}})},
    ]);

    expect(queryClient.getQueryData<Book[]>(BOOKS_QUERY_KEY)![0].metadata!.titleLocked).toBe(true);
    expect(isInvalidated(BOOKS_QUERY_KEY)).toBe(false);
    expect(isInvalidated(bookDetailQueryKey(1, false))).toBe(true);
    expect(isInvalidated(bookQueryKeys.detail(1, false))).toBe(true);
  });

  it('does nothing for an empty changeset', async () => {
    const book = makeBook(1);
    queryClient.setQueryData<Book[]>(BOOKS_QUERY_KEY, [book]);
    queryClient.setQueryData(bookQueryKeys.detail(1, false), book);

    patchBooksInCacheWith(queryClient, []);
    invalidateDeletedBookQueries(queryClient, []);
    await removeListOnlyBooks(queryClient, []);

    expect(isInvalidated(BOOKS_QUERY_KEY)).toBe(false);
    expect(isInvalidated(bookQueryKeys.detail(1, false))).toBe(false);
    expect(queryClient.getQueryData<Book[]>(BOOKS_QUERY_KEY)).toEqual([book]);
  });

});
