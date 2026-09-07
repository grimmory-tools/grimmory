import {HttpClient} from '@angular/common/http';
import {inject, Injectable} from '@angular/core';
import {queryOptions, QueryClient} from '@tanstack/angular-query-experimental';

import {API_CONFIG} from '../../../core/config/api-config';
import {BrowseQueryService} from '../../../core/data/browse-query.service';
import {QUERY_DEFAULTS} from '../../../core/data/query-transport';
import {AuthService} from '../../../shared/service/auth.service';
import {authorQueryKeys} from './author-query-keys';
import {
  AuthorQueryFacetKey,
  AuthorQuerySortKey,
} from './author-query-params';
import {AuthorDetail, AuthorSummary} from './author-response.models';

@Injectable({providedIn: 'root'})
export class AuthorQueryService extends BrowseQueryService<
  AuthorSummary,
  AuthorQueryFacetKey,
  AuthorQuerySortKey
> {
  private static readonly BASE_URL = `${API_CONFIG.BASE_URL}/api/v1/authors`;

  constructor() {
    super(
      inject(HttpClient),
      inject(AuthService),
      inject(QueryClient),
      AuthorQueryService.BASE_URL,
      authorQueryKeys,
    );
  }

  detail(authorId: number) {
    return queryOptions({
      queryKey: authorQueryKeys.detail(authorId),
      queryFn: ({signal}): Promise<AuthorDetail> => this.get<AuthorDetail>(
        `${AuthorQueryService.BASE_URL}/${authorId}`,
        signal,
      ),
      ...QUERY_DEFAULTS,
    });
  }
}
