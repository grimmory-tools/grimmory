import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {QueryClient, queryOptions} from '@tanstack/angular-query-experimental';
import {of} from 'rxjs';

import {AuthService} from './auth.service';
import {StartupService} from './startup.service';
import {UserService} from '../../features/settings/user-management/user.service';
import {AppThemeService} from './app-theme.service';
import {AppLocaleService} from './app-locale.service';
import {AppUiFontService} from './app-ui-font.service';

describe('StartupService', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
    TestBed.resetTestingModule();
  });

  it('loads the current user when a token is present', async () => {
    const fetchQuery = vi.fn().mockResolvedValue({id: 1, username: 'admin', locale: 'en', theme: 'grimmory', themeAccent: null, themeSyncEnabled: true});
    const applySyncedTheme = vi.fn();
    const applyDeviceTheme = vi.fn();
    const useStoredDeviceTheme = vi.fn();
    const applyLocale = vi.fn().mockResolvedValue(undefined);
    const getDisplayLocale = vi.fn().mockReturnValue('en');

    TestBed.configureTestingModule({
      providers: [
        StartupService,
        {provide: AuthService, useValue: {token: signal('token')}},
        {
          provide: UserService,
          useValue: {
            getUserQueryOptions: () => queryOptions({
              queryKey: ['user'],
              queryFn: async () => ({username: 'admin'}),
            }),
          },
        },
        {provide: QueryClient, useValue: {fetchQuery}},
        {provide: AppThemeService, useValue: {applySyncedTheme, applyDeviceTheme, useStoredDeviceTheme, appState: () => ({themeSyncEnabled: true})}},
        {provide: AppLocaleService, useValue: {applyLocale, getDisplayLocale}},
      ]
    });

    const service = TestBed.inject(StartupService);

    await expect(service.load()).resolves.toBeUndefined();
    expect(fetchQuery).toHaveBeenCalledOnce();
    expect(applySyncedTheme).toHaveBeenCalledWith('grimmory', 'orange');
    expect(applyDeviceTheme).not.toHaveBeenCalled();
    expect(applyLocale).toHaveBeenCalledWith('en');
  });

  it('loads the local display locale when there is no token', async () => {
    const fetchQuery = vi.fn();
    const applySyncedTheme = vi.fn();
    const applyLocale = vi.fn().mockResolvedValue(undefined);
    const getDisplayLocale = vi.fn().mockReturnValue('de');
    const applyUiFont = vi.fn();

    TestBed.configureTestingModule({
      providers: [
        StartupService,
        {provide: AuthService, useValue: {token: signal(null)}},
        {
          provide: UserService,
          useValue: {
            getUserQueryOptions: () => queryOptions({
              queryKey: ['user'],
              queryFn: async () => ({username: 'admin'}),
            }),
          },
        },
        {provide: QueryClient, useValue: {fetchQuery}},
        {provide: AppThemeService, useValue: {applySyncedTheme, appState: () => ({themeSyncEnabled: true})}},
        {provide: AppLocaleService, useValue: {applyLocale, getDisplayLocale}},
        {provide: AppUiFontService, useValue: {applyUiFont}},
      ]
    });

    const service = TestBed.inject(StartupService);

    await expect(service.load()).resolves.toBeUndefined();
    expect(fetchQuery).not.toHaveBeenCalled();
    expect(applyUiFont).not.toHaveBeenCalled();
    expect(getDisplayLocale).toHaveBeenCalledOnce();
    expect(applyLocale).toHaveBeenCalledWith('de');
  });

  it('uses the backend locale for authenticated users', async () => {
    const fetchQuery = vi.fn().mockResolvedValue({id: 1, username: 'admin', locale: 'en', theme: 'custom', themeAccent: 'teal', themeSyncEnabled: true, uiFont: 'atkinson'});
    const updateUserProfile = vi.fn().mockReturnValue(of({id: 1, username: 'admin', locale: 'de', theme: 'custom', themeAccent: 'teal', themeSyncEnabled: true}));
    const applySyncedTheme = vi.fn();
    const applyLocale = vi.fn().mockResolvedValue(undefined);
    const getDisplayLocale = vi.fn().mockReturnValue('de');
    const applyUiFont = vi.fn();

    TestBed.configureTestingModule({
      providers: [
        StartupService,
        {provide: AuthService, useValue: {token: signal('token')}},
        {
          provide: UserService,
          useValue: {
            getUserQueryOptions: () => queryOptions({
              queryKey: ['user'],
              queryFn: async () => ({id: 1, username: 'admin', locale: 'en', theme: 'custom', themeAccent: 'teal', themeSyncEnabled: true, uiFont: 'atkinson'}),
            }),
            updateUserProfile,
          },
        },
        {provide: QueryClient, useValue: {fetchQuery}},
        {provide: AppThemeService, useValue: {applySyncedTheme, appState: () => ({themeSyncEnabled: true})}},
        {provide: AppLocaleService, useValue: {applyLocale, getDisplayLocale}},
        {provide: AppUiFontService, useValue: {applyUiFont}},
      ]
    });

    const service = TestBed.inject(StartupService);

    await service.load();

    expect(updateUserProfile).not.toHaveBeenCalled();
    expect(applySyncedTheme).toHaveBeenCalledWith('custom', 'teal');
    expect(applyUiFont).toHaveBeenCalledWith('atkinson');
    expect(applyLocale).toHaveBeenCalledWith('en');
  });

  it('uses a local device theme when theme sync is disabled', async () => {
    const fetchQuery = vi.fn().mockResolvedValue({id: 1, username: 'admin', locale: 'en', theme: 'cobalt', themeAccent: null, themeSyncEnabled: false});
    const applySyncedTheme = vi.fn();
    const applyDeviceTheme = vi.fn();
    const useStoredDeviceTheme = vi.fn();
    const applyLocale = vi.fn().mockResolvedValue(undefined);
    const getDisplayLocale = vi.fn().mockReturnValue('en');

    TestBed.configureTestingModule({
      providers: [
        StartupService,
        {provide: AuthService, useValue: {token: signal('token')}},
        {
          provide: UserService,
          useValue: {
            getUserQueryOptions: () => queryOptions({
              queryKey: ['user'],
              queryFn: async () => ({id: 1, username: 'admin', locale: 'en', theme: 'cobalt', themeAccent: null, themeSyncEnabled: false}),
            }),
          },
        },
        {provide: QueryClient, useValue: {fetchQuery}},
        {provide: AppThemeService, useValue: {applySyncedTheme, applyDeviceTheme, useStoredDeviceTheme, appState: () => ({themeSyncEnabled: false})}},
        {provide: AppLocaleService, useValue: {applyLocale, getDisplayLocale}},
      ]
    });

    const service = TestBed.inject(StartupService);

    await service.load();

    expect(applySyncedTheme).not.toHaveBeenCalled();
    expect(applyDeviceTheme).not.toHaveBeenCalled();
    expect(useStoredDeviceTheme).toHaveBeenCalledOnce();
  });

  it('seeds the local device theme from the backend when sync is disabled without a local override', async () => {
    const fetchQuery = vi.fn().mockResolvedValue({id: 1, username: 'admin', locale: 'en', theme: 'cobalt', themeAccent: null, themeSyncEnabled: false});
    const applyDeviceTheme = vi.fn();
    const applyLocale = vi.fn().mockResolvedValue(undefined);
    const getDisplayLocale = vi.fn().mockReturnValue('en');

    TestBed.configureTestingModule({
      providers: [
        StartupService,
        {provide: AuthService, useValue: {token: signal('token')}},
        {
          provide: UserService,
          useValue: {
            getUserQueryOptions: () => queryOptions({
              queryKey: ['user'],
              queryFn: async () => ({id: 1, username: 'admin', locale: 'en', theme: 'cobalt', themeAccent: null, themeSyncEnabled: false}),
            }),
          },
        },
        {provide: QueryClient, useValue: {fetchQuery}},
        {provide: AppThemeService, useValue: {applyDeviceTheme, appState: () => ({themeSyncEnabled: true})}},
        {provide: AppLocaleService, useValue: {applyLocale, getDisplayLocale}},
      ]
    });

    const service = TestBed.inject(StartupService);

    await service.load();

    expect(applyDeviceTheme).toHaveBeenCalledWith('cobalt', 'orange');
  });
});
