import {HttpParams} from '@angular/common/http';
import {inject, Service} from '@angular/core';
import {experimental_streamedQuery, queryOptions} from '@tanstack/angular-query-experimental';

import {BrowseQueryService} from '../../../core/data/browse-query.service';
import {getSseJson, httpGet, QUERY_DEFAULTS, SEARCH_DEFAULTS} from '../../../core/data/query-transport';
import {AuthService} from '../../../shared/service/auth.service';
import {authorQueryKeys} from './author-query-keys';
import {AuthorMetadataSearchParams, AuthorQueryFacetKey, AuthorQuerySortKey, isAuthorQueryFacetKey} from './author-query-params';
import {AuthorDetail, AuthorPhotoResult, AuthorSearchResult, AuthorSummary} from './author-query.models';

function normalizeMetadataSearchParams(params: AuthorMetadataSearchParams): AuthorMetadataSearchParams {
  return 'asin' in params
    ? {region: params.region, asin: params.asin.trim()}
    : {region: params.region, query: params.query.trim()};
}

@Service()
export class AuthorQueryService extends BrowseQueryService<AuthorSummary, AuthorQueryFacetKey, AuthorQuerySortKey> {
  private readonly auth = inject(AuthService);

  constructor() {
    super({path: '/api/v1/authors', keys: authorQueryKeys, isFacetKey: isAuthorQueryFacetKey});
  }

  detail(authorId: number) {
    return queryOptions({
      queryKey: authorQueryKeys.detail(authorId),
      queryFn: ({signal}): Promise<AuthorDetail> => httpGet<AuthorDetail>(this.http, `${this.baseUrl}/${authorId}`, signal),
      ...QUERY_DEFAULTS,
    });
  }

  byName(name: string) {
    return queryOptions({
      queryKey: authorQueryKeys.byName(name),
      queryFn: ({signal}): Promise<AuthorDetail> =>
        httpGet<AuthorDetail>(this.http, `${this.baseUrl}/by-name`, signal, new HttpParams().set('name', name)),
      ...QUERY_DEFAULTS,
    });
  }

  metadataSearch(authorId: number, params: AuthorMetadataSearchParams) {
    const normalized = normalizeMetadataSearchParams(params);
    const httpParams = 'asin' in normalized
      ? new HttpParams().set('region', normalized.region).set('asin', normalized.asin)
      : new HttpParams().set('region', normalized.region).set('q', normalized.query);
    return queryOptions({
      queryKey: authorQueryKeys.metadataSearch(authorId, normalized),
      queryFn: ({signal}): Promise<AuthorSearchResult[]> =>
        httpGet<AuthorSearchResult[]>(this.http, `${this.baseUrl}/${authorId}/search-metadata`, signal, httpParams),
      ...SEARCH_DEFAULTS,
    });
  }

  photoSearch(authorId: number, query: string) {
    const trimmed = query.trim();
    const url = `${this.baseUrl}/${authorId}/search-photos?${new HttpParams().set('q', trimmed).toString()}`;
    return queryOptions({
      queryKey: authorQueryKeys.photoSearch(authorId, trimmed),
      queryFn: experimental_streamedQuery({
        streamFn: ({signal}) => getSseJson<AuthorPhotoResult>(this.auth, url, signal),
        refetchMode: 'reset',
      }),
      ...SEARCH_DEFAULTS,
    });
  }
}
