import {HttpParams} from '@angular/common/http';
import {Service} from '@angular/core';
import {queryOptions} from '@tanstack/angular-query-experimental';

import {BrowseQueryService} from '../../../core/data/browse-query.service';
import {QUERY_DEFAULTS, toAbortablePromise} from '../../../core/data/query-transport';
import {bookQueryKeys} from './book-query-keys';
import {BookDescriptionOptions, BookQueryFacetKey, BookQuerySortKey, isBookQueryFacetKey} from './book-query-params';
import {BookDuplicateGroup, BookDuplicateRequest} from './book-query.models';
import {BookDetail, BookRecommendation, BookSummary} from './book-response.models';

const BOOK_BATCH_CHUNK_SIZE = 500;

@Service()
export class BookQueryService extends BrowseQueryService<BookSummary, BookQueryFacetKey, BookQuerySortKey> {
  constructor() {
    super({path: '/api/v1/books', keys: bookQueryKeys, isFacetKey: isBookQueryFacetKey});
  }

  detail(bookId: number, {withDescription}: BookDescriptionOptions) {
    return queryOptions({
      queryKey: bookQueryKeys.detail(bookId, withDescription),
      queryFn: ({signal}): Promise<BookDetail> => this.get<BookDetail>(
        `${this.baseUrl}/${bookId}`,
        signal,
        new HttpParams().set('withDescription', withDescription.toString()),
      ),
      ...QUERY_DEFAULTS,
    });
  }

  batch(ids: readonly number[]) {
    const distinctIds = [...new Set(ids)];
    return queryOptions({
      queryKey: bookQueryKeys.batch(distinctIds),
      queryFn: ({signal}): Promise<BookSummary[]> => this.fetchBatch(distinctIds, signal),
      ...QUERY_DEFAULTS,
    });
  }

  recommendations(bookId: number, limit: number) {
    return queryOptions({
      queryKey: bookQueryKeys.recommendation(bookId, limit),
      queryFn: ({signal}): Promise<BookRecommendation[]> => this.get<BookRecommendation[]>(
        `${this.baseUrl}/${bookId}/recommendations`,
        signal,
        new HttpParams().set('limit', limit.toString()),
      ),
      ...QUERY_DEFAULTS,
    });
  }

  duplicates(request: BookDuplicateRequest) {
    const criteria = {...request};
    return queryOptions({
      ...QUERY_DEFAULTS,
      queryKey: bookQueryKeys.duplicates(criteria),
      queryFn: ({signal}) => toAbortablePromise(
        this.http.post<BookDuplicateGroup[]>(`${this.baseUrl}/duplicates`, criteria), signal),
      staleTime: 0,
    });
  }

  private async fetchBatch(ids: readonly number[], signal: AbortSignal): Promise<BookSummary[]> {
    const books: BookSummary[] = [];
    for (let offset = 0; offset < ids.length; offset += BOOK_BATCH_CHUNK_SIZE) {
      const chunk = ids.slice(offset, offset + BOOK_BATCH_CHUNK_SIZE);
      books.push(...await this.get<BookSummary[]>(
        `${this.baseUrl}/batch`,
        signal,
        new HttpParams().set('ids', chunk.join(',')),
      ));
    }
    return books;
  }
}
