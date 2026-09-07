import {HttpClient, HttpParams} from '@angular/common/http';
import {inject, Injectable} from '@angular/core';
import {queryOptions, QueryClient} from '@tanstack/angular-query-experimental';

import {API_CONFIG} from '../../../core/config/api-config';
import {BrowseQueryService} from '../../../core/data/browse-query.service';
import {QUERY_DEFAULTS} from '../../../core/data/query-transport';
import {AuthService} from '../../../shared/service/auth.service';
import {bookQueryKeys} from './book-query-keys';
import {
  BookDescriptionOptions,
  BookQueryFacetKey,
  BookQuerySortKey,
} from './book-query-params';
import {BookDetail, BookRecommendation, BookSummary} from './book-response.models';

@Injectable({providedIn: 'root'})
export class BookQueryService extends BrowseQueryService<
  BookSummary,
  BookQueryFacetKey,
  BookQuerySortKey
> {
  private static readonly BASE_URL = `${API_CONFIG.BASE_URL}/api/v1/books`;

  constructor() {
    super(
      inject(HttpClient),
      inject(AuthService),
      inject(QueryClient),
      BookQueryService.BASE_URL,
      bookQueryKeys,
    );
  }

  detail(bookId: number, {withDescription}: BookDescriptionOptions) {
    return queryOptions({
      queryKey: bookQueryKeys.detail(bookId, withDescription),
      queryFn: ({signal}): Promise<BookDetail> => this.get<BookDetail>(
        `${BookQueryService.BASE_URL}/${bookId}`,
        signal,
        new HttpParams().set('withDescription', withDescription.toString()),
      ),
      ...QUERY_DEFAULTS,
    });
  }

  recommendations(bookId: number, limit: number) {
    return queryOptions({
      queryKey: bookQueryKeys.recommendation(bookId, limit),
      queryFn: ({signal}): Promise<BookRecommendation[]> => this.get<BookRecommendation[]>(
        `${BookQueryService.BASE_URL}/${bookId}/recommendations`,
        signal,
        new HttpParams().set('limit', limit.toString()),
      ),
      ...QUERY_DEFAULTS,
    });
  }
}
