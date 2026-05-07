import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { NavigationEnd, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { LayoutService } from './layout.service';
import { LocalStorageService } from '../service/local-storage.service';
import { UserService } from '../../features/settings/user-management/user.service';

describe('LayoutService', () => {
  let service: LayoutService;
  let routerEvents: Subject<unknown>;
  const currentUser = signal({
    id: 7,
    userSettings: {
      sidebarLibrarySorting: { field: 'id', order: 'DESC' },
      sidebarShelfSorting: { field: 'name', order: 'asc' },
      sidebarMagicShelfSorting: { field: 'id', order: 'asc' },
    },
  } as never);
  const updateUserSetting = vi.fn();
  let resizeListener: (() => void) | undefined;
  let mobileShellActive = false;
  const localStorageService = {
    get: vi.fn((key: string) => {
      if (key === 'sidebarCollapsed') {
        return false;
      }
      if (key === 'sidebarWidth') {
        return 225;
      }
      return null;
    }),
    set: vi.fn(),
  };

  beforeEach(() => {
    routerEvents = new Subject<unknown>();
    resizeListener = undefined;
    mobileShellActive = false;
    vi.spyOn(window, 'addEventListener').mockImplementation((type, listener) => {
      if (type === 'resize') {
        resizeListener = listener as () => void;
      }
    });
    vi.spyOn(window, 'removeEventListener').mockImplementation(vi.fn());
    vi.spyOn(window, 'getComputedStyle').mockImplementation(() => ({
      getPropertyValue: (property: string) =>
        property === '--mobile-shell-active' && mobileShellActive ? '1' : '',
    }) as CSSStyleDeclaration);
    TestBed.configureTestingModule({
      providers: [
        LayoutService,
        { provide: LocalStorageService, useValue: localStorageService },
        { provide: UserService, useValue: { currentUser, updateUserSetting } },
        {
          provide: Router,
          useValue: {
            url: '/dashboard?tab=recent',
            events: routerEvents.asObservable(),
          },
        },
      ],
    });

    service = TestBed.inject(LayoutService);
    TestBed.flushEffects();
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
    localStorageService.get.mockClear();
    localStorageService.set.mockClear();
    updateUserSetting.mockClear();
  });

  it('toggles sidebarVisible on desktop', () => {
    vi.spyOn(service, 'isDesktop').mockReturnValue(true);

    expect(service.sidebarVisible()).toBe(true);
    service.onMenuToggle();
    expect(service.sidebarVisible()).toBe(false);
    service.onMenuToggle();
    expect(service.sidebarVisible()).toBe(true);
  });

  it('toggles mobileDrawerOpen on mobile', () => {
    vi.spyOn(service, 'isDesktop').mockReturnValue(false);

    expect(service.mobileDrawerOpen()).toBe(false);
    service.onMenuToggle();
    expect(service.mobileDrawerOpen()).toBe(true);
    service.onMenuToggle();
    expect(service.mobileDrawerOpen()).toBe(false);
  });

  it('derives desktop state from the mobile shell CSS state', () => {
    expect(service.isDesktop()).toBe(true);

    mobileShellActive = true;
    resizeListener?.();
    expect(service.isDesktop()).toBe(false);

    service.mobileDrawerOpen.set(true);
    mobileShellActive = false;
    resizeListener?.();

    expect(service.isDesktop()).toBe(true);
    expect(service.mobileDrawerOpen()).toBe(false);
  });

  it('closeMobileSidebar resets mobileDrawerOpen to false', () => {
    service.mobileDrawerOpen.set(true);
    service.closeMobileSidebar();
    expect(service.mobileDrawerOpen()).toBe(false);
  });

  it('updates the document root font size when the scale changes', () => {
    service.scale.set(18);
    TestBed.flushEffects();

    expect(document.documentElement.style.fontSize).toBe('18px');
  });

  it('persists the collapsed state when toggled', () => {
    expect(service.sidebarCollapsed()).toBe(false);

    service.toggleSidebarCollapsed();

    expect(service.sidebarCollapsed()).toBe(true);
    expect(localStorageService.set).toHaveBeenCalledWith('sidebarCollapsed', true);
  });

  it('tracks the desktop sidebar collapse transition', () => {
    vi.useFakeTimers();
    try {
      service.isDesktop.set(true);

      service.toggleSidebarCollapsed();

      expect(service.sidebarTransitioning()).toBe(true);

      vi.advanceTimersByTime(220);

      expect(service.sidebarTransitioning()).toBe(false);
    } finally {
      vi.useRealTimers();
    }
  });

  it('hydrates sidebar sort preferences from the current user', () => {
    expect(service.librarySort()).toEqual({ field: 'id', order: 'desc' });
    expect(service.shelfSort()).toEqual({ field: 'name', order: 'asc' });
    expect(service.magicShelfSort()).toEqual({ field: 'id', order: 'asc' });
  });

  it('persists sidebar sort changes through UserService', () => {
    service.setLibrarySort({ field: 'name', order: 'asc' });

    expect(service.librarySort()).toEqual({ field: 'name', order: 'asc' });
    expect(updateUserSetting).toHaveBeenCalledWith(7, 'sidebarLibrarySorting', { field: 'name', order: 'asc' });
  });

  it('seeds currentPath from the router URL without query string', () => {
    expect(service.currentPath()).toBe('/dashboard');
  });

  it('updates currentPath on NavigationEnd', () => {
    routerEvents.next(new NavigationEnd(1, '/library/1/books', '/library/1/books'));

    expect(service.currentPath()).toBe('/library/1/books');
  });

  it('cleans up global listeners and router events when destroyed', () => {
    TestBed.resetTestingModule();
    routerEvents.next(new NavigationEnd(1, '/series', '/series'));

    expect(window.removeEventListener).toHaveBeenCalledWith('resize', expect.any(Function));
    expect(service.currentPath()).toBe('/dashboard');
  });
});
