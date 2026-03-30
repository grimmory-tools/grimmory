import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideHttpClient} from '@angular/common/http';
import {TestBed} from '@angular/core/testing';
import {Router, UrlTree} from '@angular/router';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {firstValueFrom} from 'rxjs';

import {API_CONFIG} from '../../../core/config/api-config';
import {LoginGuard} from './login.guard';
import {SetupGuard} from './setup.guard';
import {SetupRedirectGuard} from './setup-redirect.guard';

describe('setup and login guards', () => {
  const setupStatusUrl = `${API_CONFIG.BASE_URL}/api/v1/setup/status`;
  const router = {
    createUrlTree: vi.fn((commands: string[]) => ({commands}) as unknown as UrlTree),
    navigate: vi.fn(() => Promise.resolve(true)),
  };

  let httpTestingController: HttpTestingController;
  let setupGuard: SetupGuard;
  let loginGuard: LoginGuard;
  let setupRedirectGuard: SetupRedirectGuard;

  beforeEach(() => {
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
    router.createUrlTree.mockClear();
    router.navigate.mockClear();

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {provide: Router, useValue: router},
        SetupGuard,
        LoginGuard,
        SetupRedirectGuard,
      ]
    });

    httpTestingController = TestBed.inject(HttpTestingController);
    setupGuard = TestBed.inject(SetupGuard);
    loginGuard = TestBed.inject(LoginGuard);
    setupRedirectGuard = TestBed.inject(SetupRedirectGuard);
  });

  afterEach(() => {
    httpTestingController.verify();
    TestBed.resetTestingModule();
  });

  it('allows setup when the instance is not configured yet', async () => {
    const resultPromise = firstValueFrom(setupGuard.canActivate());
    const request = httpTestingController.expectOne(setupStatusUrl);
    request.flush({data: false});

    await expect(resultPromise).resolves.toBe(true);
  });

  it('redirects setup to login when setup is already complete', async () => {
    const resultPromise = firstValueFrom(setupGuard.canActivate());
    const request = httpTestingController.expectOne(setupStatusUrl);
    request.flush({data: true});

    await expect(resultPromise).resolves.toEqual({commands: ['/login']});
    expect(router.createUrlTree).toHaveBeenCalledWith(['/login']);
  });

  it('allows login only after setup is complete', async () => {
    const resultPromise = firstValueFrom(loginGuard.canActivate());
    const request = httpTestingController.expectOne(setupStatusUrl);
    request.flush({data: true});

    await expect(resultPromise).resolves.toBe(true);
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('redirects login to setup when setup is incomplete', async () => {
    const resultPromise = firstValueFrom(loginGuard.canActivate());
    const request = httpTestingController.expectOne(setupStatusUrl);
    request.flush({data: false});

    await expect(resultPromise).resolves.toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/setup']);
  });

  it('redirects login to setup when the setup status request fails', async () => {
    const resultPromise = firstValueFrom(loginGuard.canActivate());
    const request = httpTestingController.expectOne(setupStatusUrl);
    request.flush('boom', {status: 500, statusText: 'Server Error'});

    await expect(resultPromise).resolves.toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/setup']);
  });

  it('sends users from the root route to setup when configuration is missing', async () => {
    const resultPromise = firstValueFrom(setupRedirectGuard.canActivate());
    const request = httpTestingController.expectOne(setupStatusUrl);
    request.flush({data: false});

    await expect(resultPromise).resolves.toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/setup']);
  });

  it('sends users from the root route to the dashboard when setup is complete', async () => {
    const resultPromise = firstValueFrom(setupRedirectGuard.canActivate());
    const request = httpTestingController.expectOne(setupStatusUrl);
    request.flush({data: true});

    await expect(resultPromise).resolves.toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
  });
});
