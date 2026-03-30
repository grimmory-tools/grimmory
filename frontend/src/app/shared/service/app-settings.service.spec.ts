import {HttpTestingController} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {AuthService} from './auth.service';
import {APP_SETTINGS_QUERY_KEY, PUBLIC_SETTINGS_QUERY_KEY} from './app-settings-query-keys';
import {AppSettingsService, type PublicAppSettings} from './app-settings.service';
import type {AppSettings} from '../model/app-settings.model';
import {createAuthServiceStub, createQueryClientHarness, flushSignalAndQueryEffects} from '../../core/testing/query-testing';

function buildPublicSettings(overrides: Partial<PublicAppSettings> = {}): PublicAppSettings {
  return {
    oidcEnabled: true,
    remoteAuthEnabled: true,
    oidcProviderDetails: {
      providerName: 'OIDC',
      clientId: 'client-id',
      issuerUri: 'https://issuer.example',
      claimMapping: {
        username: 'preferred_username',
        email: 'email',
        name: 'name',
        groups: 'groups',
      },
    },
    oidcForceOnlyMode: false,
    ...overrides,
  };
}

function buildAppSettings(overrides: Partial<AppSettings> = {}): AppSettings {
  const publicSettings = buildPublicSettings();

  return {
    autoBookSearch: true,
    similarBookRecommendation: true,
    defaultMetadataRefreshOptions: {} as never,
    libraryMetadataRefreshOptions: [],
    uploadPattern: '{Title}',
    opdsServerEnabled: true,
    komgaApiEnabled: false,
    komgaGroupUnknown: false,
    remoteAuthEnabled: publicSettings.remoteAuthEnabled,
    oidcEnabled: publicSettings.oidcEnabled,
    oidcProviderDetails: publicSettings.oidcProviderDetails,
    oidcAutoProvisionDetails: {
      enableAutoProvisioning: false,
      allowLocalAccountLinking: false,
      defaultPermissions: [],
      defaultLibraryIds: [],
    },
    maxFileUploadSizeInMb: 50,
    metadataProviderSettings: {} as never,
    metadataMatchWeights: {} as never,
    metadataPersistenceSettings: {} as never,
    metadataPublicReviewsSettings: {
      downloadEnabled: false,
      autoDownloadEnabled: false,
      providers: [],
    },
    koboSettings: {} as never,
    coverCroppingSettings: {} as never,
    metadataDownloadOnBookdrop: false,
    metadataProviderSpecificFields: {} as never,
    oidcSessionDurationHours: null,
    oidcGroupSyncMode: null,
    oidcForceOnlyMode: publicSettings.oidcForceOnlyMode,
    diskType: 'LOCAL',
    ...overrides,
  };
}

function flushInitialSettingsRequests(
  httpTestingController: HttpTestingController,
  overrides: {
    publicSettings?: Partial<PublicAppSettings>;
    appSettings?: Partial<AppSettings>;
  } = {},
): {
  publicSettings: PublicAppSettings;
  appSettings: AppSettings;
} {
  const publicSettings = buildPublicSettings(overrides.publicSettings);
  const appSettings = buildAppSettings({
    oidcEnabled: publicSettings.oidcEnabled,
    remoteAuthEnabled: publicSettings.remoteAuthEnabled,
    oidcProviderDetails: publicSettings.oidcProviderDetails,
    oidcForceOnlyMode: publicSettings.oidcForceOnlyMode,
    ...overrides.appSettings,
  });

  const publicRequests = httpTestingController.match(req => req.url.endsWith('/api/v1/public-settings'));
  expect(publicRequests.length).toBeGreaterThan(0);
  publicRequests.forEach(request => request.flush(publicSettings));

  const appRequests = httpTestingController.match(req => req.url.endsWith('/api/v1/settings') && req.method === 'GET');
  expect(appRequests.length).toBeGreaterThan(0);
  appRequests.forEach(request => request.flush(appSettings));

  return {publicSettings, appSettings};
}

describe('AppSettingsService', () => {
  let service: AppSettingsService;
  let httpTestingController: HttpTestingController;
  let authService: ReturnType<typeof createAuthServiceStub>;
  let queryClientHarness: ReturnType<typeof createQueryClientHarness>;

  beforeEach(() => {
    authService = createAuthServiceStub();
    queryClientHarness = createQueryClientHarness();
    vi.spyOn(queryClientHarness.queryClient, 'removeQueries').mockImplementation(() => undefined);

    TestBed.configureTestingModule({
      providers: [
        ...queryClientHarness.providers,
        AppSettingsService,
        {provide: AuthService, useValue: authService},
      ],
    });

    service = TestBed.inject(AppSettingsService);
    httpTestingController = TestBed.inject(HttpTestingController);
    flushSignalAndQueryEffects();
  });

  afterEach(() => {
    httpTestingController.verify();
    queryClientHarness.queryClient.clear();
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('exposes a public-settings query option that fetches the expected payload', async () => {
    const publicSettings = buildPublicSettings({
      oidcEnabled: false,
      remoteAuthEnabled: false,
      oidcForceOnlyMode: true,
    });
    flushInitialSettingsRequests(httpTestingController);
    const queryFn = service.getPublicSettingsQueryOptions().queryFn as (context: never) => Promise<PublicAppSettings>;

    const queryResultPromise = queryFn({} as never);

    httpTestingController.expectOne(req => req.url.endsWith('/api/v1/public-settings')).flush(publicSettings);

    await expect(queryResultPromise).resolves.toEqual(publicSettings);
  });

  it('removes authenticated settings queries when the auth token becomes null', () => {
    const removeQueriesSpy = vi.spyOn(queryClientHarness.queryClient, 'removeQueries').mockImplementation(() => undefined);
    flushInitialSettingsRequests(httpTestingController);

    authService.token.set(null);
    flushSignalAndQueryEffects();

    expect(removeQueriesSpy).toHaveBeenCalledWith({queryKey: APP_SETTINGS_QUERY_KEY});
  });

  it('updates both caches when OIDC is toggled successfully', async () => {
    const initialSettings = flushInitialSettingsRequests(httpTestingController, {
      publicSettings: {
        oidcEnabled: true,
        remoteAuthEnabled: true,
        oidcForceOnlyMode: false,
      },
      appSettings: {
        oidcEnabled: true,
        remoteAuthEnabled: true,
        oidcForceOnlyMode: false,
      },
    });
    await Promise.resolve();
    flushSignalAndQueryEffects();

    const setQueryDataSpy = vi.spyOn(queryClientHarness.queryClient, 'setQueryData');
    Object.assign(service, {
      appSettings: () => initialSettings.appSettings,
    });
    queryClientHarness.queryClient.setQueryData(PUBLIC_SETTINGS_QUERY_KEY, initialSettings.publicSettings);

    service.toggleOidcEnabled(false).subscribe();

    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/settings') && req.method === 'PUT');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual([{name: 'OIDC_ENABLED', value: false}]);
    request.flush(null);
    await Promise.resolve();
    flushSignalAndQueryEffects();

    expect(setQueryDataSpy).toHaveBeenCalledWith(
      APP_SETTINGS_QUERY_KEY,
      expect.objectContaining({oidcEnabled: false}),
    );
    expect(setQueryDataSpy).toHaveBeenCalledWith(
      PUBLIC_SETTINGS_QUERY_KEY,
      expect.objectContaining({oidcEnabled: false}),
    );
  });
});
