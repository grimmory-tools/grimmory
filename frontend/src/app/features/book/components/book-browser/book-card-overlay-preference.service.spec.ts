import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {UserService} from '../../../settings/user-management/user.service';
import {BookCardOverlayPreferenceService} from './book-card-overlay-preference.service';

describe('BookCardOverlayPreferenceService', () => {
  let service: BookCardOverlayPreferenceService;
  let userService: {
    currentUser: unknown;
    getCurrentUser: ReturnType<typeof vi.fn>;
    updateUserSetting: ReturnType<typeof vi.fn>;
  };
  let user: {
    id: number;
    userSettings: {
      entityViewPreferences: {
        global: {
          overlayBookType?: boolean;
          showBookTypePill?: boolean;
        };
        overrides: unknown[];
      };
    };
  };

  beforeEach(() => {
    vi.useFakeTimers();

    user = {
      id: 7,
      userSettings: {
        entityViewPreferences: {
          global: {
            overlayBookType: false,
          },
          overrides: [],
        },
      },
    };
    userService = {
      currentUser: signal(user),
      getCurrentUser: vi.fn(() => user),
      updateUserSetting: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        BookCardOverlayPreferenceService,
        {provide: UserService, useValue: userService},
      ],
    });

    service = TestBed.inject(BookCardOverlayPreferenceService);
  });

  afterEach(() => {
    vi.useRealTimers();
    TestBed.resetTestingModule();
  });

  it('updates the exposed signal immediately when the overlay preference changes', () => {
    service.setShowBookTypePill(false);

    expect(service.showBookTypePill()).toBe(false);
  });

  it('persists the updated global overlay preference after the debounce delay', () => {
    service.setShowBookTypePill(true);
    vi.advanceTimersByTime(500);

    expect(userService.updateUserSetting).toHaveBeenCalledWith(
      7,
      'entityViewPreferences',
      expect.objectContaining({
        global: expect.objectContaining({overlayBookType: true}),
      })
    );
  });
});
