import {HttpClient, HttpParams} from '@angular/common/http';
import {effect, inject} from '@angular/core';
import {
  type InfiniteData,
  infiniteQueryOptions,
  type QueryFunctionContext,
  type QueryKey,
  queryOptions,
  QueryClient,
} from '@tanstack/angular-query-experimental';

import {API_CONFIG} from '../config/api-config';
import {withBrowseFacetValues} from '../../shared/browse/facets';
import {AuthService} from '../../shared/service/auth.service';
import {BrowseFacetGroup, BrowseFacetIndex, BrowsePage, findBrowsePageLink} from './browse.models';
import {
  mapBrowseFacetIndex,
  mapBrowseFacetPage,
  mapBrowsePage,
  type RawBrowsePage,
  type RawFacetResponse,
} from './browse-response';
import {BrowseQueryKeys} from './browse-query-keys';
import {
  BrowseCollectionFilterParams,
  BrowsePageParams,
  BrowseQueryParams,
  normalizeBrowseCollectionFilterParams,
  normalizeBrowsePageParams,
  normalizeBrowseQueryParams,
  toCollectionHttpParams,
  toIdsHttpParams,
  toPageHttpParams,
} from './browse-query-params';
import {QUERY_DEFAULTS, toAbortablePromise} from './query-transport';

const FACET_PAGE_SIZE = 100;

export interface BrowseQueryConfig<FacetKey extends string, SortKey extends string> {
  readonly path: string;
  readonly keys: BrowseQueryKeys<FacetKey, SortKey>;
}

export abstract class BrowseQueryService<Item extends {id: number}, FacetKey extends string, SortKey extends string> {
  private readonly http = inject(HttpClient);
  protected readonly authService = inject(AuthService);
  private readonly queryClient = inject(QueryClient);
  private readonly keys: BrowseQueryKeys<FacetKey, SortKey>;
  protected readonly baseUrl: string;

  protected constructor({path, keys}: BrowseQueryConfig<FacetKey, SortKey>) {
    this.keys = keys;
    this.baseUrl = `${API_CONFIG.BASE_URL}${path}`;

    effect(() => {
      if (this.authService.token() === null) {
        this.queryClient.removeQueries({queryKey: this.keys.all()});
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

    const key = this.keys.infinitePage(normalized);

    return infiniteQueryOptions({
      queryKey: key,
      queryFn: ({pageParam, queryKey, signal}: QueryFunctionContext<typeof key, string | null>) => this.fetchPage(
        normalized,
        pageParam ?? this.seededFirstPageHref(queryKey),
        signal,
      ),
      initialPageParam: null,
      getNextPageParam: page => findBrowsePageLink(page, 'next')?.href,
      ...QUERY_DEFAULTS,
    });
  }

  private seededFirstPageHref(queryKey: QueryKey): string | null {
    const firstPage = this.queryClient.getQueryData<InfiniteData<BrowsePage<Item>>>(queryKey)?.pages[0];
    return firstPage ? findBrowsePageLink(firstPage, 'self')?.href ?? null : null;
  }

  restartInfinitePage(params: BrowsePageParams<FacetKey, SortKey>): Promise<void> {
    const normalized = normalizeBrowsePageParams(params);
    return this.queryClient.resetQueries({
      queryKey: this.keys.infinitePage(normalized),
      exact: true,
    });
  }

  facetIndex(params: BrowseCollectionFilterParams<FacetKey>) {
    const normalized = normalizeBrowseCollectionFilterParams(params);

    return queryOptions({
      queryKey: this.keys.facetIndex(normalized),
      queryFn: ({signal}): Promise<BrowseFacetIndex> =>
        this.get<RawFacetResponse>(`${this.baseUrl}/facets`, signal, toCollectionHttpParams(normalized))
          .then(mapBrowseFacetIndex),
      ...QUERY_DEFAULTS,
    });
  }

  facet(key: FacetKey, params: BrowseCollectionFilterParams<FacetKey>, search = '') {
    const otherFacets = withBrowseFacetValues(params.facets, key, []);
    const normalized = normalizeBrowseCollectionFilterParams({...params, facets: otherFacets});
    const term = search.trim();
    let httpParams = toCollectionHttpParams(normalized).set('size', FACET_PAGE_SIZE);
    if (term) {
      httpParams = httpParams.set('search', term);
    }

    return queryOptions({
      queryKey: this.keys.facet(key, normalized, term),
      queryFn: ({signal}): Promise<BrowseFacetGroup> =>
        this.get<RawFacetResponse>(`${this.baseUrl}/facets/${encodeURIComponent(key)}`, signal, httpParams)
          .then(mapBrowseFacetPage),
      ...QUERY_DEFAULTS,
    });
  }

  ids(params: BrowseQueryParams<FacetKey, SortKey>) {
    const normalized = normalizeBrowseQueryParams(params);

    return queryOptions({
      queryKey: this.keys.ids(normalized),
      queryFn: ({signal}) => this.get<number[]>(
        `${this.baseUrl}/ids`,
        signal,
        toIdsHttpParams(normalized),
      ),
      ...QUERY_DEFAULTS,
      staleTime: 0,
      gcTime: 0,
    });
  }

  protected get<T>(url: string, signal: AbortSignal, params?: HttpParams): Promise<T> {
    return toAbortablePromise(this.http.get<T>(url, {params}), signal);
  }

  private fetchPage(
    params: BrowsePageParams<FacetKey, SortKey>,
    nextHref: string | null,
    signal: AbortSignal,
  ): Promise<BrowsePage<Item>> {
    const page = nextHref !== null
      ? this.get<RawBrowsePage<Item>>(`${API_CONFIG.BASE_URL}${nextHref}`, signal)
      : this.get<RawBrowsePage<Item>>(`${this.baseUrl}/page`, signal, toPageHttpParams(params));
    return page.then(mapBrowsePage);
  }
}
