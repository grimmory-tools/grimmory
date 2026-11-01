import {Service} from '@angular/core';
import {queryOptions} from '@tanstack/angular-query-experimental';

import {API_CONFIG} from '../../../core/config/api-config';
import {BrowseQueryService} from '../../../core/data/browse-query.service';
import {httpGet, QUERY_DEFAULTS, toAbortablePromise} from '../../../core/data/query-transport';
import {bookQueryKeys} from './book-query-keys';
import {BookDescriptionOptions, BookQueryFacetKey, BookQuerySortKey, isBookQueryFacetKey} from './book-query-params';
import {BookDuplicateGroup, BookDuplicateRequest} from './book-query.models';
import {BookDetail, BookRecommendation, BookReviewResponse, BookSummary} from './book-response.models';

const BOOK_BATCH_CHUNK_SIZE = 500;

@Service()
export class BookQueryService extends BrowseQueryService<BookSummary, BookQueryFacetKey, BookQuerySortKey> {
  private readonly reviewsUrl = `${API_CONFIG.BASE_URL}/api/v1/reviews`;

  constructor() {
    super({path: '/api/v1/books', keys: bookQueryKeys, isFacetKey: isBookQueryFacetKey});
  }

  detail(bookId: number, {withDescription}: BookDescriptionOptions) {
    return queryOptions({
      queryKey: bookQueryKeys.detail(bookId, withDescription),
      queryFn: ({signal}) => httpGet<BookDetail>(
        this.http,
        `${this.baseUrl}/${bookId}`,
        signal,
        {withDescription},
      ),
      ...QUERY_DEFAULTS,
    });
  }

  reviews(bookId: number) {
    return queryOptions({
      queryKey: bookQueryKeys.reviews(bookId),
      queryFn: async ({signal}): Promise<readonly BookReviewResponse[]> =>
        (await httpGet<readonly BookReviewResponse[] | null>(this.http, `${this.reviewsUrl}/book/${bookId}`, signal)) ?? [],
      ...QUERY_DEFAULTS,
    });
  }

  batch(ids: readonly number[]) {
    const distinctIds = [...new Set(ids)];
    return queryOptions({
      queryKey: bookQueryKeys.batch(distinctIds),
      queryFn: ({signal}) => this.fetchBatch(distinctIds, signal),
      ...QUERY_DEFAULTS,
    });
  }

  recommendations(bookId: number, limit: number) {
    return queryOptions({
      queryKey: bookQueryKeys.recommendation(bookId, limit),
      queryFn: ({signal}) => httpGet<BookRecommendation[]>(
        this.http,
        `${this.baseUrl}/${bookId}/recommendations`,
        signal,
        {limit},
      ),
      ...QUERY_DEFAULTS,
    });
  }

  duplicates(request: BookDuplicateRequest) {
    return queryOptions({
      queryKey: bookQueryKeys.duplicates(request),
      queryFn: ({signal}) => toAbortablePromise(
        this.http.post<BookDuplicateGroup[]>(`${this.baseUrl}/duplicates`, request), signal),
      ...QUERY_DEFAULTS,
      staleTime: 0,
    });
  }

  private async fetchBatch(ids: readonly number[], signal: AbortSignal): Promise<BookSummary[]> {
    const books: BookSummary[] = [];
    for (let offset = 0; offset < ids.length; offset += BOOK_BATCH_CHUNK_SIZE) {
      const chunk = ids.slice(offset, offset + BOOK_BATCH_CHUNK_SIZE);
      books.push(...await httpGet<BookSummary[]>(
        this.http,
        `${this.baseUrl}/batch`,
        signal,
        {ids: chunk.join(',')},
      ));
    }
    return books;
  }
}
