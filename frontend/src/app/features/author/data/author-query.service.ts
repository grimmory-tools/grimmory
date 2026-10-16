import {HttpParams} from '@angular/common/http';
import {Service} from '@angular/core';
import {experimental_streamedQuery, queryOptions} from '@tanstack/angular-query-experimental';

import {BrowseQueryService} from '../../../core/data/browse-query.service';
import {getSseJson, QUERY_DEFAULTS} from '../../../core/data/query-transport';
import {authorQueryKeys} from './author-query-keys';
import {AuthorMetadataSearchParams, AuthorQueryFacetKey, AuthorQuerySortKey} from './author-query-params';
import {AuthorDetail, AuthorPhotoResult, AuthorSearchResult, AuthorSummary} from './author-response.models';

const SEARCH_DEFAULTS = {
  refetchOnWindowFocus: false,
  retry: false,
} as const;

@Service()
export class AuthorQueryService extends BrowseQueryService<AuthorSummary, AuthorQueryFacetKey, AuthorQuerySortKey> {
  constructor() {
    super({path: '/api/v1/authors', keys: authorQueryKeys});
  }

  detail(authorId: number) {
    return queryOptions({
      queryKey: authorQueryKeys.detail(authorId),
      queryFn: ({signal}): Promise<AuthorDetail> => this.get<AuthorDetail>(`${this.baseUrl}/${authorId}`, signal),
      ...QUERY_DEFAULTS,
    });
  }

  byName(name: string) {
    return queryOptions({
      queryKey: authorQueryKeys.byName(name),
      queryFn: ({signal}): Promise<AuthorDetail> =>
        this.get<AuthorDetail>(`${this.baseUrl}/by-name`, signal, new HttpParams().set('name', name)),
      ...QUERY_DEFAULTS,
      staleTime: 0,
    });
  }

  metadataSearch(authorId: number, params: AuthorMetadataSearchParams) {
    const httpParams = params.asin
      ? new HttpParams().set('region', params.region).set('asin', params.asin)
      : new HttpParams().set('region', params.region).set('q', params.query);
    return queryOptions({
      queryKey: authorQueryKeys.metadataSearch(authorId, params),
      queryFn: ({signal}): Promise<AuthorSearchResult[]> =>
        this.get<AuthorSearchResult[]>(`${this.baseUrl}/${authorId}/search-metadata`, signal, httpParams),
      ...SEARCH_DEFAULTS,
    });
  }

  photoSearch(authorId: number, query: string) {
    const url = `${this.baseUrl}/${authorId}/search-photos?${new HttpParams().set('q', query).toString()}`;
    return queryOptions({
      queryKey: authorQueryKeys.photoSearch(authorId, query),
      queryFn: experimental_streamedQuery({
        streamFn: ({signal}) => getSseJson<AuthorPhotoResult>(this.authService, url, signal),
        refetchMode: 'reset',
      }),
      ...SEARCH_DEFAULTS,
    });
  }
}
