import {HttpClient, HttpParams} from '@angular/common/http';
import {effect, inject, Injectable} from '@angular/core';
import {
  infiniteQueryOptions,
  queryOptions,
  QueryClient,
} from '@tanstack/angular-query-experimental';
import {lastValueFrom, Observable, map, takeUntil} from 'rxjs';

import {API_CONFIG} from '../../../core/config/api-config';
import {findBrowsePageLink} from '../../../core/data/browse.models';
import {mapBrowseFacetGroups, mapBrowsePage} from '../../../core/data/browse-response';
import {bookQueryKeys} from './book-query-keys';
import {
  BookCollectionFilterParams,
  BookDescriptionOptions,
  BookPageParams,
  BookQueryParams,
  normalizeBookCollectionFilterParams,
  normalizeBookPageParams,
  normalizeBookQueryParams,
  toCollectionHttpParams,
  toIdsHttpParams,
  toPageHttpParams,
} from './book-query-params';
import {BookFacetGroup, BookPage} from './book-query.models';
import {BookDetail, BookRecommendation, BookSummary} from './book-response.models';
import {abortSignal, QUERY_DEFAULTS} from '../../../core/data/query-transport';
import {AuthService} from '../../../shared/service/auth.service';

@Injectable({providedIn: 'root'})
export class BookQueryService {
  private readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService);
  private readonly queryClient = inject(QueryClient);
  private readonly baseUrl = `${API_CONFIG.BASE_URL}/api/v1/books`;

  constructor() {
    effect(() => {
      if (this.authService.token() === null) {
        this.queryClient.removeQueries({queryKey: bookQueryKeys.all()});
      }
    });
  }

  page(params: BookPageParams) {
    const normalized = normalizeBookPageParams(params);

    return queryOptions({
      queryKey: bookQueryKeys.boundedPage(normalized),
      queryFn: ({signal}) => this.fetchPage(normalized, null, signal),
      ...QUERY_DEFAULTS,
    });
  }

  infinitePage(params: BookPageParams) {
    const normalized = normalizeBookPageParams(params);

    return infiniteQueryOptions({
      queryKey: bookQueryKeys.infinitePage(normalized),
      queryFn: ({pageParam, signal}) => this.fetchPage(normalized, pageParam, signal),
      initialPageParam: null as string | null,
      getNextPageParam: page => findBrowsePageLink(page, 'next')?.href,
      ...QUERY_DEFAULTS,
    });
  }

  facets(params: BookCollectionFilterParams) {
    const normalized = normalizeBookCollectionFilterParams(params);

    return queryOptions({
      queryKey: bookQueryKeys.facets(normalized),
      queryFn: ({signal}): Promise<BookFacetGroup[]> => this.getMapped(
        `${this.baseUrl}/facets`,
        signal,
        mapBrowseFacetGroups,
        toCollectionHttpParams(normalized),
      ),
      ...QUERY_DEFAULTS,
    });
  }

  ids(params: BookQueryParams) {
    const normalized = normalizeBookQueryParams(params);

    return queryOptions({
      queryKey: bookQueryKeys.ids(normalized),
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

  private fetchPage(
    params: BookPageParams,
    nextHref: string | null,
    signal: AbortSignal,
  ): Promise<BookPage> {
    if (nextHref !== null) {
      return this.getMapped(
        `${API_CONFIG.BASE_URL}${nextHref}`,
        signal,
        mapBrowsePage<BookSummary>,
      );
    }

    return this.getMapped(
      `${this.baseUrl}/page`,
      signal,
      mapBrowsePage<BookSummary>,
      toPageHttpParams(params),
    );
  }

  private get<T>(url: string, signal: AbortSignal, params?: HttpParams): Promise<T> {
    return this.finalize(this.http.get<T>(url, {params}), signal);
  }

  private getMapped<TRaw, T>(
    url: string,
    signal: AbortSignal,
    project: (value: TRaw) => T,
    params?: HttpParams,
  ): Promise<T> {
    return this.finalize(this.http.get<TRaw>(url, {params}).pipe(map(project)), signal);
  }

  private finalize<T>(source: Observable<T>, signal: AbortSignal): Promise<T> {
    return lastValueFrom(source.pipe(takeUntil(abortSignal(signal))));
  }
}
