import {TestBed} from '@angular/core/testing';
import {ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree} from '@angular/router';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {AuthService} from '../../shared/service/auth.service';
import {AuthGuard} from './auth.guard';

describe('AuthGuard', () => {
  const route = {} as ActivatedRouteSnapshot;
  const state = {} as RouterStateSnapshot;
  const router = {
    createUrlTree: vi.fn((commands: string[]) => ({commands}) as unknown as UrlTree),
    navigate: vi.fn(() => Promise.resolve(true)),
  };

  const authService = {
    getInternalAccessToken: vi.fn<() => string | null>(),
    getInternalAccessTokenExpiry: vi.fn<() => number | null>(),
    getIsDefaultPassword: vi.fn<() => boolean>(),
  };

  beforeEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
    router.createUrlTree.mockClear();
    router.navigate.mockClear();
    authService.getInternalAccessToken.mockReset();
    authService.getInternalAccessTokenExpiry.mockReset();
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

  it('allows navigation for a valid non-default-password token', () => {
    authService.getInternalAccessToken.mockReturnValue('bearer token');
    authService.getInternalAccessTokenExpiry.mockReturnValue(Date.now() + 3600000);

    const result = TestBed.runInInjectionContext(() => AuthGuard(route, state));

    expect(result).toBe(true);
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('redirects to login when there is no token', () => {
    authService.getInternalAccessToken.mockReturnValue(null);

    const result = TestBed.runInInjectionContext(() => AuthGuard(route, state));

    expect(result).toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('returns a login UrlTree for expired tokens', () => {
    localStorage.setItem('accessToken_Internal', 'stale-token');
    authService.getInternalAccessToken.mockReturnValue('stale-token');
    authService.getInternalAccessTokenExpiry.mockReturnValue(Date.now() - 10000);

    const result = TestBed.runInInjectionContext(() => AuthGuard(route, state));

    expect(router.createUrlTree).toHaveBeenCalledWith(['/login']);
    expect(result).toEqual({commands: ['/login']});
    expect(localStorage.getItem('accessToken_Internal')).toBeNull();
  });

  it('redirects to the change-password flow for default-password tokens', () => {
    authService.getInternalAccessToken.mockReturnValue('bearer token');
    authService.getInternalAccessTokenExpiry.mockReturnValue(Date.now() + 3600000);
    authService.getIsDefaultPassword.mockReturnValue(true);

    const result = TestBed.runInInjectionContext(() => AuthGuard(route, state));

    expect(result).toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/change-password']);
  });
});
