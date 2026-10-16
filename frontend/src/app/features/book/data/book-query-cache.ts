import {QueryClient} from '@tanstack/angular-query-experimental';

import {invalidateAuthorCollections, invalidateAuthorDetails} from '../../author/data/author-query-cache';
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
    invalidateAuthorCollections(queryClient),
    invalidateAuthorDetails(queryClient),
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
        || isBookLeafKey(queryKey, bookQueryKeys.recommendations(), deletedBookIds),
    });
  }

  await Promise.all([
    invalidateBookCollections(queryClient),
    invalidateAuthorCollections(queryClient),
    ...(changedBookIds.size > 0 ? [queryClient.invalidateQueries({
      predicate: ({queryKey}) => isBookLeafKey(queryKey, bookQueryKeys.details(), changedBookIds),
    })] : []),
    invalidateBookRecommendations(queryClient),
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
