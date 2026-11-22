import {HttpClient} from '@angular/common/http';
import {effect, inject} from '@angular/core';
import {
  type InfiniteData,
  infiniteQueryOptions,
  type QueryFunctionContext,
  type QueryKey,
  queryOptions,
  QueryClient,
} from '@tanstack/angular-query';

import {API_CONFIG} from '../config/api-config';
import {browseFacetValues, withBrowseFacetValues} from '../../shared/browse/facets';
import {AuthService} from '../../shared/service/auth.service';
import {BrowseFacetGroup, BrowseFacetIndex, BrowsePage, findBrowsePageLink} from './browse.models';
import {
  mapBrowseFacetIndex,
  mapBrowseFacetPage,
  mapBrowsePage,
  type RawBrowsePage,
  type RawFacetResponse,
} from './browse-response';
import {
  type BrowseFacetPageOptions,
  type BrowseQueryKeys,
  DEFAULT_FACET_PAGE_SIZE,
} from './browse-query-keys';
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
import {httpGet, QUERY_DEFAULTS} from './query-transport';

export interface BrowseQueryConfig<FacetKey extends string, SortKey extends string> {
  readonly path: string;
  readonly keys: BrowseQueryKeys<FacetKey, SortKey>;
  readonly isFacetKey: (key: string) => key is FacetKey;
}

export abstract class BrowseQueryService<Item extends {id: number}, FacetKey extends string, SortKey extends string> {
  protected readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService);
  private readonly queryClient = inject(QueryClient);
  private readonly keys: BrowseQueryKeys<FacetKey, SortKey>;
  private readonly isFacetKey: (key: string) => key is FacetKey;
  protected readonly baseUrl: string;

  protected constructor({path, keys, isFacetKey}: BrowseQueryConfig<FacetKey, SortKey>) {
    this.keys = keys;
    this.isFacetKey = isFacetKey;
    this.baseUrl = `${API_CONFIG.BASE_URL}${path}`;

    effect(() => {
      if (this.authService.token() === null) {
        this.queryClient.removeQueries({queryKey: this.keys.all()});
      }
    });
  }

  page(params: BrowsePageParams<FacetKey, SortKey>) {
    const normalized = normalizeBrowsePageParams(params, this.isFacetKey);

    return queryOptions({
      queryKey: this.keys.boundedPage(normalized),
      queryFn: ({signal}) => this.fetchPage(normalized, null, signal),
      ...QUERY_DEFAULTS,
    });
  }

  infinitePage(params: BrowsePageParams<FacetKey, SortKey>, pageParams?: Readonly<Record<string, string>>) {
    const normalized = normalizeBrowsePageParams(params, this.isFacetKey);
    const key = withPageParams(this.keys.infinitePage(normalized), pageParams);

    return infiniteQueryOptions({
      queryKey: key,
      queryFn: ({pageParam, queryKey, signal}: QueryFunctionContext<typeof key, string | null>) => this.fetchPage(
        normalized,
        pageParam ?? this.seededFirstPageHref(queryKey),
        signal,
        pageParams,
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

  restartInfinitePage(
    params: BrowsePageParams<FacetKey, SortKey>,
    pageParams?: Readonly<Record<string, string>>,
  ): Promise<void> {
    const normalized = normalizeBrowsePageParams(params, this.isFacetKey);
    return this.queryClient.resetQueries({
      queryKey: withPageParams(this.keys.infinitePage(normalized), pageParams),
      exact: true,
    });
  }

  facetIndex(params: BrowseCollectionFilterParams<FacetKey>) {
    const normalized = normalizeBrowseCollectionFilterParams(params, this.isFacetKey);

    return queryOptions({
      queryKey: this.keys.facetIndex(normalized),
      queryFn: ({signal}): Promise<BrowseFacetIndex> =>
        httpGet<RawFacetResponse>(this.http, `${this.baseUrl}/facets`, signal, toCollectionHttpParams(normalized))
          .then(mapBrowseFacetIndex),
      ...QUERY_DEFAULTS,
    });
  }

  facet(
    key: FacetKey,
    params: BrowseCollectionFilterParams<FacetKey>,
    search = '',
    {page = 0, size = DEFAULT_FACET_PAGE_SIZE}: BrowseFacetPageOptions = {},
  ) {
    const own = browseFacetValues(params.facets, key);
    const otherFacets = own.matchAll ? params.facets : withBrowseFacetValues(params.facets, key, {...own, include: []});
    const normalized = normalizeBrowseCollectionFilterParams({...params, facets: otherFacets}, this.isFacetKey);
    const term = search.trim();
    let httpParams = toCollectionHttpParams(normalized).set('size', size);
    if (page > 0) {
      httpParams = httpParams.set('page', page);
    }
    if (term) {
      httpParams = httpParams.set('search', term);
    }

    return queryOptions({
      queryKey: this.keys.facet(key, normalized, term, {page, size}),
      queryFn: ({signal}): Promise<BrowseFacetGroup> =>
        httpGet<RawFacetResponse>(this.http, `${this.baseUrl}/facets/${encodeURIComponent(key)}`, signal, httpParams)
          .then(mapBrowseFacetPage),
      ...QUERY_DEFAULTS,
    });
  }

  ids(params: BrowseQueryParams<FacetKey, SortKey>) {
    const normalized = normalizeBrowseQueryParams(params, this.isFacetKey);

    return queryOptions({
      queryKey: this.keys.ids(normalized),
      queryFn: ({signal}) => httpGet<number[]>(
        this.http,
        `${this.baseUrl}/ids`,
        signal,
        toIdsHttpParams(normalized),
      ),
      ...QUERY_DEFAULTS,
      staleTime: 0,
      gcTime: 0,
    });
  }

  private fetchPage(
    params: BrowsePageParams<FacetKey, SortKey>,
    nextHref: string | null,
    signal: AbortSignal,
    pageParams?: Readonly<Record<string, string>>,
  ): Promise<BrowsePage<Item>> {
    const page = nextHref !== null
      ? httpGet<RawBrowsePage<Item>>(this.http, `${API_CONFIG.BASE_URL}${nextHref}`, signal)
      : httpGet<RawBrowsePage<Item>>(this.http, `${this.baseUrl}/page`, signal, toPageHttpParams(params).appendAll(pageParams ?? {}));
    return page.then(mapBrowsePage);
  }
}

function withPageParams<Key extends QueryKey>(
  key: Key,
  pageParams: Readonly<Record<string, string>> | undefined,
): Key | readonly [...Key, Readonly<Record<string, string>>] {
  return pageParams !== undefined && Object.keys(pageParams).length > 0 ? [...key, pageParams] as const : key;
}
