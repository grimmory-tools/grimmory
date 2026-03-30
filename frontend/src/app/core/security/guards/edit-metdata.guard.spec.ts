import {TestBed} from '@angular/core/testing';
import {ActivatedRouteSnapshot, Router, RouterStateSnapshot} from '@angular/router';
import {beforeEach, describe, expect, it, vi} from 'vitest';

import {UserService} from '../../../features/settings/user-management/user.service';
import {EditMetadataGuard} from './edit-metdata.guard';

describe('EditMetadataGuard', () => {
  const route = {} as ActivatedRouteSnapshot;
  const state = {} as RouterStateSnapshot;
  const router = {
    navigate: vi.fn(() => Promise.resolve(true)),
  };
  const userService = {
    currentUser: vi.fn<() => {permissions: {admin: boolean; canEditMetadata: boolean}} | null>(),
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

  it('allows users with metadata-edit permissions', () => {
    userService.currentUser.mockReturnValue({
      permissions: {
        admin: false,
        canEditMetadata: true,
      }
    });

    const result = TestBed.runInInjectionContext(() => EditMetadataGuard(route, state));

    expect(result).toBe(true);
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('redirects users who lack metadata-edit permissions', () => {
    userService.currentUser.mockReturnValue({
      permissions: {
        admin: false,
        canEditMetadata: false,
      }
    });

    const result = TestBed.runInInjectionContext(() => EditMetadataGuard(route, state));

    expect(result).toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
  });
});
