import {QueryClient} from '@tanstack/angular-query-experimental';

import {Book, BookMetadata} from '../model/book.model';
import {BOOKS_QUERY_KEY, bookDetailQueryPrefix, bookRecommendationsQueryPrefix} from './book-query-keys';

const APP_BOOKS_QUERY_PREFIX = ['app-books'] as const;
const APP_FILTER_OPTIONS_QUERY_PREFIX = ['app-filter-options'] as const;

export function invalidateAppBooksQueries(queryClient: QueryClient): void {
  void queryClient.invalidateQueries({queryKey: APP_BOOKS_QUERY_PREFIX});
  void queryClient.invalidateQueries({queryKey: APP_FILTER_OPTIONS_QUERY_PREFIX});
}

// --- Full invalidation (refetches from server) ---

export function invalidateBooksQuery(queryClient: QueryClient): void {
  void queryClient.invalidateQueries({queryKey: BOOKS_QUERY_KEY, exact: true});
  invalidateAppBooksQueries(queryClient);
}

export function invalidateBookQueries(queryClient: QueryClient, bookIds: Iterable<number>): void {
  invalidateBooksQuery(queryClient);
  invalidateBookDetailQueries(queryClient, bookIds);
}

export function invalidateBookDetailQueries(queryClient: QueryClient, bookIds: Iterable<number>): void {
  for (const bookId of new Set(bookIds)) {
    void queryClient.invalidateQueries({queryKey: bookDetailQueryPrefix(bookId)});
  }
}

export function removeBookQueries(queryClient: QueryClient, bookIds: Iterable<number>): void {
  for (const bookId of new Set(bookIds)) {
    queryClient.removeQueries({queryKey: bookDetailQueryPrefix(bookId)});
    queryClient.removeQueries({queryKey: bookRecommendationsQueryPrefix(bookId)});
  }
}

export function removeBooksFromCache(queryClient: QueryClient, bookIds: Iterable<number>): void {
  const removedIds = new Set(bookIds);
  if (removedIds.size === 0) {
    return;
  }

  queryClient.setQueryData<Book[]>(BOOKS_QUERY_KEY, current =>
    (current ?? []).filter(book => !removedIds.has(book.id))
  );
  removeBookQueries(queryClient, removedIds);
  invalidateAppBooksQueries(queryClient);
}

// --- Surgical patches (updates cache directly, no list refetch) ---

export function addBookToCache(queryClient: QueryClient, book: Book): void {
  queryClient.setQueryData<Book[]>(BOOKS_QUERY_KEY, current => {
    const books = current ?? [];
    const exists = books.some(b => b.id === book.id);
    return exists ? books.map(b => b.id === book.id ? book : b) : [...books, book];
  });
  invalidateAppBooksQueries(queryClient);
}

export function patchBooksInCache(queryClient: QueryClient, updatedBooks: Book[]): void {
  const updatedMap = new Map(updatedBooks.map(book => [book.id, book]));
  queryClient.setQueryData<Book[]>(BOOKS_QUERY_KEY, current =>
    (current ?? []).map(book => updatedMap.get(book.id) ?? book)
  );
  invalidateBookDetailQueries(queryClient, updatedBooks.map(b => b.id));
  invalidateAppBooksQueries(queryClient);
}

export function patchBookMetadataInCache(queryClient: QueryClient, bookId: number, metadata: BookMetadata): void {
  queryClient.setQueryData<Book[]>(BOOKS_QUERY_KEY, current =>
    (current ?? []).map(book =>
      book.id === bookId ? {...book, metadata} : book
    )
  );
  invalidateBookDetailQueries(queryClient, [bookId]);
  invalidateAppBooksQueries(queryClient);
}

export function patchBookInCacheWith(queryClient: QueryClient, bookId: number, updater: (book: Book) => Book): void {
  queryClient.setQueryData<Book[]>(BOOKS_QUERY_KEY, current =>
    (current ?? []).map(book => book.id === bookId ? updater(book) : book)
  );
  invalidateBookDetailQueries(queryClient, [bookId]);
  invalidateAppBooksQueries(queryClient);
}

export function patchBookFieldsInCache(queryClient: QueryClient, updates: {bookId: number; fields: Partial<Book>}[]): void {
  const updateMap = new Map(updates.map(u => [u.bookId, u.fields]));
  queryClient.setQueryData<Book[]>(BOOKS_QUERY_KEY, current =>
    (current ?? []).map(book => {
      const fields = updateMap.get(book.id);
      return fields ? {...book, ...fields} : book;
    })
  );
  invalidateBookDetailQueries(queryClient, updates.map(u => u.bookId));
  invalidateAppBooksQueries(queryClient);
}
