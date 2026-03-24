import {beforeEach, describe, expect, it, vi} from 'vitest';
import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {initializeAuthFactory} from './auth-initializer';
import {AuthInitializationService} from './auth-initialization-service';
import {AuthService} from '../../shared/service/auth.service';
import {AppSettingsService, PublicAppSettings} from '../../shared/service/app-settings.service';
import {QueryClient, queryOptions} from '@tanstack/angular-query-experimental';

describe('initializeAuthFactory', () => {
  let authInitService: AuthInitializationService;
  let mockQueryClient: { fetchQuery: ReturnType<typeof vi.fn> };

  const defaultPublicSettings: PublicAppSettings = {
    oidcEnabled: false,
    remoteAuthEnabled: false,
    oidcProviderDetails: null!,
    oidcForceOnlyMode: false,
  };

  beforeEach(() => {
    mockQueryClient = {
      fetchQuery: vi.fn().mockResolvedValue(defaultPublicSettings),
    };

    TestBed.configureTestingModule({
      providers: [
        {provide: AuthService, useValue: {token: signal(null), getInternalAccessToken: vi.fn()}},
        {
          provide: AppSettingsService,
          useValue: {
            publicAppSettings: signal(null),
            getPublicSettingsQueryOptions: () => queryOptions({queryKey: ['public-settings'], queryFn: async () => defaultPublicSettings}),
          },
        },
        {provide: QueryClient, useValue: mockQueryClient},
        AuthInitializationService,
      ]
    });

    authInitService = TestBed.inject(AuthInitializationService);
  });

  it('should proceed with auth initialization when navigator.onLine is false', async () => {
    const markSpy = vi.spyOn(authInitService, 'markAsInitialized');

    Object.defineProperty(navigator, 'onLine', {value: false, configurable: true});

    const factory = TestBed.runInInjectionContext(() => initializeAuthFactory());
    const initPromise = TestBed.runInInjectionContext(() => factory());

    await initPromise;

    expect(markSpy).toHaveBeenCalled();

    Object.defineProperty(navigator, 'onLine', {value: true, configurable: true});
  });

  it('should initialize normally when navigator.onLine is true', async () => {
    const markSpy = vi.spyOn(authInitService, 'markAsInitialized');

    Object.defineProperty(navigator, 'onLine', {value: true, configurable: true});

    const factory = TestBed.runInInjectionContext(() => initializeAuthFactory());
    const initPromise = TestBed.runInInjectionContext(() => factory());

    await initPromise;

    expect(markSpy).toHaveBeenCalled();
  });
});
