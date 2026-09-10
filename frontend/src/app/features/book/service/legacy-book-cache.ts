import {QueryClient} from '@tanstack/angular-query-experimental';

import {
  invalidateAllBookQueries,
  applyBookQueryChangeSet,
} from '../data/book-query-cache';
import {Book, BookMetadata} from '../model/book.model';
import {
  BOOK_DETAIL_QUERY_PREFIX,
  BOOK_RECOMMENDATIONS_QUERY_PREFIX,
  BOOKS_QUERY_KEY,
  bookDetailQueryPrefix,
  bookRecommendationsQueryPrefix,
} from './book-query-keys';

export interface BookCoverPatch {
  readonly id: number;
  readonly coverUpdatedOn: string | null;
}

interface BookCacheChangeSet {
  readonly changedBookIds?: Iterable<number>;
  readonly deletedBookIds?: Iterable<number>;
}

interface NormalizedBookCacheChangeSet {
  readonly changedBookIds: ReadonlySet<number>;
  readonly deletedBookIds: ReadonlySet<number>;
}

interface BookCacheReconciliationOptions {
  readonly legacyList: 'already-updated' | 'needs-refetch';
}

function removeLegacyBookQueries(queryClient: QueryClient, bookIds: Iterable<number>): void {
  for (const bookId of bookIds) {
    queryClient.removeQueries({queryKey: bookDetailQueryPrefix(bookId)});
    queryClient.removeQueries({queryKey: bookRecommendationsQueryPrefix(bookId)});
  }
}

async function reconcileLegacyBookChangeSet(
  queryClient: QueryClient,
  changeSet: NormalizedBookCacheChangeSet,
): Promise<void> {
  removeLegacyBookQueries(queryClient, changeSet.deletedBookIds);

  await Promise.all([
    queryClient.invalidateQueries({queryKey: BOOKS_QUERY_KEY, exact: true}),
    ...[...changeSet.changedBookIds].map(bookId => queryClient.invalidateQueries({
      queryKey: bookDetailQueryPrefix(bookId),
    })),
    invalidateLegacyBookRecommendations(queryClient),
  ]);
}

export async function invalidateAllLegacyBooks(queryClient: QueryClient): Promise<void> {
  await Promise.all([
    queryClient.invalidateQueries({queryKey: BOOKS_QUERY_KEY, exact: true}),
    queryClient.invalidateQueries({queryKey: BOOK_DETAIL_QUERY_PREFIX}),
    queryClient.invalidateQueries({queryKey: BOOK_RECOMMENDATIONS_QUERY_PREFIX}),
  ]);
}

export function invalidateLegacyBookRecommendations(queryClient: QueryClient): Promise<void> {
  return queryClient.invalidateQueries({queryKey: BOOK_RECOMMENDATIONS_QUERY_PREFIX});
}

export async function reconcileBookCacheChangeSet(
  queryClient: QueryClient,
  changeSet: BookCacheChangeSet,
  options: BookCacheReconciliationOptions,
): Promise<void> {
  const deletedBookIds = new Set(changeSet.deletedBookIds ?? []);
  const changedBookIds = new Set(changeSet.changedBookIds ?? []);
  if (changedBookIds.size === 0 && deletedBookIds.size === 0) {
    return;
  }

  await Promise.all([
    options.legacyList === 'needs-refetch'
      ? reconcileLegacyBookChangeSet(queryClient, {changedBookIds, deletedBookIds})
      : reconcilePatchedLegacyBookChangeSet(queryClient, {changedBookIds, deletedBookIds}),
    applyBookQueryChangeSet(queryClient, {changedBookIds, deletedBookIds}),
  ]);
}

async function reconcilePatchedLegacyBookChangeSet(
  queryClient: QueryClient,
  changeSet: NormalizedBookCacheChangeSet,
): Promise<void> {
  if (changeSet.changedBookIds.size === 0 && changeSet.deletedBookIds.size === 0) {
    return;
  }
  removeLegacyBookQueries(queryClient, changeSet.deletedBookIds);
  await Promise.all([
    ...[...changeSet.changedBookIds].map(bookId => queryClient.invalidateQueries({
      queryKey: bookDetailQueryPrefix(bookId),
    })),
    invalidateLegacyBookRecommendations(queryClient),
  ]);
}

export function patchListOnlyBookFields(
  queryClient: QueryClient,
  updates: readonly {readonly bookId: number; readonly fields: Partial<Book>}[],
): Promise<void> {
  return patchListOnlyBooksWith(queryClient, updates.map(update => ({
    bookId: update.bookId,
    updater: book => ({...book, ...update.fields}),
  })));
}

export function patchListOnlyBooksWith(
  queryClient: QueryClient,
  updates: readonly {readonly bookId: number; readonly updater: (book: Book) => Book}[],
): Promise<void> {
  const updaterMap = new Map(updates.map(update => [update.bookId, update.updater]));
  queryClient.setQueryData<Book[]>(BOOKS_QUERY_KEY, current =>
    current?.map(book => updaterMap.get(book.id)?.(book) ?? book)
  );
  return reconcilePatchedLegacyBookChangeSet(queryClient, {
    changedBookIds: new Set(updaterMap.keys()),
    deletedBookIds: new Set(),
  });
}

export function removeListOnlyBooks(
  queryClient: QueryClient,
  bookIds: Iterable<number>,
): Promise<void> {
  const deletedBookIds = new Set(bookIds);
  queryClient.setQueryData<Book[]>(BOOKS_QUERY_KEY, current =>
    current?.filter(book => !deletedBookIds.has(book.id))
  );
  return reconcilePatchedLegacyBookChangeSet(queryClient, {
    changedBookIds: new Set(),
    deletedBookIds,
  });
}

export function patchBookCoversInCache(
  queryClient: QueryClient,
  patches: readonly BookCoverPatch[],
): void {
  const patchMap = new Map(patches.map(patch => [patch.id, patch.coverUpdatedOn]));
  queryClient.setQueryData<Book[]>(BOOKS_QUERY_KEY, current =>
    current?.map(book => {
      const coverUpdatedOn = patchMap.get(book.id);
      return coverUpdatedOn && book.metadata
        ? {...book, metadata: {...book.metadata, coverUpdatedOn}}
        : book;
    })
  );
  void reconcileBookCacheChangeSet(
    queryClient,
    {changedBookIds: patchMap.keys()},
    {legacyList: 'already-updated'},
  );
}

export function invalidateAllBookCaches(queryClient: QueryClient): void {
  void Promise.all([
    invalidateAllLegacyBooks(queryClient),
    invalidateAllBookQueries(queryClient),
  ]);
}

export function invalidateBooksById(queryClient: QueryClient, bookIds: Iterable<number>): void {
  void reconcileBookCacheChangeSet(
    queryClient,
    {changedBookIds: bookIds},
    {legacyList: 'needs-refetch'},
  );
}

export function invalidateDeletedBookQueries(queryClient: QueryClient, bookIds: Iterable<number>): void {
  void reconcileBookCacheChangeSet(
    queryClient,
    {deletedBookIds: bookIds},
    {legacyList: 'needs-refetch'},
  );
}

export function patchBooksInCache(queryClient: QueryClient, updatedBooks: readonly Book[]): void {
  patchBooksInCacheWith(queryClient, updatedBooks.map(book => ({
    bookId: book.id,
    updater: () => book,
  })));
}

export function upsertBooksInCache(queryClient: QueryClient, updatedBooks: readonly Book[]): void {
  const updatedMap = new Map(updatedBooks.map(book => [book.id, book]));
  queryClient.setQueryData<Book[]>(BOOKS_QUERY_KEY, current => {
    if (!current) {
      return current;
    }
    const currentBookIds = new Set(current.map(book => book.id));
    return [
      ...current.map(book => updatedMap.get(book.id) ?? book),
      ...updatedBooks.filter(book => !currentBookIds.has(book.id)),
    ];
  });
  void reconcileBookCacheChangeSet(
    queryClient,
    {changedBookIds: updatedMap.keys()},
    {legacyList: 'already-updated'},
  );
}

export function patchBookMetadataInCache(queryClient: QueryClient, bookId: number, metadata: BookMetadata): void {
  patchBooksInCacheWith(queryClient, [{
    bookId,
    updater: book => ({...book, metadata}),
  }]);
}

export function patchBookInCacheWith(queryClient: QueryClient, bookId: number, updater: (book: Book) => Book): void {
  patchBooksInCacheWith(queryClient, [{bookId, updater}]);
}

export function patchBooksInCacheWith(
  queryClient: QueryClient,
  updates: readonly {readonly bookId: number; readonly updater: (book: Book) => Book}[],
): void {
  const updatedBookIds = updates.map(update => update.bookId);
  void Promise.all([
    patchListOnlyBooksWith(queryClient, updates),
    applyBookQueryChangeSet(queryClient, {changedBookIds: updatedBookIds}),
  ]);
}

export function patchBookFieldsInCache(queryClient: QueryClient, updates: {bookId: number; fields: Partial<Book>}[]): void {
  patchBooksInCacheWith(queryClient, updates.map(update => ({
    bookId: update.bookId,
    updater: book => ({...book, ...update.fields}),
  })));
}

export function patchAttachedBookFilesInCache(
  queryClient: QueryClient,
  updatedBook: Book,
  deletedSourceBookIds: Iterable<number>,
): void {
  const deletedBookIds = new Set(deletedSourceBookIds);
  queryClient.setQueryData<Book[]>(BOOKS_QUERY_KEY, current =>
    current
      ?.filter(book => !deletedBookIds.has(book.id))
      .map(book => book.id === updatedBook.id ? updatedBook : book)
  );
  void reconcileBookCacheChangeSet(
    queryClient,
    {changedBookIds: [updatedBook.id], deletedBookIds},
    {legacyList: 'already-updated'},
  );
}
