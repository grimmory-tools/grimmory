import {browseQueryKeys} from '../../../core/data/browse-query-keys';
import {AuthorQueryFacetKey, AuthorQuerySortKey} from './author-query-params';

export const authorQueryKeys = browseQueryKeys<AuthorQueryFacetKey, AuthorQuerySortKey>('authors');
