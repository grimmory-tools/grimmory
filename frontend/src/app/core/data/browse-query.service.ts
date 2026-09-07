import {HttpClient, HttpParams} from '@angular/common/http';
import {effect} from '@angular/core';
import {
  infiniteQueryOptions,
  queryOptions,
  QueryClient,
} from '@tanstack/angular-query-experimental';
import {lastValueFrom, takeUntil} from 'rxjs';

import {API_CONFIG} from '../config/api-config';
import {AuthService} from '../../shared/service/auth.service';
import {
  BrowseCollectionFilterParams,
  BrowsePageParams,
  BrowseQueryParams,
  normalizeBrowseCollectionFilterParams,
  normalizeBrowsePageParams,
  normalizeBrowseQueryParams,
  toBrowseCollectionHttpParams,
  toBrowseIdsHttpParams,
  toBrowsePageHttpParams,
} from './browse-query-params';
import {BrowsePage, findBrowsePageLink} from './browse.models';
import {mapBrowseFacetGroups, mapBrowsePage} from './browse-response';
import {abortSignal, QUERY_DEFAULTS} from './query-transport';

interface BrowseQueryKeys<CollectionParams, QueryParams, PageParams> {
  all: () => readonly unknown[];
  boundedPage: (params: PageParams) => readonly unknown[];
  infinitePage: (params: PageParams) => readonly unknown[];
  facets: (params: CollectionParams) => readonly unknown[];
  ids: (params: QueryParams) => readonly unknown[];
}

export abstract class BrowseQueryService<
  Summary extends {id: string | number},
  FacetKey extends string,
  SortKey extends string,
> {
  protected constructor(
    protected readonly http: HttpClient,
    authService: AuthService,
    queryClient: QueryClient,
    private readonly baseUrl: string,
    private readonly keys: BrowseQueryKeys<
      BrowseCollectionFilterParams<FacetKey>,
      BrowseQueryParams<FacetKey, SortKey>,
      BrowsePageParams<FacetKey, SortKey>
    >,
  ) {
    effect(() => {
      if (authService.token() === null) {
        queryClient.removeQueries({queryKey: keys.all()});
      }
    });
  }

  page(params: BrowsePageParams<FacetKey, SortKey>) {
    const normalized = normalizeBrowsePageParams(params);

    return queryOptions({
      queryKey: this.keys.boundedPage(normalized),
      queryFn: ({signal}) => this.fetchPage(normalized, null, signal),
      ...QUERY_DEFAULTS,
    });
  }

  infinitePage(params: BrowsePageParams<FacetKey, SortKey>) {
    const normalized = normalizeBrowsePageParams(params);

    return infiniteQueryOptions({
      queryKey: this.keys.infinitePage(normalized),
      queryFn: ({pageParam, signal}) => this.fetchPage(normalized, pageParam, signal),
      initialPageParam: null as string | null,
      getNextPageParam: page => findBrowsePageLink(page, 'next')?.href,
      ...QUERY_DEFAULTS,
    });
  }

  facets(params: BrowseCollectionFilterParams<FacetKey>) {
    const normalized = normalizeBrowseCollectionFilterParams(params);

    return queryOptions({
      queryKey: this.keys.facets(normalized),
      queryFn: ({signal}) => this.getMapped(
        `${this.baseUrl}/facets`,
        signal,
        mapBrowseFacetGroups,
        toBrowseCollectionHttpParams(normalized),
      ),
      ...QUERY_DEFAULTS,
    });
  }

  ids(params: BrowseQueryParams<FacetKey, SortKey>) {
    const normalized = normalizeBrowseQueryParams(params);

    return queryOptions({
      queryKey: this.keys.ids(normalized),
      queryFn: ({signal}) => this.get<Summary['id'][]>(
        `${this.baseUrl}/ids`,
        signal,
        toBrowseIdsHttpParams(normalized),
      ),
      ...QUERY_DEFAULTS,
      staleTime: 0,
      gcTime: 0,
    });
  }

  protected get<T>(url: string, signal: AbortSignal, params?: HttpParams): Promise<T> {
    return lastValueFrom(this.http.get<T>(url, {params}).pipe(takeUntil(abortSignal(signal))));
  }

  private fetchPage(
    params: BrowsePageParams<FacetKey, SortKey>,
    nextHref: string | null,
    signal: AbortSignal,
  ): Promise<BrowsePage<Summary>> {
    if (nextHref !== null) {
      return this.getMapped(
        `${API_CONFIG.BASE_URL}${nextHref}`,
        signal,
        mapBrowsePage<Summary>,
      );
    }

    return this.getMapped(
      `${this.baseUrl}/page`,
      signal,
      mapBrowsePage<Summary>,
      toBrowsePageHttpParams(params),
    );
  }

  private getMapped<Raw, Result>(
    url: string,
    signal: AbortSignal,
    project: (value: Raw) => Result,
    params?: HttpParams,
  ): Promise<Result> {
    return this.get<Raw>(url, signal, params).then(project);
  }
}
