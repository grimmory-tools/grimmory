import {HttpErrorResponse} from '@angular/common/http';
import {HttpTestingController} from '@angular/common/http/testing';
import {Injectable, inject} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {injectInfiniteQuery, QueryClient} from '@tanstack/angular-query-experimental';
import {afterEach, beforeEach, describe, expect, expectTypeOf, it, vi} from 'vitest';

import {API_CONFIG} from '../../../core/config/api-config';
import {
  createAuthServiceStub,
  createQueryClientHarness,
  flushSignalAndQueryEffects,
} from '../../../core/testing/query-testing';
import {AuthService} from '../../../shared/service/auth.service';
import {bookQueryKeys} from './book-query-keys';
import {BookPageParams} from './book-query-params';
import {BookPage} from './book-query.models';
import {BookDetail, BookRecommendation} from './book-response.models';
import {retryTransientQueryError} from '../../../core/data/query-transport';
import {BookQueryService} from './book-query.service';

const PARAMS: BookPageParams = {
  query: 'dune',
  facets: {genre: ['Science Fiction']},
  facetLogic: 'or',
  sort: [{key: 'title', direction: 'asc'}],
  size: 20,
};

function page(ids: number[]): BookPage {
  return {
    content: ids.map(id => ({id, libraryId: 1, libraryName: 'Library'})),
    page: {
      number: 0,
      size: 20,
      totalElements: ids.length,
      totalPages: ids.length === 0 ? 0 : 1,
      cursor: 'opaque-cursor',
    },
    links: [],
  };
}

@Injectable()
class InfiniteQueryHost {
  private readonly books = inject(BookQueryService);
  readonly query = injectInfiniteQuery(() => this.books.infinitePage(PARAMS));
}

describe('BookQueryService', () => {
  let service: BookQueryService;
  let queryClient: QueryClient;
  let authService: ReturnType<typeof createAuthServiceStub>;
  let http: HttpTestingController;

  beforeEach(() => {
    const harness = createQueryClientHarness();
    queryClient = harness.queryClient;
    authService = createAuthServiceStub();
    queryClient.setDefaultOptions({queries: {retry: false}});

    TestBed.configureTestingModule({
      providers: [
        ...harness.providers,
        {provide: AuthService, useValue: authService},
        BookQueryService,
        InfiniteQueryHost,
      ],
    });

    service = TestBed.inject(BookQueryService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
  });

  it('removes its cached book queries when the authenticated session ends', () => {
    const key = bookQueryKeys.detail(7, false);
    queryClient.setQueryData(key, {id: 7});

    authService.token.set(null);
    flushSignalAndQueryEffects();

    expect(queryClient.getQueryData(key)).toBeUndefined();
  });

  it('fetches one bounded summary page with normalized parameters', async () => {
    const resultPromise = queryClient.fetchQuery(service.page(PARAMS));
    const request = http.expectOne(`${API_CONFIG.BASE_URL}/api/v1/books/page?facet_logic=or&query=dune&facet=genre:Science%20Fiction&sort=title&size=20`);
    request.flush(page([1, 2]));

    await expect(resultPromise).resolves.toMatchObject({
      content: [{id: 1}, {id: 2}],
    });
  });

  it('sends every selected facet value as a repeated facet parameter', async () => {
    const resultPromise = queryClient.fetchQuery(service.page({
      ...PARAMS,
      facets: {
        genre: ['Science Fiction'],
        language: ['English'],
      },
    }));
    const request = http.expectOne(
      `${API_CONFIG.BASE_URL}/api/v1/books/page?facet_logic=or&query=dune&facet=genre:Science%20Fiction&facet=language:English&sort=title&size=20`,
    );
    request.flush(page([1]));

    await expect(resultPromise).resolves.toMatchObject({content: [{id: 1}]});
  });

  it('fetches facets without sort or size', async () => {
    const resultPromise = queryClient.fetchQuery(service.facets(PARAMS));
    const request = http.expectOne(`${API_CONFIG.BASE_URL}/api/v1/books/facets?facet_logic=or&query=dune&facet=genre:Science%20Fiction`);
    request.flush({
      links: [{rel: 'self', href: '/api/v1/books/facets?query=dune', type: 'application/json'}],
      facets: [{
        metadata: {rel: 'facet', key: 'genre', title: 'Genre'},
        links: [{
          rel: ['self', 'facet'],
          href: '/api/v1/books/page?facet=genre%3AFantasy',
          type: 'application/json',
          title: 'Fantasy',
          value: 'Fantasy',
          properties: {numberOfItems: 4},
        }],
      }],
    });

    await expect(resultPromise).resolves.toEqual([{
      rel: 'facet',
      key: 'genre',
      title: 'Genre',
      values: [{value: 'Fantasy', title: 'Fantasy', count: 4, selected: true}],
    }]);
  });

  it('fetches matching IDs with sort but no size', async () => {
    const resultPromise = queryClient.fetchQuery(service.ids(PARAMS));
    const request = http.expectOne(`${API_CONFIG.BASE_URL}/api/v1/books/ids?facet_logic=or&query=dune&facet=genre:Science%20Fiction&sort=title`);
    request.flush([3, 1, 2]);

    await expect(resultPromise).resolves.toEqual([3, 1, 2]);
  });

  it('follows the exact next href for an infinite query', async () => {
    const host = TestBed.inject(InfiniteQueryHost);
    flushSignalAndQueryEffects();

    const firstRequest = http.expectOne(`${API_CONFIG.BASE_URL}/api/v1/books/page?facet_logic=or&query=dune&facet=genre:Science%20Fiction&sort=title&size=20`);
    expect(firstRequest.request.params.has('cursor')).toBe(false);
    firstRequest.flush({
      ...page([1]),
      links: [
        {
          rel: 'self',
          href: '/api/v1/books/page?cursor=origin',
          type: 'application/json',
        },
        {
          rel: 'next',
          href: '/api/v1/books/page?facet=genre%3AScience%20Fiction&cursor=opaque',
          type: 'application/json',
        },
      ],
    });
    await vi.waitFor(() => expect(host.query.isSuccess()).toBe(true));

    const nextPromise = host.query.fetchNextPage();
    const nextRequest = http.expectOne(`${API_CONFIG.BASE_URL}/api/v1/books/page?facet=genre%3AScience%20Fiction&cursor=opaque`);
    nextRequest.flush(page([2]));
    const nextResult = await nextPromise;

    expect(nextResult.data?.pages.flatMap(current => current.content.map(book => book.id)))
      .toEqual([1, 2]);
  });

  it('stops paging when the backend emits no next link', async () => {
    const host = TestBed.inject(InfiniteQueryHost);
    flushSignalAndQueryEffects();

    http.expectOne(`${API_CONFIG.BASE_URL}/api/v1/books/page?facet_logic=or&query=dune&facet=genre:Science%20Fiction&sort=title&size=20`)
      .flush(page([1]));
    await vi.waitFor(() => expect(host.query.isSuccess()).toBe(true));

    expect(host.query.hasNextPage()).toBe(false);
  });

  it('keys an infinite query by its normalized parameters alone so the cache is shared', () => {
    expect(service.infinitePage(PARAMS).queryKey).toEqual(service.infinitePage(PARAMS).queryKey);
  });

  it('fetches full book detail with the description flag', async () => {
    const resultPromise = queryClient.fetchQuery(service.detail(42, {withDescription: true}));
    expectTypeOf(resultPromise).toEqualTypeOf<Promise<BookDetail>>();
    const request = http.expectOne(`${API_CONFIG.BASE_URL}/api/v1/books/42?withDescription=true`);
    const response: BookDetail = {
      id: 42,
      libraryId: 1,
      libraryName: 'Library',
      metadata: {bookId: 42, title: 'Dune', description: 'Desert power.'},
    };
    request.flush(response);

    await expect(resultPromise).resolves.toMatchObject({
      id: 42,
      metadata: {description: 'Desert power.'},
    });
  });

  it('fetches recommendations and preserves similarity order', async () => {
    const resultPromise = queryClient.fetchQuery(service.recommendations(42, 2));
    expectTypeOf(resultPromise).toEqualTypeOf<Promise<BookRecommendation[]>>();
    const request = http.expectOne(`${API_CONFIG.BASE_URL}/api/v1/books/42/recommendations?limit=2`);
    const response: BookRecommendation[] = [
      {book: {id: 8, libraryId: 1, libraryName: 'Library'}, similarityScore: 0.4},
      {book: {id: 5, libraryId: 1, libraryName: 'Library'}, similarityScore: 0.9},
    ];
    request.flush(response);

    await expect(resultPromise).resolves.toMatchObject([
      {book: {id: 8}, similarityScore: 0.4},
      {book: {id: 5}, similarityScore: 0.9},
    ]);
  });

  it('cancels an active HTTP request through the query signal', async () => {
    const options = service.detail(42, {withDescription: false});
    const resultPromise = queryClient.fetchQuery(options);
    const request = http.expectOne(`${API_CONFIG.BASE_URL}/api/v1/books/42?withDescription=false`);

    await queryClient.cancelQueries({queryKey: options.queryKey});

    expect(request.cancelled).toBe(true);
    await expect(resultPromise).rejects.toBeDefined();
  });

  it('retries only transient failures and only twice', () => {
    const networkError = new HttpErrorResponse({status: 0});
    const badRequest = new HttpErrorResponse({status: 400});
    const serverError = new HttpErrorResponse({status: 503});

    expect(retryTransientQueryError(0, networkError)).toBe(true);
    expect(retryTransientQueryError(0, badRequest)).toBe(false);
    expect(retryTransientQueryError(0, serverError)).toBe(true);
    expect(retryTransientQueryError(0, new Error('Unexpected failure'))).toBe(false);
    expect(retryTransientQueryError(2, serverError)).toBe(false);
  });
});
