import {HttpClient} from '@angular/common/http';
import {HttpTestingController} from '@angular/common/http/testing';
import {Injectable, inject} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {injectInfiniteQuery, QueryClient} from '@tanstack/angular-query-experimental';
import {afterEach, beforeEach, describe, expect, expectTypeOf, it, vi} from 'vitest';

import {API_CONFIG} from '../config/api-config';
import {
  createAuthServiceStub,
  createQueryClientHarness,
  flushSignalAndQueryEffects,
} from '../testing/query-testing';
import {AuthService} from '../../shared/service/auth.service';
import {createBrowseQueryKeys} from './browse-query-keys';
import {BrowsePageParams} from './browse-query-params';
import {BrowsePage} from './browse.models';
import {BrowseQueryService} from './browse-query.service';

type TestFacetKey = 'genre';
type TestSortKey = 'name';

interface TestSummary {
  id: string;
  name: string;
}

type TestPageParams = BrowsePageParams<TestFacetKey, TestSortKey>;

const PARAMS: TestPageParams = {
  query: '  earthsea  ',
  facets: {genre: ['Fantasy']},
  facetLogic: 'or',
  sort: [{key: 'name', direction: 'asc'}],
  size: 20,
};

const testQueryKeys = createBrowseQueryKeys<
  Omit<TestPageParams, 'sort' | 'size'>,
  Omit<TestPageParams, 'size'>,
  TestPageParams
>('test-items');

function page(ids: string[]): BrowsePage<TestSummary> {
  return {
    content: ids.map(id => ({id, name: id})),
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
class TestBrowseQueryService extends BrowseQueryService<
  TestSummary,
  TestFacetKey,
  TestSortKey
> {
  constructor() {
    super(
      inject(HttpClient),
      inject(AuthService),
      inject(QueryClient),
      `${API_CONFIG.BASE_URL}/api/v1/test-items`,
      testQueryKeys,
    );
  }
}

@Injectable()
class InfiniteQueryHost {
  private readonly service = inject(TestBrowseQueryService);
  readonly query = injectInfiniteQuery(() => this.service.infinitePage(PARAMS));
}

describe('BrowseQueryService', () => {
  let service: TestBrowseQueryService;
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
        TestBrowseQueryService,
        InfiniteQueryHost,
      ],
    });

    service = TestBed.inject(TestBrowseQueryService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
  });

  it('removes its resource cache when the authenticated session ends', () => {
    const key = testQueryKeys.boundedPage(PARAMS);
    queryClient.setQueryData(key, page(['earthsea']));

    authService.token.set(null);
    flushSignalAndQueryEffects();

    expect(queryClient.getQueryData(key)).toBeUndefined();
  });

  it('fetches one normalized bounded page', async () => {
    const resultPromise = queryClient.fetchQuery(service.page(PARAMS));
    const request = http.expectOne(
      `${API_CONFIG.BASE_URL}/api/v1/test-items/page?facet_logic=or&query=earthsea&facet=genre:Fantasy&sort=name&size=20`,
    );
    request.flush(page(['earthsea']));

    await expect(resultPromise).resolves.toMatchObject({content: [{id: 'earthsea'}]});
  });

  it('maps facet responses without page-only parameters', async () => {
    const resultPromise = queryClient.fetchQuery(service.facets(PARAMS));
    const request = http.expectOne(
      `${API_CONFIG.BASE_URL}/api/v1/test-items/facets?facet_logic=or&query=earthsea&facet=genre:Fantasy`,
    );
    request.flush({
      facets: [{
        metadata: {rel: 'facet', key: 'genre', title: 'Genre'},
        links: [{
          rel: ['self', 'facet'],
          href: '/api/v1/test-items/page?facet=genre%3AFantasy',
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

  it('supports string resource IDs and excludes page size from ID requests', async () => {
    const resultPromise = queryClient.fetchQuery(service.ids(PARAMS));
    expectTypeOf(resultPromise).toEqualTypeOf<Promise<string[]>>();
    const request = http.expectOne(
      `${API_CONFIG.BASE_URL}/api/v1/test-items/ids?facet_logic=or&query=earthsea&facet=genre:Fantasy&sort=name`,
    );
    request.flush(['earthsea', 'the-expanse']);

    await expect(resultPromise).resolves.toEqual(['earthsea', 'the-expanse']);
  });

  it('follows the exact next href for an infinite query', async () => {
    const host = TestBed.inject(InfiniteQueryHost);
    flushSignalAndQueryEffects();

    const firstRequest = http.expectOne(
      `${API_CONFIG.BASE_URL}/api/v1/test-items/page?facet_logic=or&query=earthsea&facet=genre:Fantasy&sort=name&size=20`,
    );
    firstRequest.flush({
      ...page(['earthsea']),
      links: [{
        rel: 'next',
        href: '/api/v1/test-items/page?facet=genre%3AFantasy&cursor=opaque',
        type: 'application/json',
      }],
    });
    await vi.waitFor(() => expect(host.query.isSuccess()).toBe(true));

    const nextPromise = host.query.fetchNextPage();
    http.expectOne(
      `${API_CONFIG.BASE_URL}/api/v1/test-items/page?facet=genre%3AFantasy&cursor=opaque`,
    ).flush(page(['the-expanse']));
    const nextResult = await nextPromise;

    expect(nextResult.data?.pages.flatMap(current => current.content.map(item => item.id)))
      .toEqual(['earthsea', 'the-expanse']);
  });

  it('stops paging when the backend emits no next link', async () => {
    const host = TestBed.inject(InfiniteQueryHost);
    flushSignalAndQueryEffects();

    http.expectOne(
      `${API_CONFIG.BASE_URL}/api/v1/test-items/page?facet_logic=or&query=earthsea&facet=genre:Fantasy&sort=name&size=20`,
    ).flush(page(['earthsea']));
    await vi.waitFor(() => expect(host.query.isSuccess()).toBe(true));

    expect(host.query.hasNextPage()).toBe(false);
  });

  it('cancels an active HTTP request through the query signal', async () => {
    const options = service.page(PARAMS);
    const resultPromise = queryClient.fetchQuery(options);
    const request = http.expectOne(
      `${API_CONFIG.BASE_URL}/api/v1/test-items/page?facet_logic=or&query=earthsea&facet=genre:Fantasy&sort=name&size=20`,
    );

    await queryClient.cancelQueries({queryKey: options.queryKey});

    expect(request.cancelled).toBe(true);
    await expect(resultPromise).rejects.toBeDefined();
  });
});
