import {QueryClient} from '@tanstack/angular-query-experimental';

import {authorQueryKeys} from './author-query-keys';

export function invalidateAuthorCollections(queryClient: QueryClient): Promise<void> {
  return queryClient.invalidateQueries({queryKey: authorQueryKeys.collections()});
}
