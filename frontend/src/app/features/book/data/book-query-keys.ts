import {createBrowseQueryKeys} from '../../../core/data/browse-query-keys';
import {
  BookCollectionFilterParams,
  BookPageParams,
  BookQueryParams,
} from './book-query-params';

const browseBookQueryKeys = createBrowseQueryKeys<
  BookCollectionFilterParams,
  BookQueryParams,
  BookPageParams
>('books');

export const bookQueryKeys = {
  ...browseBookQueryKeys,
  details: () => [...bookQueryKeys.all(), 'detail'] as const,
  detailQueries: (bookId: number) =>
    [...bookQueryKeys.details(), bookId] as const,
  detail: (bookId: number, withDescription: boolean) =>
    [...bookQueryKeys.detailQueries(bookId), {withDescription}] as const,
  recommendations: () => [...bookQueryKeys.all(), 'recommendation'] as const,
  recommendationQueries: (bookId: number) =>
    [...bookQueryKeys.recommendations(), bookId] as const,
  recommendation: (bookId: number, limit: number) =>
    [...bookQueryKeys.recommendationQueries(bookId), {limit}] as const,
};
