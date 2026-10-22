import {browseQueryKeys} from '../../../core/data/browse-query-keys';
import {AuthorMetadataSearchParams, AuthorQueryFacetKey, AuthorQuerySortKey} from './author-query-params';

const browseKeys = browseQueryKeys<AuthorQueryFacetKey, AuthorQuerySortKey>('authors');

export const authorQueryKeys = {
  ...browseKeys,
  details: () => [...browseKeys.all(), 'detail'] as const,
  detail: (authorId: number) => [...authorQueryKeys.details(), authorId] as const,
  metadataSearch: (authorId: number, params: AuthorMetadataSearchParams) =>
    [...browseKeys.all(), 'metadata-search', authorId, params] as const,
  photoSearch: (authorId: number, query: string) => [...browseKeys.all(), 'photo-search', authorId, query] as const,
  byName: (name: string) => [...browseKeys.all(), 'by-name', name] as const,
};
