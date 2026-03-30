import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {Router} from '@angular/router';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {firstValueFrom} from 'rxjs';

import {API_CONFIG} from '../../../core/config/api-config';
import {SetupRedirectGuard} from './setup-redirect.guard';

describe('SetupRedirectGuard', () => {
  const setupStatusUrl = `${API_CONFIG.BASE_URL}/api/v1/setup/status`;
  const router = {
    navigate: vi.fn(() => Promise.resolve(true)),
  };

  let guard: SetupRedirectGuard;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
    router.navigate.mockClear();

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {provide: Router, useValue: router},
        SetupRedirectGuard,
      ]
    });

    guard = TestBed.inject(SetupRedirectGuard);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    TestBed.resetTestingModule();
  });

  it('sends users to setup when the instance is not configured', async () => {
    const resultPromise = firstValueFrom(guard.canActivate());

    const request = httpTestingController.expectOne(setupStatusUrl);
    request.flush({data: false});

    await expect(resultPromise).resolves.toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/setup']);
  });

  it('sends users to the dashboard when setup is complete', async () => {
    const resultPromise = firstValueFrom(guard.canActivate());

    const request = httpTestingController.expectOne(setupStatusUrl);
    request.flush({data: true});

    await expect(resultPromise).resolves.toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
  });
});
