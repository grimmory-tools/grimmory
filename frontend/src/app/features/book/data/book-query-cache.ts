import {QueryClient} from '@tanstack/angular-query-experimental';

import {readingQueryKeys} from '../../reading/data/reading-query-keys';
import {bookQueryKeys} from './book-query-keys';

function uniqueBookIds(bookIds: Iterable<number>): Set<number> {
  return new Set(bookIds);
}

export interface BookQueryChangeSet {
  readonly changedBookIds?: Iterable<number>;
  readonly deletedBookIds?: Iterable<number>;
}

export async function invalidateAllBookQueries(queryClient: QueryClient): Promise<void> {
  await Promise.all([
    queryClient.invalidateQueries({queryKey: bookQueryKeys.all()}),
    queryClient.invalidateQueries({queryKey: readingQueryKeys.all()}),
  ]);
}

export function invalidateBookCollections(queryClient: QueryClient): Promise<void> {
  return queryClient.invalidateQueries({queryKey: bookQueryKeys.collections()});
}

export function invalidateBookRecommendations(queryClient: QueryClient): Promise<void> {
  return queryClient.invalidateQueries({queryKey: bookQueryKeys.recommendations()});
}

export async function applyBookQueryChangeSet(
  queryClient: QueryClient,
  update: BookQueryChangeSet,
): Promise<void> {
  const deletedBookIds = uniqueBookIds(update.deletedBookIds ?? []);
  const changedBookIds = uniqueBookIds(update.changedBookIds ?? []);
  for (const bookId of deletedBookIds) {
    changedBookIds.delete(bookId);
  }

  if (changedBookIds.size === 0 && deletedBookIds.size === 0) {
    return;
  }

  if (deletedBookIds.size > 0) {
    queryClient.removeQueries({
      predicate: ({queryKey}) =>
        isBookLeafKey(queryKey, bookQueryKeys.details(), deletedBookIds)
        || isBookLeafKey(queryKey, bookQueryKeys.recommendations(), deletedBookIds)
        || isBookLeafKey(queryKey, readingQueryKeys.books(), deletedBookIds)
        || isBookLeafKey(queryKey, readingQueryKeys.files(), deletedBookIds),
    });
  }

  await Promise.all([
    invalidateBookCollections(queryClient),
    ...(changedBookIds.size > 0 ? [queryClient.invalidateQueries({
      predicate: ({queryKey}) =>
        isBookLeafKey(queryKey, bookQueryKeys.details(), changedBookIds)
        || isBookLeafKey(queryKey, readingQueryKeys.books(), changedBookIds)
        || isBookLeafKey(queryKey, readingQueryKeys.files(), changedBookIds),
    })] : []),
    invalidateBookRecommendations(queryClient),
    queryClient.invalidateQueries({queryKey: readingQueryKeys.notebook()}),
  ]);
}

function isBookLeafKey(
  queryKey: readonly unknown[],
  prefix: readonly string[],
  bookIds: ReadonlySet<number>,
): boolean {
  return prefix.every((part, index) => queryKey[index] === part)
    && bookIds.has(queryKey[prefix.length] as number);
}
