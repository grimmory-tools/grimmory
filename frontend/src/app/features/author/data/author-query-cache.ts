import {QueryClient} from '@tanstack/angular-query-experimental';

import {authorQueryKeys} from './author-query-keys';

export function invalidateAuthorCollections(queryClient: QueryClient): Promise<void> {
  return queryClient.invalidateQueries({queryKey: authorQueryKeys.collections()});
}

export function invalidateAuthorDetails(queryClient: QueryClient): Promise<void> {
  return queryClient.invalidateQueries({queryKey: authorQueryKeys.details()});
}

export async function invalidateAuthors(queryClient: QueryClient, authorIds: Iterable<number>): Promise<void> {
  await Promise.all([
    invalidateAuthorCollections(queryClient),
    ...[...authorIds].map(authorId => queryClient.invalidateQueries({queryKey: authorQueryKeys.detail(authorId)})),
  ]);
}
