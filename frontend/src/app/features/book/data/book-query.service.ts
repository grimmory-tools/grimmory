import {HttpClient} from '@angular/common/http';
import {effect, inject, Service} from '@angular/core';
import {
  type InfiniteData,
  infiniteQueryOptions,
  type QueryKey,
  queryOptions,
  QueryClient,
} from '@tanstack/angular-query-experimental';
import {API_CONFIG} from '../../../core/config/api-config';
import {findBrowsePageLink} from '../../../core/data/browse.models';
import {mapBrowseFacetIndex, mapBrowseFacetPage, mapBrowsePage} from '../../../core/data/browse-response';
import {browseFacetValues, withBrowseFacetValues} from '../../../shared/browse/facets';
import {bookQueryKeys} from './book-query-keys';
import {
  BookCollectionFilterParams,
  BookDescriptionOptions,
  BookPageParams,
  BookQueryFacetKey,
  BookQueryParams,
  normalizeBookCollectionFilterParams,
  normalizeBookPageParams,
  normalizeBookQueryParams,
  toCollectionHttpParams,
  toIdsHttpParams,
  toPageHttpParams,
} from './book-query-params';
import {
  BookDuplicateGroup,
  BookDuplicateRequest,
  BookPage,
} from './book-query.models';
import {BookDetail, BookRecommendation, BookReviewResponse, BookSummary} from './book-response.models';
import {httpGet, QUERY_DEFAULTS, toAbortablePromise} from '../../../core/data/query-transport';
import {AuthService} from '../../../shared/service/auth.service';

const FACET_PAGE_SIZE = 100;
const BOOK_BATCH_CHUNK_SIZE = 500;

@Service()
export class BookQueryService {
  private readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService);
  private readonly queryClient = inject(QueryClient);
  private readonly booksUrl = `${API_CONFIG.BASE_URL}/api/v1/books`;
  private readonly reviewsUrl = `${API_CONFIG.BASE_URL}/api/v1/reviews`;

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
      queryFn: ({pageParam, queryKey, signal}) => this.fetchPage(
        normalized,
        pageParam ?? this.seededFirstPageHref(queryKey),
        signal,
      ),
      initialPageParam: null as string | null,
      getNextPageParam: page => findBrowsePageLink(page, 'next')?.href,
      ...QUERY_DEFAULTS,
    });
  }

  private seededFirstPageHref(queryKey: QueryKey): string | null {
    const firstPage = this.queryClient.getQueryData<InfiniteData<BookPage>>(queryKey)?.pages[0];
    return firstPage ? findBrowsePageLink(firstPage, 'self')?.href ?? null : null;
  }

  restartInfinitePage(params: BookPageParams): Promise<void> {
    const normalized = normalizeBookPageParams(params);
    return this.queryClient.resetQueries({
      queryKey: bookQueryKeys.infinitePage(normalized),
      exact: true,
    });
  }

  facetIndex(params: BookCollectionFilterParams) {
    const normalized = normalizeBookCollectionFilterParams(params);

    return queryOptions({
      queryKey: bookQueryKeys.facetIndex(normalized),
      queryFn: async ({signal}) => mapBrowseFacetIndex(await httpGet(
        this.http,
        `${this.booksUrl}/facets`,
        signal,
        toCollectionHttpParams(normalized),
      )),
      ...QUERY_DEFAULTS,
    });
  }

  facet(key: BookQueryFacetKey, params: BookCollectionFilterParams, search = '') {
    const own = browseFacetValues(params.facets, key);
    const otherFacets = own.matchAll ? params.facets : withBrowseFacetValues(params.facets, key, {...own, include: []});
    const normalized = normalizeBookCollectionFilterParams({...params, facets: otherFacets});
    const term = search.trim();
    let httpParams = toCollectionHttpParams(normalized).set('size', FACET_PAGE_SIZE);
    if (term) {
      httpParams = httpParams.set('search', term);
    }

    return queryOptions({
      queryKey: bookQueryKeys.facet(key, normalized, term),
      queryFn: async ({signal}) => mapBrowseFacetPage(await httpGet(
        this.http,
        `${this.booksUrl}/facets/${encodeURIComponent(key)}`,
        signal,
        httpParams,
      )),
      ...QUERY_DEFAULTS,
    });
  }

  ids(params: BookQueryParams) {
    const normalized = normalizeBookQueryParams(params);

    return queryOptions({
      queryKey: bookQueryKeys.ids(normalized),
      queryFn: ({signal}) => httpGet<number[]>(
        this.http,
        `${this.booksUrl}/ids`,
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
      queryFn: ({signal}) => httpGet<BookDetail>(
        this.http,
        `${this.booksUrl}/${bookId}`,
        signal,
        {withDescription},
      ),
      ...QUERY_DEFAULTS,
    });
  }

  reviews(bookId: number) {
    return queryOptions({
      queryKey: bookQueryKeys.reviews(bookId),
      queryFn: async ({signal}): Promise<readonly BookReviewResponse[]> =>
        (await httpGet<readonly BookReviewResponse[] | null>(this.http, `${this.reviewsUrl}/book/${bookId}`, signal)) ?? [],
      ...QUERY_DEFAULTS,
    });
  }

  batch(ids: readonly number[]) {
    const distinctIds = [...new Set(ids)];
    return queryOptions({
      queryKey: bookQueryKeys.batch(distinctIds),
      queryFn: ({signal}) => this.fetchBatch(distinctIds, signal),
      ...QUERY_DEFAULTS,
    });
  }

  recommendations(bookId: number, limit: number) {
    return queryOptions({
      queryKey: bookQueryKeys.recommendation(bookId, limit),
      queryFn: ({signal}) => httpGet<BookRecommendation[]>(
        this.http,
        `${this.booksUrl}/${bookId}/recommendations`,
        signal,
        {limit},
      ),
      ...QUERY_DEFAULTS,
    });
  }

  duplicates(request: BookDuplicateRequest) {
    return queryOptions({
      queryKey: bookQueryKeys.duplicates(request),
      queryFn: ({signal}) => toAbortablePromise(
        this.http.post<BookDuplicateGroup[]>(`${this.booksUrl}/duplicates`, request), signal),
      ...QUERY_DEFAULTS,
      staleTime: 0,
    });
  }

  private async fetchPage(
    params: BookPageParams,
    nextHref: string | null,
    signal: AbortSignal,
  ): Promise<BookPage> {
    if (nextHref !== null) {
      return mapBrowsePage<BookSummary>(await httpGet(this.http, `${API_CONFIG.BASE_URL}${nextHref}`, signal));
    }

    return mapBrowsePage<BookSummary>(await httpGet(
      this.http,
      `${this.booksUrl}/page`,
      signal,
      toPageHttpParams(params),
    ));
  }

  private async fetchBatch(ids: readonly number[], signal: AbortSignal): Promise<BookSummary[]> {
    const books: BookSummary[] = [];
    for (let offset = 0; offset < ids.length; offset += BOOK_BATCH_CHUNK_SIZE) {
      const chunk = ids.slice(offset, offset + BOOK_BATCH_CHUNK_SIZE);
      books.push(...await httpGet<BookSummary[]>(
        this.http,
        `${this.booksUrl}/batch`,
        signal,
        {ids: chunk.join(',')},
      ));
    }
    return books;
  }
}
