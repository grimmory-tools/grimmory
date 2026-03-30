import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {MagicShelfService, type MagicShelf} from '../../magic-shelf/service/magic-shelf.service';
import {UserService} from '../../settings/user-management/user.service';
import {DEFAULT_DASHBOARD_CONFIG, type DashboardConfig, ScrollerType} from '../models/dashboard-config.model';
import {DashboardConfigService} from './dashboard-config.service';

describe('DashboardConfigService', () => {
  const updateUserSetting = vi.fn();
  interface TestUser {
    id: number;
    userSettings?: {
      dashboardConfig?: DashboardConfig;
    };
  }
  const userState = signal<TestUser | null>(null);
  const shelvesState = signal<MagicShelf[]>([]);

  const userService = {
    currentUser: userState.asReadonly(),
    getCurrentUser: vi.fn(() => userState()),
    updateUserSetting,
  };

  const magicShelfService = {
    shelves: shelvesState.asReadonly(),
  };

  beforeEach(() => {
    vi.restoreAllMocks();
    updateUserSetting.mockReset();
    userService.getCurrentUser.mockReset();
    userService.getCurrentUser.mockImplementation(() => userState());
    userState.set(null);
    shelvesState.set([]);

    TestBed.configureTestingModule({
      providers: [
        DashboardConfigService,
        {provide: UserService, useValue: userService},
        {provide: MagicShelfService, useValue: magicShelfService},
      ],
    });
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('normalizes missing dashboard config to a cloned default value', () => {
    userState.set({
      id: 11,
      userSettings: {dashboardConfig: {scrollers: []}},
    });

    const service = TestBed.inject(DashboardConfigService);

    expect(service.config()).toEqual(DEFAULT_DASHBOARD_CONFIG);
    expect(service.config()).not.toBe(DEFAULT_DASHBOARD_CONFIG);
  });

  it('syncs renamed magic shelf titles into the config and persists the change', async () => {
    userState.set({
      id: 12,
      userSettings: {
        dashboardConfig: {
          scrollers: [
            {
              id: '1',
              type: ScrollerType.MAGIC_SHELF,
              title: 'Old shelf title',
              enabled: true,
              order: 1,
              maxItems: 20,
              magicShelfId: 55,
            },
          ],
        },
      },
    });

    const service = TestBed.inject(DashboardConfigService);
    await new Promise(resolve => setTimeout(resolve, 0));
    await new Promise(resolve => setTimeout(resolve, 0));
    shelvesState.set([{id: 55, name: 'New shelf title', filterJson: '{}'} as MagicShelf]);
    await new Promise(resolve => setTimeout(resolve, 0));
    await new Promise(resolve => setTimeout(resolve, 0));

    expect(service.config().scrollers[0].title).toBe('New shelf title');
    expect(updateUserSetting).toHaveBeenCalledWith(12, 'dashboardConfig', {
      scrollers: [
        {
          id: '1',
          type: ScrollerType.MAGIC_SHELF,
          title: 'New shelf title',
          enabled: true,
          order: 1,
          maxItems: 20,
          magicShelfId: 55,
        },
      ],
    });
  });

  it('saves normalized configs and skips persistence when no user is available', () => {
    const service = TestBed.inject(DashboardConfigService);

    service.saveConfig({
      scrollers: [
        {
          id: '9',
          type: ScrollerType.LATEST_ADDED,
          title: 'recent',
          enabled: true,
          order: 1,
          maxItems: 15,
        },
      ],
    });

    expect(service.config().scrollers[0].id).toBe('9');
    expect(updateUserSetting).not.toHaveBeenCalled();

    userState.set({
      id: 21,
      userSettings: {dashboardConfig: DEFAULT_DASHBOARD_CONFIG},
    });

    service.resetToDefault();

    expect(updateUserSetting).toHaveBeenCalledWith(21, 'dashboardConfig', DEFAULT_DASHBOARD_CONFIG);
  });
});
