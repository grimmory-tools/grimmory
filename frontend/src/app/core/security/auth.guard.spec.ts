import {TestBed} from '@angular/core/testing';
import {ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree} from '@angular/router';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {firstValueFrom, Observable, of} from 'rxjs';

import {AuthService} from '../../shared/service/auth.service';
import {AuthChildGuard, AuthGuard} from './auth.guard';

describe('AuthGuard', () => {
  const route = {} as ActivatedRouteSnapshot;
  const state = {} as RouterStateSnapshot;
  const router = {
    createUrlTree: vi.fn((commands: string[]) => ({commands}) as unknown as UrlTree),
    navigate: vi.fn(() => Promise.resolve(true)),
  };

  const authService = {
    ensureAuthenticated: vi.fn(),
    getIsDefaultPassword: vi.fn<() => boolean>(),
  };

  beforeEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
    router.createUrlTree.mockClear();
    router.navigate.mockClear();
    authService.ensureAuthenticated.mockReset();
    authService.getIsDefaultPassword.mockReset();

    TestBed.configureTestingModule({
      providers: [
        {provide: Router, useValue: router},
        {provide: AuthService, useValue: authService},
      ]
    });
  });

  afterEach(() => {
    localStorage.clear();
  });

  function runGuard(): Observable<unknown> {
    return TestBed.runInInjectionContext(() => AuthGuard(route, state)) as Observable<unknown>;
  }

  it('allows navigation for an authenticated non-default-password session', async () => {
    authService.ensureAuthenticated.mockReturnValue(of(true));
    authService.getIsDefaultPassword.mockReturnValue(false);

    const result = await firstValueFrom(runGuard());

    expect(result).toBe(true);
    expect(authService.ensureAuthenticated).toHaveBeenCalledOnce();
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('redirects to login when the session cannot be authenticated', async () => {
    authService.ensureAuthenticated.mockReturnValue(of(false));

    const result = await firstValueFrom(runGuard());

    expect(router.createUrlTree).toHaveBeenCalledWith(['/login']);
    expect(result).toEqual({commands: ['/login']});
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('allows navigation when AuthService refreshes an expired token', async () => {
    authService.ensureAuthenticated.mockReturnValue(of(true));
    authService.getIsDefaultPassword.mockReturnValue(false);

    const result = await firstValueFrom(runGuard());

    expect(result).toBe(true);
    expect(router.createUrlTree).not.toHaveBeenCalledWith(['/login']);
  });

  it('redirects to the change-password flow for default-password sessions', async () => {
    authService.ensureAuthenticated.mockReturnValue(of(true));
    authService.getIsDefaultPassword.mockReturnValue(true);

    const result = await firstValueFrom(runGuard());

    expect(router.createUrlTree).toHaveBeenCalledWith(['/change-password']);
    expect(result).toEqual({commands: ['/change-password']});
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('uses the same authentication flow for child routes', async () => {
    authService.ensureAuthenticated.mockReturnValue(of(true));
    authService.getIsDefaultPassword.mockReturnValue(false);

    const result = await firstValueFrom(
      TestBed.runInInjectionContext(() => AuthChildGuard(route, state)) as Observable<unknown>
    );

    expect(result).toBe(true);
    expect(authService.ensureAuthenticated).toHaveBeenCalledOnce();
  });
});
