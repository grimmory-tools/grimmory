import {browseQueryKeys} from '../../../core/data/browse-query-keys';
import {BookQueryFacetKey, BookQuerySortKey} from './book-query-params';

const browseKeys = browseQueryKeys<BookQueryFacetKey, BookQuerySortKey>('books');

export const bookQueryKeys = {
  ...browseKeys,
  details: () => [...browseKeys.all(), 'detail'] as const,
  detailQueries: (bookId: number) =>
    [...bookQueryKeys.details(), bookId] as const,
  detail: (bookId: number, withDescription: boolean) =>
    [...bookQueryKeys.detailQueries(bookId), {withDescription}] as const,
};
