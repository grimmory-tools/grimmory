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
import {BookDetail, BookRecommendation} from './book-response.models';
import {BookQueryService} from './book-query.service';

describe('BookQueryService', () => {
  let service: BookQueryService;
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
        BookQueryService,
      ],
    });

    service = TestBed.inject(BookQueryService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

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
});
