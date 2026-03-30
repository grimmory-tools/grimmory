import {TestBed} from '@angular/core/testing';
import {ActivatedRouteSnapshot, Router, RouterStateSnapshot} from '@angular/router';
import {beforeEach, describe, expect, it, vi} from 'vitest';

import {UserService} from '../../../features/settings/user-management/user.service';
import {ManageLibraryGuard} from './manage-library.guard';

describe('ManageLibraryGuard', () => {
  const route = {} as ActivatedRouteSnapshot;
  const state = {} as RouterStateSnapshot;
  const router = {
    navigate: vi.fn(() => Promise.resolve(true)),
  };
  const userService = {
    currentUser: vi.fn<() => {permissions: {admin: boolean; canManageLibrary: boolean}} | null>(),
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

  it('allows users with the manage-library permission', () => {
    userService.currentUser.mockReturnValue({
      permissions: {
        admin: false,
        canManageLibrary: true,
      }
    });

    const result = TestBed.runInInjectionContext(() => ManageLibraryGuard(route, state));

    expect(result).toBe(true);
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('redirects users who lack access', () => {
    userService.currentUser.mockReturnValue({
      permissions: {
        admin: false,
        canManageLibrary: false,
      }
    });

    const result = TestBed.runInInjectionContext(() => ManageLibraryGuard(route, state));

    expect(result).toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
  });
});
