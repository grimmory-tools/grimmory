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
  recommendations: () => [...browseKeys.all(), 'recommendation'] as const,
  recommendationQueries: (bookId: number) =>
    [...bookQueryKeys.recommendations(), bookId] as const,
  recommendation: (bookId: number, limit: number) =>
    [...bookQueryKeys.recommendationQueries(bookId), {limit}] as const,
};
