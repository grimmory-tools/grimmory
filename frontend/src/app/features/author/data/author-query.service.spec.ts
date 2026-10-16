import {HttpTestingController} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {QueryClient} from '@tanstack/angular-query-experimental';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';

import {API_CONFIG} from '../../../core/config/api-config';
import {createAuthServiceStub, createQueryClientHarness} from '../../../core/testing/query-testing';
import {AuthService} from '../../../shared/service/auth.service';
import {AuthorQueryService} from './author-query.service';

describe('AuthorQueryService', () => {
  let service: AuthorQueryService;
  let queryClient: QueryClient;
  let http: HttpTestingController;

  beforeEach(() => {
    const harness = createQueryClientHarness();
    queryClient = harness.queryClient;

    TestBed.configureTestingModule({
      providers: [
        ...harness.providers,
        {provide: AuthService, useValue: createAuthServiceStub()},
      ],
    });

    service = TestBed.inject(AuthorQueryService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('searches metadata by name, or by ASIN when one is given', () => {
    void queryClient.query(service.metadataSearch(9, {query: 'Ada', region: 'us', asin: ''}));
    void queryClient.query(service.metadataSearch(9, {query: 'Ada', region: 'us', asin: 'B000123'}));

    const [byName, byAsin] = http.match(request => request.url === `${API_CONFIG.BASE_URL}/api/v1/authors/9/search-metadata`);
    expect(byName.request.params.toString()).toBe('region=us&q=Ada');
    expect(byAsin.request.params.toString()).toBe('region=us&asin=B000123');
    byName.flush([]);
    byAsin.flush([]);
  });
});
