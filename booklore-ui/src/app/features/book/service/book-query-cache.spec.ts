import {beforeEach, describe, expect, it, vi} from 'vitest';
import {QueryClient} from '@tanstack/angular-query-experimental';

import {Book} from '../model/book.model';
import {
  addBookToCache,
  patchBooksInCache,
  removeBookQueries
} from './book-query-cache';
import {
  BOOKS_QUERY_KEY,
  bookDetailQueryKey,
  bookDetailQueryPrefix,
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

describe('book-query-cache', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient();
  });

  it('adds new books and replaces existing entries by id', () => {
    const firstBook = makeBook(1);
    const secondBook = makeBook(2);
    const updatedSecondBook = makeBook(2, {
      libraryName: 'Updated Library',
      metadata: {
        bookId: 2,
        title: 'Updated Book 2'
      }
    });

    queryClient.setQueryData<Book[]>(BOOKS_QUERY_KEY, [firstBook]);

    addBookToCache(queryClient, secondBook);
    expect(queryClient.getQueryData<Book[]>(BOOKS_QUERY_KEY)).toEqual([firstBook, secondBook]);

    addBookToCache(queryClient, updatedSecondBook);
    expect(queryClient.getQueryData<Book[]>(BOOKS_QUERY_KEY)).toEqual([firstBook, updatedSecondBook]);
  });

  it('patches list entries and invalidates matching detail queries', () => {
    const firstBook = makeBook(1);
    const secondBook = makeBook(2);
    const updatedSecondBook = makeBook(2, {
      libraryName: 'Updated Library'
    });
    const invalidateQueriesSpy = vi.spyOn(queryClient, 'invalidateQueries');

    queryClient.setQueryData<Book[]>(BOOKS_QUERY_KEY, [firstBook, secondBook]);

    patchBooksInCache(queryClient, [updatedSecondBook]);

    expect(queryClient.getQueryData<Book[]>(BOOKS_QUERY_KEY)).toEqual([firstBook, updatedSecondBook]);
    expect(invalidateQueriesSpy).toHaveBeenCalledWith({queryKey: bookDetailQueryPrefix(2)});
    expect(invalidateQueriesSpy).toHaveBeenCalledTimes(1);
  });

  it('removes detail and recommendation queries for deleted books', () => {
    const firstBook = makeBook(1);
    const secondBook = makeBook(2);

    queryClient.setQueryData(bookDetailQueryKey(1, false), firstBook);
    queryClient.setQueryData(bookDetailQueryKey(1, true), firstBook);
    queryClient.setQueryData(bookRecommendationsQueryKey(1, 20), [secondBook]);
    queryClient.setQueryData(bookDetailQueryKey(2, false), secondBook);

    removeBookQueries(queryClient, [1]);

    expect(queryClient.getQueryData(bookDetailQueryKey(1, false))).toBeUndefined();
    expect(queryClient.getQueryData(bookDetailQueryKey(1, true))).toBeUndefined();
    expect(queryClient.getQueryData(bookRecommendationsQueryKey(1, 20))).toBeUndefined();
    expect(queryClient.getQueryData(bookDetailQueryKey(2, false))).toEqual(secondBook);
  });
});
