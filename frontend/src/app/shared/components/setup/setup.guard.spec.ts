import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {Router, UrlTree} from '@angular/router';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {firstValueFrom} from 'rxjs';

import {API_CONFIG} from '../../../core/config/api-config';
import {SetupGuard} from './setup.guard';

describe('SetupGuard', () => {
  const setupStatusUrl = `${API_CONFIG.BASE_URL}/api/v1/setup/status`;
  const router = {
    createUrlTree: vi.fn((commands: string[]) => ({commands}) as unknown as UrlTree),
  };

  let guard: SetupGuard;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
    router.createUrlTree.mockClear();

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {provide: Router, useValue: router},
        SetupGuard,
      ]
    });

    guard = TestBed.inject(SetupGuard);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    TestBed.resetTestingModule();
  });

  it('allows access when setup is still incomplete', async () => {
    const resultPromise = firstValueFrom(guard.canActivate());

    const request = httpTestingController.expectOne(setupStatusUrl);
    request.flush({data: false});

    await expect(resultPromise).resolves.toBe(true);
    expect(router.createUrlTree).not.toHaveBeenCalled();
  });

  it('returns a login redirect tree once setup is complete', async () => {
    const resultPromise = firstValueFrom(guard.canActivate());

    const request = httpTestingController.expectOne(setupStatusUrl);
    request.flush({data: true});

    await expect(resultPromise).resolves.toEqual({commands: ['/login']});
    expect(router.createUrlTree).toHaveBeenCalledWith(['/login']);
  });
});
