import {createBrowseQueryKeys} from '../../../core/data/browse-query-keys';
import {
  AuthorCollectionFilterParams,
  AuthorPageParams,
  AuthorQueryParams,
} from './author-query-params';

const browseAuthorQueryKeys = createBrowseQueryKeys<
  AuthorCollectionFilterParams,
  AuthorQueryParams,
  AuthorPageParams
>('authors');

export const authorQueryKeys = {
  ...browseAuthorQueryKeys,
  details: () => [...authorQueryKeys.all(), 'detail'] as const,
  detail: (authorId: number) => [...authorQueryKeys.details(), authorId] as const,
};
