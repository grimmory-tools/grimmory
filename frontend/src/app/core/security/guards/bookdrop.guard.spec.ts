import {TestBed} from '@angular/core/testing';
import {ActivatedRouteSnapshot, Router, RouterStateSnapshot} from '@angular/router';
import {beforeEach, describe, expect, it, vi} from 'vitest';

import {UserService} from '../../../features/settings/user-management/user.service';
import {BookdropGuard} from './bookdrop.guard';

describe('BookdropGuard', () => {
  const route = {} as ActivatedRouteSnapshot;
  const state = {} as RouterStateSnapshot;
  const router = {
    navigate: vi.fn(() => Promise.resolve(true)),
  };
  const userService = {
    currentUser: vi.fn<() => {permissions: {admin: boolean; canAccessBookdrop: boolean}} | null>(),
  };

  beforeEach(() => {
    vi.restoreAllMocks();
    router.navigate.mockClear();
    userService.currentUser.mockReset();

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        {provide: Router, useValue: router},
        {provide: UserService, useValue: userService},
      ]
    });
  });

  it('allows admins even without the explicit bookdrop permission', () => {
    userService.currentUser.mockReturnValue({
      permissions: {
        admin: true,
        canAccessBookdrop: false,
      }
    });

    const result = TestBed.runInInjectionContext(() => BookdropGuard(route, state));

    expect(result).toBe(true);
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('redirects anonymous users to the dashboard', () => {
    userService.currentUser.mockReturnValue(null);

    const result = TestBed.runInInjectionContext(() => BookdropGuard(route, state));

    expect(result).toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
  });
});
