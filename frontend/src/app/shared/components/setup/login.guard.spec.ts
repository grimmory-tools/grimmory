import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {Router} from '@angular/router';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {firstValueFrom} from 'rxjs';

import {API_CONFIG} from '../../../core/config/api-config';
import {LoginGuard} from './login.guard';

describe('LoginGuard', () => {
  const setupStatusUrl = `${API_CONFIG.BASE_URL}/api/v1/setup/status`;
  const router = {
    navigate: vi.fn(() => Promise.resolve(true)),
  };

  let guard: LoginGuard;
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
        LoginGuard,
      ]
    });

    guard = TestBed.inject(LoginGuard);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    TestBed.resetTestingModule();
  });

  it('allows access when setup has already completed', async () => {
    const resultPromise = firstValueFrom(guard.canActivate());

    const request = httpTestingController.expectOne(setupStatusUrl);
    request.flush({data: true});

    await expect(resultPromise).resolves.toBe(true);
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('redirects to setup when the setup status request fails', async () => {
    const resultPromise = firstValueFrom(guard.canActivate());

    const request = httpTestingController.expectOne(setupStatusUrl);
    request.flush('boom', {status: 500, statusText: 'Server Error'});

    await expect(resultPromise).resolves.toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/setup']);
  });
});
