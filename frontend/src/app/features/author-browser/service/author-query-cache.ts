import {QueryClient} from '@tanstack/angular-query-experimental';

import {AuthorSummary} from '../model/author.model';
import {AUTHORS_QUERY_KEY} from './author-query-keys';

export function patchAuthorInCache(queryClient: QueryClient, authorId: number, fields: Partial<AuthorSummary>): void {
  queryClient.setQueryData<AuthorSummary[]>(AUTHORS_QUERY_KEY, current =>
    (current ?? []).map(author => author.id === authorId ? {...author, ...fields} : author)
  );
}
