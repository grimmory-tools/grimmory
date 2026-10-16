import {HttpParams} from '@angular/common/http';
import {Service} from '@angular/core';
import {queryOptions} from '@tanstack/angular-query-experimental';

import {BrowseQueryService} from '../../../core/data/browse-query.service';
import {QUERY_DEFAULTS} from '../../../core/data/query-transport';
import {bookQueryKeys} from './book-query-keys';
import {BookDescriptionOptions, BookQueryFacetKey, BookQuerySortKey} from './book-query-params';
import {BookDetail, BookSummary} from './book-response.models';

@Service()
export class BookQueryService extends BrowseQueryService<BookSummary, BookQueryFacetKey, BookQuerySortKey> {
  constructor() {
    super({path: '/api/v1/books', keys: bookQueryKeys});
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
}
