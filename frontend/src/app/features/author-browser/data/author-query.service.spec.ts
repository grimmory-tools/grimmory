import {HttpTestingController} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {QueryClient} from '@tanstack/angular-query-experimental';
import {afterEach, beforeEach, describe, expect, expectTypeOf, it} from 'vitest';

import {API_CONFIG} from '../../../core/config/api-config';
import {
  createAuthServiceStub,
  createQueryClientHarness,
} from '../../../core/testing/query-testing';
import {AuthService} from '../../../shared/service/auth.service';
import {AuthorPageParams} from './author-query-params';
import {AuthorPage} from './author-query.models';
import {AuthorQueryService} from './author-query.service';
import {AuthorDetail} from './author-response.models';

const PARAMS: AuthorPageParams = {
  query: '  le guin  ',
  facets: {has_photo: ['true']},
  facetLogic: 'or',
  sort: [{key: 'name', direction: 'asc'}],
  size: 20,
};

function page(): AuthorPage {
  return {
    content: [{id: 7, name: 'Ursula K. Le Guin', bookCount: 4, hasPhoto: true}],
    page: {
      number: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      cursor: 'opaque-cursor',
    },
    links: [],
  };
}

describe('AuthorQueryService', () => {
  let service: AuthorQueryService;
  let queryClient: QueryClient;
  let http: HttpTestingController;

  beforeEach(() => {
    const harness = createQueryClientHarness();
    queryClient = harness.queryClient;
    queryClient.setDefaultOptions({queries: {retry: false}});

    TestBed.configureTestingModule({
      providers: [
        ...harness.providers,
        {provide: AuthService, useValue: createAuthServiceStub()},
        AuthorQueryService,
      ],
    });

    service = TestBed.inject(AuthorQueryService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('binds a normalized author request to the shared browse page contract', async () => {
    const resultPromise = queryClient.fetchQuery(service.page(PARAMS));
    expectTypeOf(resultPromise).toEqualTypeOf<Promise<AuthorPage>>();

    http.expectOne(
      `${API_CONFIG.BASE_URL}/api/v1/authors/page?facet_logic=or&query=le%20guin&facet=has_photo:true&sort=name&size=20`,
    ).flush(page());

    await expect(resultPromise).resolves.toMatchObject({
      content: [{id: 7, name: 'Ursula K. Le Guin'}],
    });
  });

  it('fetches author detail', async () => {
    const resultPromise = queryClient.fetchQuery(service.detail(7));
    expectTypeOf(resultPromise).toEqualTypeOf<Promise<AuthorDetail>>();

    const response: AuthorDetail = {
      id: 7,
      name: 'Ursula K. Le Guin',
      description: 'American author.',
      asin: 'B000AQ1X2C',
      nameLocked: false,
      descriptionLocked: false,
      asinLocked: false,
      photoLocked: false,
    };
    http.expectOne(`${API_CONFIG.BASE_URL}/api/v1/authors/7`).flush(response);

    await expect(resultPromise).resolves.toEqual(response);
  });
});
