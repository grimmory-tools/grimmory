import {HttpTestingController} from '@angular/common/http/testing';
import {WritableSignal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {firstValueFrom} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';

import {createAuthServiceStub, createQueryClientHarness, flushQueryAsync, flushSignalAndQueryEffects, QueryClientHarness} from '../../../core/testing/query-testing';
import {AuthService} from '../../../shared/service/auth.service';
import {BookConversionService} from './book-conversion.service';

interface AuthServiceStub {
  token: WritableSignal<string | null>;
  getInternalAccessToken: () => string | null;
}

describe('BookConversionService', () => {
  let service: BookConversionService;
  let httpTestingController: HttpTestingController;
  let authService: AuthServiceStub;
  let queryClientHarness: QueryClientHarness;

  beforeEach(() => {
    authService = createAuthServiceStub();
    queryClientHarness = createQueryClientHarness();
    queryClientHarness.queryClient.setDefaultOptions({
      queries: {
        retry: false,
      },
    });

    TestBed.configureTestingModule({
      providers: [
        ...queryClientHarness.providers,
        BookConversionService,
        {provide: AuthService, useValue: authService},
      ],
    });

    service = TestBed.inject(BookConversionService);
    httpTestingController = TestBed.inject(HttpTestingController);
    flushSignalAndQueryEffects();
  });

  afterEach(() => {
    httpTestingController.verify();
    queryClientHarness.queryClient.clear();
    TestBed.resetTestingModule();
  });

  it('maps capability query data and posts conversion requests', async () => {
    expect(service.capability()).toEqual({available: false, supportedTargetFormats: []});
    expect(service.canConvert()).toBe(false);

    const capabilityRequest = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/books/conversion-capability'));
    expect(capabilityRequest.request.method).toBe('GET');
    capabilityRequest.flush({available: true, supportedTargetFormats: ['EPUB', 'MOBI']});
    await flushQueryAsync();

    expect(service.capability()).toEqual({available: true, supportedTargetFormats: ['EPUB', 'MOBI']});
    expect(service.canConvert()).toBe(true);

    const conversionPromise = firstValueFrom(service.convertBooks([1, 2], 'MOBI'));
    const conversionRequest = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/books/convert'));
    expect(conversionRequest.request.method).toBe('POST');
    expect(conversionRequest.request.body).toEqual({bookIds: [1, 2], targetFormat: 'MOBI'});
    conversionRequest.flush({acceptedCount: 2, targetFormat: 'MOBI'});

    await expect(conversionPromise).resolves.toEqual({acceptedCount: 2, targetFormat: 'MOBI'});
  });
});
