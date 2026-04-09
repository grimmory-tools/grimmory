import {beforeEach, describe, expect, it, vi} from 'vitest';
import {QueryClient} from '@tanstack/angular-query-experimental';

import {Book, BookMetadata} from '../model/book.model';
import {
  addBookToCache,
  invalidateBookDetailQueries,
  invalidateBookQueries,
  invalidateBooksQuery,
  patchBookFieldsInCache,
  patchBookInCacheWith,
  patchBookMetadataInCache,
  patchBooksInCache,
  removeBookQueries,
  removeBooksFromCache
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
    const invalidateQueriesSpy = vi.spyOn(queryClient, 'invalidateQueries');

    queryClient.setQueryData<Book[]>(BOOKS_QUERY_KEY, [firstBook]);

    addBookToCache(queryClient, secondBook);
    expect(queryClient.getQueryData<Book[]>(BOOKS_QUERY_KEY)).toEqual([firstBook, secondBook]);

    addBookToCache(queryClient, updatedSecondBook);
    expect(queryClient.getQueryData<Book[]>(BOOKS_QUERY_KEY)).toEqual([firstBook, updatedSecondBook]);

    expect(invalidateQueriesSpy).toHaveBeenCalledWith({queryKey: ['app-books']});
    expect(invalidateQueriesSpy).toHaveBeenCalledWith({queryKey: ['app-filter-options']});
  });

  it('invalidates the full books query and book detail queries', () => {
    const invalidateQueriesSpy = vi.spyOn(queryClient, 'invalidateQueries');

    invalidateBooksQuery(queryClient);
    invalidateBookDetailQueries(queryClient, [1, 1, 2]);
    invalidateBookQueries(queryClient, [3, 3]);

    expect(invalidateQueriesSpy).toHaveBeenCalledWith({queryKey: BOOKS_QUERY_KEY, exact: true});
    expect(invalidateQueriesSpy).toHaveBeenCalledWith({queryKey: bookDetailQueryPrefix(1)});
    expect(invalidateQueriesSpy).toHaveBeenCalledWith({queryKey: bookDetailQueryPrefix(2)});
    expect(invalidateQueriesSpy).toHaveBeenCalledWith({queryKey: bookDetailQueryPrefix(3)});
    expect(invalidateQueriesSpy).toHaveBeenCalledWith({queryKey: ['app-books']});
    expect(invalidateQueriesSpy).toHaveBeenCalledWith({queryKey: ['app-filter-options']});
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
    expect(invalidateQueriesSpy).toHaveBeenCalledWith({queryKey: ['app-books']});
    expect(invalidateQueriesSpy).toHaveBeenCalledWith({queryKey: ['app-filter-options']});
  });

  it('patches metadata, selected fields, and updater callbacks in the list cache', () => {
    const firstBook = makeBook(1, {
      metadata: {
        bookId: 1,
        title: 'Original Title',
        authors: ['Old Author']
      }
    });
    const secondBook = makeBook(2, {
      metadata: {
        bookId: 2,
        title: 'Second'
      },
      libraryName: 'Library A'
    });
    const thirdBook = makeBook(3, {
      metadata: {
        bookId: 3,
        title: 'Third'
      }
    });
    const invalidateQueriesSpy = vi.spyOn(queryClient, 'invalidateQueries');

    queryClient.setQueryData<Book[]>(BOOKS_QUERY_KEY, [firstBook, secondBook, thirdBook]);

    const updatedMetadata: BookMetadata = {
      bookId: 1,
      title: 'Updated Title'
    };

    patchBookMetadataInCache(queryClient, 1, updatedMetadata);
    patchBookFieldsInCache(queryClient, [
      {bookId: 2, fields: {libraryName: 'Updated Library'}},
      {bookId: 3, fields: {personalRating: 4}}
    ]);
    patchBookInCacheWith(queryClient, 1, book => ({
      ...book,
      metadata: {
        ...(book.metadata ?? {bookId: book.id}),
        authors: ['New Author']
      }
    }));

    expect(queryClient.getQueryData<Book[]>(BOOKS_QUERY_KEY)).toEqual([
      {
        ...firstBook,
        metadata: {
          ...firstBook.metadata,
          title: 'Updated Title',
          authors: ['New Author']
        }
      },
      {
        ...secondBook,
        libraryName: 'Updated Library'
      },
      {
        ...thirdBook,
        personalRating: 4
      }
    ]);
    expect(invalidateQueriesSpy).toHaveBeenCalledWith({queryKey: bookDetailQueryPrefix(1)});
    expect(invalidateQueriesSpy).toHaveBeenCalledWith({queryKey: bookDetailQueryPrefix(2)});
    expect(invalidateQueriesSpy).toHaveBeenCalledWith({queryKey: bookDetailQueryPrefix(3)});
    expect(invalidateQueriesSpy).toHaveBeenCalledWith({queryKey: ['app-books']});
    expect(invalidateQueriesSpy).toHaveBeenCalledWith({queryKey: ['app-filter-options']});
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

  it('removes deleted books from the list cache and associated queries', () => {
    const firstBook = makeBook(1);
    const secondBook = makeBook(2);
    const invalidateQueriesSpy = vi.spyOn(queryClient, 'invalidateQueries');

    queryClient.setQueryData<Book[]>(BOOKS_QUERY_KEY, [firstBook, secondBook]);
    queryClient.setQueryData(bookDetailQueryKey(1, false), firstBook);
    queryClient.setQueryData(bookRecommendationsQueryKey(1, 20), [secondBook]);

    removeBooksFromCache(queryClient, [1]);

    expect(queryClient.getQueryData<Book[]>(BOOKS_QUERY_KEY)).toEqual([secondBook]);
    expect(queryClient.getQueryData(bookDetailQueryKey(1, false))).toBeUndefined();
    expect(queryClient.getQueryData(bookRecommendationsQueryKey(1, 20))).toBeUndefined();
    expect(invalidateQueriesSpy).toHaveBeenCalledWith({queryKey: ['app-books']});
    expect(invalidateQueriesSpy).toHaveBeenCalledWith({queryKey: ['app-filter-options']});
  });

  it('ignores an empty remove request', () => {
    const setQueryDataSpy = vi.spyOn(queryClient, 'setQueryData');
    const removeQueriesSpy = vi.spyOn(queryClient, 'removeQueries');

    removeBooksFromCache(queryClient, []);

    expect(setQueryDataSpy).not.toHaveBeenCalled();
    expect(removeQueriesSpy).not.toHaveBeenCalled();
  });
});
