import {QueryClient} from '@tanstack/angular-query-experimental';

import {metadataSourceQueryKeys} from '../../metadata/sources/metadata-source-query-keys';
import {bookQueryKeys} from './book-query-keys';

export interface BookQueryChangeSet {
  readonly changedBookIds?: Iterable<number>;
  readonly deletedBookIds?: Iterable<number>;
}

export async function invalidateAllBookQueries(queryClient: QueryClient): Promise<void> {
  await Promise.all([
    queryClient.invalidateQueries({queryKey: bookQueryKeys.all()}),
    queryClient.invalidateQueries({queryKey: metadataSourceQueryKeys.sidecars()}),
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
  const deletedBookIds = new Set(update.deletedBookIds);
  const changedBookIds = new Set(update.changedBookIds);
  for (const bookId of deletedBookIds) {
    changedBookIds.delete(bookId);
  }

  if (changedBookIds.size === 0 && deletedBookIds.size === 0) {
    return;
  }

  if (deletedBookIds.size > 0) {
    queryClient.removeQueries({
      predicate: ({queryKey}) =>
        matchesBookId(queryKey, bookQueryKeys.details(), deletedBookIds)
        || matchesBookId(queryKey, bookQueryKeys.recommendations(), deletedBookIds)
        || matchesBookId(queryKey, metadataSourceQueryKeys.sidecars(), deletedBookIds),
    });
  }

  await Promise.all([
    invalidateBookCollections(queryClient),
    ...(changedBookIds.size > 0 ? [queryClient.invalidateQueries({
      predicate: ({queryKey}) =>
        matchesBookId(queryKey, bookQueryKeys.details(), changedBookIds)
        || matchesBookId(queryKey, metadataSourceQueryKeys.sidecars(), changedBookIds),
    })] : []),
    invalidateBookRecommendations(queryClient),
  ]);
}

function matchesBookId(
  queryKey: readonly unknown[],
  prefix: readonly string[],
  bookIds: ReadonlySet<number>,
): boolean {
  const bookId = queryKey[prefix.length];
  return prefix.every((part, index) => queryKey[index] === part)
    && typeof bookId === 'number'
    && bookIds.has(bookId);
}
