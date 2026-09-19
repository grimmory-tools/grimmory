import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {AuthService} from '../../../shared/service/auth.service';
import {MetadataProviderService} from './metadata-provider.service';

describe('MetadataProviderService', () => {
  let service: MetadataProviderService;
  let httpTestingController: HttpTestingController;
  let authService: {getInternalAccessToken: ReturnType<typeof vi.fn>};

  beforeEach(() => {
    authService = {
      getInternalAccessToken: vi.fn(() => 'token-123'),
    };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        MetadataProviderService,
        {provide: AuthService, useValue: authService},
      ],
    });

    service = TestBed.inject(MetadataProviderService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('requests provider detail through HTTP endpoints', () => {
    service.fetchMetadataDetail('google', 'abc123').subscribe();

    const detailRequest = httpTestingController.expectOne(req =>
      req.url.endsWith('v1/metadata/providers/google/fetch/abc123')
    );
    expect(detailRequest.request.method).toBe('GET');
    detailRequest.flush({});
  });
});
