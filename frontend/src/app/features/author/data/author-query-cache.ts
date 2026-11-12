import {QueryClient} from '@tanstack/angular-query-experimental';

import {authorQueryKeys} from './author-query-keys';

export async function invalidateAuthors(queryClient: QueryClient): Promise<void> {
  await Promise.all([
    queryClient.invalidateQueries({queryKey: authorQueryKeys.collections()}),
    queryClient.invalidateQueries({queryKey: authorQueryKeys.details()}),
  ]);
}
