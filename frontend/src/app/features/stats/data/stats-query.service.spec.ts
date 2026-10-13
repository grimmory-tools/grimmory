import {TestBed} from '@angular/core/testing';
import {QueryClient} from '@tanstack/angular-query-experimental';
import {beforeEach, describe, expect, it} from 'vitest';

import {
  createAuthServiceStub,
  createQueryClientHarness,
  flushSignalAndQueryEffects,
} from '../../../core/testing/query-testing';
import {AuthService} from '../../../shared/service/auth.service';
import {type StatsScopeParams} from './stats-query-keys';
import {StatsQueryService} from './stats-query.service';

const SCOPE: StatsScopeParams = {
  facets: {library: ['3']},
  facetLogic: 'and',
  tz: 'Europe/London',
};

describe('StatsQueryService', () => {
  let service: StatsQueryService;
  let queryClient: QueryClient;
  let authService: ReturnType<typeof createAuthServiceStub>;

  beforeEach(() => {
    const harness = createQueryClientHarness();
    queryClient = harness.queryClient;
    authService = createAuthServiceStub();
    queryClient.setDefaultOptions({queries: {retry: false}});

    TestBed.configureTestingModule({
      providers: [
        ...harness.providers,
        {provide: AuthService, useValue: authService},
        StatsQueryService,
      ],
    });

    service = TestBed.inject(StatsQueryService);
  });

  it('removes its cached stats when the authenticated session ends', () => {
    const {queryKey} = service.genres(SCOPE);
    queryClient.setQueryData(queryKey, {rows: [{genre: 'Fantasy', seconds: 3600}]});

    authService.token.set('token-456');
    flushSignalAndQueryEffects();

    expect(queryClient.getQueryData(queryKey)).toEqual({rows: [{genre: 'Fantasy', seconds: 3600}]});

    authService.token.set(null);
    flushSignalAndQueryEffects();

    expect(queryClient.getQueryData(queryKey)).toBeUndefined();
  });
});
