import {TestBed} from '@angular/core/testing';
import {ActivatedRouteSnapshot, Router, RouterStateSnapshot} from '@angular/router';
import {beforeEach, describe, expect, it, vi} from 'vitest';

import {UserService} from '../../features/settings/user-management/user.service';
import {BookdropGuard} from './guards/bookdrop.guard';
import {EditMetadataGuard} from './guards/edit-metdata.guard';
import {LibraryStatsGuard} from './guards/library-stats.guard';
import {ManageLibraryGuard} from './guards/manage-library.guard';
import {UserStatsGuard} from './guards/user-stats.guard';

type PermissionFlag =
  | 'canAccessBookdrop'
  | 'canEditMetadata'
  | 'canAccessLibraryStats'
  | 'canManageLibrary'
  | 'canAccessUserStats';

function createUser(permissionFlag: PermissionFlag, value: boolean, admin = false) {
  return {
    permissions: {
      admin,
      canAccessBookdrop: permissionFlag === 'canAccessBookdrop' ? value : false,
      canEditMetadata: permissionFlag === 'canEditMetadata' ? value : false,
      canAccessLibraryStats: permissionFlag === 'canAccessLibraryStats' ? value : false,
      canManageLibrary: permissionFlag === 'canManageLibrary' ? value : false,
      canAccessUserStats: permissionFlag === 'canAccessUserStats' ? value : false,
    }
  };
}

describe('security guards', () => {
  const route = {} as ActivatedRouteSnapshot;
  const state = {} as RouterStateSnapshot;
  const router = {
    navigate: vi.fn(() => Promise.resolve(true)),
  };
  const userService = {
    currentUser: vi.fn(),
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

  function expectGuardAllows(
    permissionFlag: PermissionFlag,
    guard: (route: ActivatedRouteSnapshot, state: RouterStateSnapshot) => unknown
  ): void {
    userService.currentUser.mockReturnValue(createUser(permissionFlag, true));

    const result = TestBed.runInInjectionContext(() => guard(route, state));

    expect(result).toBe(true);
    expect(router.navigate).not.toHaveBeenCalled();
  }

  function expectGuardAllowsAdmin(
    permissionFlag: PermissionFlag,
    guard: (route: ActivatedRouteSnapshot, state: RouterStateSnapshot) => unknown
  ): void {
    userService.currentUser.mockReturnValue(createUser(permissionFlag, false, true));

    const result = TestBed.runInInjectionContext(() => guard(route, state));

    expect(result).toBe(true);
    expect(router.navigate).not.toHaveBeenCalled();
  }

  function expectGuardRedirects(
    permissionFlag: PermissionFlag,
    guard: (route: ActivatedRouteSnapshot, state: RouterStateSnapshot) => unknown
  ): void {
    userService.currentUser.mockReturnValue(createUser(permissionFlag, false));

    const result = TestBed.runInInjectionContext(() => guard(route, state));

    expect(result).toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
  }

  it('allows bookdrop access for admins and users with the explicit permission', () => {
    expectGuardAllows('canAccessBookdrop', BookdropGuard);
    expectGuardAllowsAdmin('canAccessBookdrop', BookdropGuard);
  });

  it('redirects bookdrop access when the user lacks permission', () => {
    expectGuardRedirects('canAccessBookdrop', BookdropGuard);
  });

  it('allows metadata editing for admins and users with the explicit permission', () => {
    expectGuardAllows('canEditMetadata', EditMetadataGuard);
    expectGuardAllowsAdmin('canEditMetadata', EditMetadataGuard);
  });

  it('redirects metadata editing when the user lacks permission', () => {
    expectGuardRedirects('canEditMetadata', EditMetadataGuard);
  });

  it('allows library stats for admins and users with the explicit permission', () => {
    expectGuardAllows('canAccessLibraryStats', LibraryStatsGuard);
    expectGuardAllowsAdmin('canAccessLibraryStats', LibraryStatsGuard);
  });

  it('redirects library stats when the user lacks permission', () => {
    expectGuardRedirects('canAccessLibraryStats', LibraryStatsGuard);
  });

  it('allows library management for admins and users with the explicit permission', () => {
    expectGuardAllows('canManageLibrary', ManageLibraryGuard);
    expectGuardAllowsAdmin('canManageLibrary', ManageLibraryGuard);
  });

  it('redirects library management when the user lacks permission', () => {
    expectGuardRedirects('canManageLibrary', ManageLibraryGuard);
  });

  it('allows user stats for admins and users with the explicit permission', () => {
    expectGuardAllows('canAccessUserStats', UserStatsGuard);
    expectGuardAllowsAdmin('canAccessUserStats', UserStatsGuard);
  });

  it('redirects user stats when the user lacks permission', () => {
    expectGuardRedirects('canAccessUserStats', UserStatsGuard);
  });
});
