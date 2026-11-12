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

  it('loads an author by id and by name', async () => {
    const author = {id: 9, name: 'Ada Lovelace', nameLocked: false, descriptionLocked: false, asinLocked: false, photoLocked: false};
    const byId = queryClient.query(service.detail(9));
    const byName = queryClient.query(service.byName('Ada Lovelace'));

    http.expectOne(`${API_CONFIG.BASE_URL}/api/v1/authors/9`).flush(author);
    const nameRequest = http.expectOne(request => request.url === `${API_CONFIG.BASE_URL}/api/v1/authors/by-name`);
    expect(nameRequest.request.params.get('name')).toBe('Ada Lovelace');
    nameRequest.flush(author);

    await expect(byId).resolves.toEqual(author);
    await expect(byName).resolves.toEqual(author);
  });

  it('searches metadata by trimmed name, or by trimmed ASIN when one is given', () => {
    void queryClient.query(service.metadataSearch(9, {region: 'us', query: ' Ada '}));
    void queryClient.query(service.metadataSearch(9, {region: 'us', asin: ' B000123 '}));

    const [byName, byAsin] = http.match(request => request.url === `${API_CONFIG.BASE_URL}/api/v1/authors/9/search-metadata`);
    expect(byName.request.params.toString()).toBe('region=us&q=Ada');
    expect(byAsin.request.params.toString()).toBe('region=us&asin=B000123');
    byName.flush([]);
    byAsin.flush([]);
  });
});
