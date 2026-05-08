import {Injector, runInInjectionContext} from '@angular/core';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {of, throwError} from 'rxjs';
import {QueryClient} from '@tanstack/angular-query-experimental';

import {AuthInitializationService} from './auth-initialization-service';
import {AuthService} from '../../shared/service/auth.service';
import {AppSettingsService, PublicAppSettings} from '../../shared/service/app-settings.service';
import {initializeAuthFactory} from './auth-initializer';

describe('initializeAuthFactory', () => {
  const authInitService = {
    markAsInitialized: vi.fn(),
  };

  const authService = {
    getInternalAccessToken: vi.fn<() => string | null>(),
    remoteLogin: vi.fn(),
    initializeWebSocketConnection: vi.fn(),
  };

  const queryClient = {
    fetchQuery: vi.fn(),
  };

  const appSettingsService = {
    getPublicSettingsQueryOptions: vi.fn(() => ({queryKey: ['public-settings']})),
  };

  const settingsBase: PublicAppSettings = {
    oidcEnabled: true,
    remoteAuthEnabled: false,
    oidcProviderDetails: null!,
    oidcForceOnlyMode: false,
  };

  beforeEach(() => {
    vi.restoreAllMocks();
    authInitService.markAsInitialized.mockReset();
    authService.getInternalAccessToken.mockReset();
    authService.remoteLogin.mockReset();
    authService.initializeWebSocketConnection.mockReset();
    queryClient.fetchQuery.mockReset();
    appSettingsService.getPublicSettingsQueryOptions.mockClear();
  });

  it('marks auth initialized when public settings are unavailable', async () => {
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    queryClient.fetchQuery.mockResolvedValue(null);

    const injector = Injector.create({
      providers: [
        {provide: AuthInitializationService, useValue: authInitService},
        {provide: AuthService, useValue: authService},
        {provide: AppSettingsService, useValue: appSettingsService},
        {provide: QueryClient, useValue: queryClient},
      ]
    });

    await runInInjectionContext(injector, () => initializeAuthFactory()());

    expect(appSettingsService.getPublicSettingsQueryOptions).toHaveBeenCalledOnce();
    expect(authInitService.markAsInitialized).toHaveBeenCalledOnce();
    expect(warnSpy).toHaveBeenCalledOnce();
  });

  it('initializes websocket auth when local auth is active and a token exists', async () => {
    queryClient.fetchQuery.mockResolvedValue({...settingsBase, remoteAuthEnabled: false});
    authService.getInternalAccessToken.mockReturnValue('access-token');

    const injector = Injector.create({
      providers: [
        {provide: AuthInitializationService, useValue: authInitService},
        {provide: AuthService, useValue: authService},
        {provide: AppSettingsService, useValue: appSettingsService},
        {provide: QueryClient, useValue: queryClient},
      ]
    });

    await runInInjectionContext(injector, () => initializeAuthFactory()());

    expect(authService.initializeWebSocketConnection).toHaveBeenCalledOnce();
    expect(authInitService.markAsInitialized).toHaveBeenCalledOnce();
  });

  it('skips websocket initialization when local auth is active and no token exists', async () => {
    queryClient.fetchQuery.mockResolvedValue({...settingsBase, remoteAuthEnabled: false});
    authService.getInternalAccessToken.mockReturnValue(null);

    const injector = Injector.create({
      providers: [
        {provide: AuthInitializationService, useValue: authInitService},
        {provide: AuthService, useValue: authService},
        {provide: AppSettingsService, useValue: appSettingsService},
        {provide: QueryClient, useValue: queryClient},
      ]
    });

    await runInInjectionContext(injector, () => initializeAuthFactory()());

    expect(authService.initializeWebSocketConnection).not.toHaveBeenCalled();
    expect(authInitService.markAsInitialized).toHaveBeenCalledOnce();
  });

  it('performs remote login when remote auth is enabled', async () => {
    queryClient.fetchQuery.mockResolvedValue({...settingsBase, remoteAuthEnabled: true});
    authService.remoteLogin.mockReturnValue(of({
      accessToken: 'access',
      refreshToken: 'refresh',
      isDefaultPassword: false,
    }));

    const injector = Injector.create({
      providers: [
        {provide: AuthInitializationService, useValue: authInitService},
        {provide: AuthService, useValue: authService},
        {provide: AppSettingsService, useValue: appSettingsService},
        {provide: QueryClient, useValue: queryClient},
      ]
    });

    await runInInjectionContext(injector, () => initializeAuthFactory()());

    expect(authService.remoteLogin).toHaveBeenCalledOnce();
    expect(authInitService.markAsInitialized).toHaveBeenCalledOnce();
  });

  it('marks auth initialized even when remote login fails', async () => {
    const error = new Error('remote login failed');
    queryClient.fetchQuery.mockResolvedValue({...settingsBase, remoteAuthEnabled: true});
    authService.remoteLogin.mockReturnValue(throwError(() => error));
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => undefined);

    const injector = Injector.create({
      providers: [
        {provide: AuthInitializationService, useValue: authInitService},
        {provide: AuthService, useValue: authService},
        {provide: AppSettingsService, useValue: appSettingsService},
        {provide: QueryClient, useValue: queryClient},
      ]
    });

    await runInInjectionContext(injector, () => initializeAuthFactory()());

    expect(authService.remoteLogin).toHaveBeenCalledOnce();
    expect(authInitService.markAsInitialized).toHaveBeenCalledOnce();
    expect(errorSpy).toHaveBeenCalledWith('[Remote Login] failed:', error);
  });
});
