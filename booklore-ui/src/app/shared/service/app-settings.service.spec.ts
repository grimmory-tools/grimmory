import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {EnvironmentInjector, runInInjectionContext} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {of, throwError} from 'rxjs';

import {AppSettingsService, PublicAppSettings} from './app-settings.service';
import {AppSettings} from '../model/app-settings.model';
import {AuthService} from './auth.service';

describe('AppSettingsService', () => {
  let service: AppSettingsService;
  let httpClientMock: any;
  let authServiceMock: any;
  let injectorMock: any;

  const mockAppSettings: AppSettings = {
    autoBookSearch: true,
    similarBookRecommendation: false,
    defaultMetadataRefreshOptions: {} as any,
    libraryMetadataRefreshOptions: [],
    uploadPattern: '',
    opdsServerEnabled: false,
    komgaApiEnabled: false,
    komgaGroupUnknown: false,
    remoteAuthEnabled: false,
    oidcEnabled: true,
    oidcProviderDetails: {
      providerName: 'TestProvider',
      clientId: 'clientid',
      issuerUri: 'https://issuer.example.com',
      claimMapping: {
        username: 'username',
        email: 'email',
        name: 'name'
      }
    },
    oidcAutoProvisionDetails: {
      enableAutoProvisioning: false,
      defaultPermissions: [],
      defaultLibraryIds: []
    },
    maxFileUploadSizeInMb: 0,
    metadataProviderSettings: {
      amazon: {enabled: false, cookie: '', domain: ''},
      google: {enabled: false, language: ''},
      goodReads: {enabled: false},
      hardcover: {enabled: false, apiKey: ''},
      comicvine: {enabled: false, apiKey: ''},
      douban: {enabled: false},
      lubimyczytac: {enabled: false},
      ranobedb: {enabled: false}
    },
    metadataMatchWeights: {
      title: 0,
      subtitle: 0,
      description: 0,
      authors: 0,
      publisher: 0,
      publishedDate: 0,
      seriesName: 0,
      seriesNumber: 0,
      seriesTotal: 0,
      isbn13: 0,
      isbn10: 0,
      language: 0,
      pageCount: 0,
      categories: 0,
      amazonRating: 0,
      amazonReviewCount: 0,
      goodreadsRating: 0,
      goodreadsReviewCount: 0,
      hardcoverRating: 0,
      hardcoverReviewCount: 0,
      doubanRating: 0,
      doubanReviewCount: 0,
      lubimyczytacRating: 0,
      ranobedbRating: 0,
      coverImage: 0
    },
    metadataPersistenceSettings: {
      moveFilesToLibraryPattern: false,
      saveToOriginalFile: {
        epub: {enabled: false, maxFileSizeInMb: 0},
        pdf: {enabled: false, maxFileSizeInMb: 0},
        cbx: {enabled: false, maxFileSizeInMb: 0}
      },
      convertCbrCb7ToCbz: false
    },
    metadataPublicReviewsSettings: {
      downloadEnabled: false,
      autoDownloadEnabled: false,
      providers: []
    },
    koboSettings: {
      convertToKepub: false,
      conversionLimitInMb: 0,
      conversionImageCompressionPercentage: 0,
      convertCbxToEpub: false,
      conversionLimitInMbForCbx: 0,
      forceEnableHyphenation: false
    },
    coverCroppingSettings: {
      verticalCroppingEnabled: false,
      horizontalCroppingEnabled: false,
      aspectRatioThreshold: 0,
      smartCroppingEnabled: false
    },
    metadataDownloadOnBookdrop: false,
    telemetryEnabled: false,
    metadataProviderSpecificFields: {
      asin: false,
      amazonRating: false,
      amazonReviewCount: false,
      googleId: false,
      goodreadsId: false,
      goodreadsRating: false,
      goodreadsReviewCount: false,
      hardcoverId: false,
      hardcoverBookId: false,
      hardcoverRating: false,
      hardcoverReviewCount: false,
      comicvineId: false,
      lubimyczytacId: false,
      lubimyczytacRating: false,
      ranobedbId: false,
      ranobedbRating: false
    }
  };

  const mockPublicSettings: PublicAppSettings = {
    oidcEnabled: true,
    remoteAuthEnabled: false,
    oidcProviderDetails: mockAppSettings.oidcProviderDetails
  };

  beforeEach(() => {
    httpClientMock = {
      get: vi.fn(),
      put: vi.fn()
    };

    authServiceMock = {
      clearOIDCTokens: vi.fn()
    };

    injectorMock = {
      get: vi.fn().mockReturnValue(authServiceMock)
    };

    TestBed.configureTestingModule({
      providers: [
        AppSettingsService,
        {provide: HttpClient, useValue: httpClientMock},
        {provide: AuthService, useValue: authServiceMock},
        {provide: EnvironmentInjector, useValue: injectorMock}
      ]
    });

    const injector = TestBed.inject(EnvironmentInjector);
    service = runInInjectionContext(injector, () => TestBed.inject(AppSettingsService));
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('should fetch app settings and update subject', () => {
    httpClientMock.get.mockReturnValue(of(mockAppSettings));
    service['fetchAppSettings']().subscribe(settings => {
      expect(settings).toEqual(mockAppSettings);
      expect(service['appSettingsSubject'].value).toEqual(mockAppSettings);
    });
  });

  it('should handle error when fetching app settings', () => {
    httpClientMock.get.mockReturnValue(throwError(() => new Error('fail')));
    service['fetchAppSettings']().subscribe({
      error: (err: any) => {
        expect(service['appSettingsSubject'].value).toBeNull();
        expect(err).toBeInstanceOf(Error);
      }
    });
  });

  it('should fetch public settings and update subject', () => {
    httpClientMock.get.mockReturnValue(of(mockPublicSettings));
    service['fetchPublicSettings']().subscribe(settings => {
      expect(settings).toEqual(mockPublicSettings);
      expect(service['publicAppSettingsSubject'].value).toEqual(mockPublicSettings);
    });
  });

  it('should handle error when fetching public settings', () => {
    httpClientMock.get.mockReturnValue(throwError(() => new Error('fail')));
    service['fetchPublicSettings']().subscribe({
      error: (err: any) => {
        expect(err).toBeInstanceOf(Error);
      }
    });
  });

  it('should sync public settings from app settings', () => {
    service['publicAppSettingsSubject'].next(null);
    service['syncPublicSettings'](mockAppSettings);
    expect(service['publicAppSettingsSubject'].value).toEqual(mockPublicSettings);
  });

  it('should not update public settings if unchanged', () => {
    service['publicAppSettingsSubject'].next(mockPublicSettings);
    service['syncPublicSettings'](mockAppSettings);
    expect(service['publicAppSettingsSubject'].value).toEqual(mockPublicSettings);
  });

  it('should save settings and update appSettingsSubject', () => {
    httpClientMock.put.mockReturnValue(of(void 0));
    service['appSettingsSubject'].next({...mockAppSettings});
    service.saveSettings([{key: 'oidcEnabled', newValue: false}]).subscribe(() => {
      expect(service['appSettingsSubject'].value?.oidcEnabled).toBe(false);
    });
  });

  it('should fetch settings if current is null on save', () => {
    httpClientMock.put.mockReturnValue(of(void 0));
    httpClientMock.get.mockReturnValue(of(mockAppSettings));
    service['appSettingsSubject'].next(null);
    service.saveSettings([{key: 'oidcEnabled', newValue: true}]).subscribe(() => {
      expect(httpClientMock.get).toHaveBeenCalled();
    });
  });

  it('should handle error on saveSettings', () => {
    httpClientMock.put.mockReturnValue(throwError(() => new Error('fail')));
    service.saveSettings([{key: 'oidcEnabled', newValue: false}]).subscribe(result => {
      expect(result).toBeUndefined();
    });
  });

  it('should toggle OIDC enabled and update state', () => {
    httpClientMock.put.mockReturnValue(of(void 0));
    service['appSettingsSubject'].next({...mockAppSettings});
    service.toggleOidcEnabled(false).subscribe(() => {
      expect(service['appSettingsSubject'].value?.oidcEnabled).toBe(false);
    });
  });

  it('should call clearOIDCTokens when disabling OIDC', async () => {
    httpClientMock.put.mockReturnValue(of(void 0));
    service['appSettingsSubject'].next({...mockAppSettings});
    await new Promise<void>(resolve => {
      service.toggleOidcEnabled(false).subscribe(() => {
        setTimeout(() => {
          expect(authServiceMock.clearOIDCTokens).toHaveBeenCalled();
          resolve();
        }, 10);
      });
    });
  });

  it('should handle error on toggleOidcEnabled', () => {
    httpClientMock.put.mockReturnValue(throwError(() => new Error('fail')));
    service.toggleOidcEnabled(true).subscribe(result => {
      expect(result).toBeUndefined();
    });
  });
});

describe('AppSettingsService - API Contract Tests', () => {
  let service: AppSettingsService;
  let httpClientMock: any;
  let authServiceMock: any;
  let injectorMock: any;

  beforeEach(() => {
    httpClientMock = {
      get: vi.fn(),
      put: vi.fn()
    };

    authServiceMock = {
      clearOIDCTokens: vi.fn()
    };

    injectorMock = {
      get: vi.fn().mockReturnValue(authServiceMock)
    };

    TestBed.configureTestingModule({
      providers: [
        AppSettingsService,
        {provide: HttpClient, useValue: httpClientMock},
        {provide: AuthService, useValue: authServiceMock},
        {provide: EnvironmentInjector, useValue: injectorMock}
      ]
    });


    const injector = TestBed.inject(EnvironmentInjector);
    service = runInInjectionContext(injector, () => TestBed.inject(AppSettingsService));
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('PublicAppSettings interface contract', () => {
    it('should validate all required PublicAppSettings fields exist', () => {
      const requiredFields: (keyof PublicAppSettings)[] = [
        'oidcEnabled', 'remoteAuthEnabled', 'oidcProviderDetails'
      ];
      const mockResponse: PublicAppSettings = {
        oidcEnabled: true,
        remoteAuthEnabled: false,
        oidcProviderDetails: {
          providerName: 'Provider',
          clientId: 'id',
          issuerUri: 'issuer',
          claimMapping: {
            username: 'username',
            email: 'email',
            name: 'name'
          }
        }
      };
      httpClientMock.get.mockReturnValue(of(mockResponse));
      service['fetchPublicSettings']().subscribe(settings => {
        requiredFields.forEach(field => {
          expect(settings).toHaveProperty(field);
          expect(settings[field]).toBeDefined();
        });
      });
    });
  });

  describe('HTTP endpoint contract', () => {
    it('should call correct endpoint for fetchAppSettings', () => {
      httpClientMock.get.mockReturnValue(of({}));
      service['fetchAppSettings']().subscribe();
      expect(httpClientMock.get).toHaveBeenCalledWith(expect.stringMatching(/\/api\/v1\/settings$/));
    });

    it('should call correct endpoint for fetchPublicSettings', () => {
      httpClientMock.get.mockReturnValue(of({}));
      service['fetchPublicSettings']().subscribe();
      expect(httpClientMock.get).toHaveBeenCalledWith(expect.stringMatching(/\/api\/v1\/public-settings$/));
    });

    it('should call correct endpoint for saveSettings', () => {
      httpClientMock.put.mockReturnValue(of(void 0));
      service.saveSettings([{key: 'oidcEnabled', newValue: true}]).subscribe();
      expect(httpClientMock.put).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/v1\/settings$/),
        [{name: 'oidcEnabled', value: true}]
      );
    });

    it('should call correct endpoint for toggleOidcEnabled', () => {
      httpClientMock.put.mockReturnValue(of(void 0));
      service.toggleOidcEnabled(true).subscribe();
      expect(httpClientMock.put).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/v1\/settings$/),
        [{name: 'OIDC_ENABLED', value: true}]
      );
    });
  });

  describe('Request payload contract', () => {
    it('should send correct structure for saveSettings', () => {
      httpClientMock.put.mockReturnValue(of(void 0));
      service.saveSettings([{key: 'remoteAuthEnabled', newValue: false}]).subscribe();
      expect(httpClientMock.put).toHaveBeenCalledWith(
        expect.any(String),
        [{name: 'remoteAuthEnabled', value: false}]
      );
    });

    it('should send correct structure for toggleOidcEnabled', () => {
      httpClientMock.put.mockReturnValue(of(void 0));
      service.toggleOidcEnabled(false).subscribe();
      expect(httpClientMock.put).toHaveBeenCalledWith(
        expect.any(String),
        [{name: 'OIDC_ENABLED', value: false}]
      );
    });
  });

  describe('Response type contract', () => {
    it('should expect AppSettings from fetchAppSettings', () => {
      const mockSettings: AppSettings = {
        autoBookSearch: true,
        similarBookRecommendation: false,
        defaultMetadataRefreshOptions: {} as any,
        libraryMetadataRefreshOptions: [],
        uploadPattern: '',
        opdsServerEnabled: false,
        komgaApiEnabled: false,
        komgaGroupUnknown: false,
        remoteAuthEnabled: false,
        oidcEnabled: true,
        oidcProviderDetails: {
          providerName: 'Provider',
          clientId: 'id',
          issuerUri: 'issuer',
          claimMapping: {
            username: 'username',
            email: 'email',
            name: 'name'
          }
        },
        oidcAutoProvisionDetails: {
          enableAutoProvisioning: false,
          defaultPermissions: [],
          defaultLibraryIds: []
        },
        maxFileUploadSizeInMb: 0,
        metadataProviderSettings: {
          amazon: {enabled: false, cookie: '', domain: ''},
          google: {enabled: false, language: ''},
          goodReads: {enabled: false},
          hardcover: {enabled: false, apiKey: ''},
          comicvine: {enabled: false, apiKey: ''},
          douban: {enabled: false},
          lubimyczytac: {enabled: false},
          ranobedb: {enabled: false}
        },
        metadataMatchWeights: {
          title: 0,
          subtitle: 0,
          description: 0,
          authors: 0,
          publisher: 0,
          publishedDate: 0,
          seriesName: 0,
          seriesNumber: 0,
          seriesTotal: 0,
          isbn13: 0,
          isbn10: 0,
          language: 0,
          pageCount: 0,
          categories: 0,
          amazonRating: 0,
          amazonReviewCount: 0,
          goodreadsRating: 0,
          goodreadsReviewCount: 0,
          hardcoverRating: 0,
          hardcoverReviewCount: 0,
          doubanRating: 0,
          doubanReviewCount: 0,
          lubimyczytacRating: 0,
          ranobedbRating: 0,
          coverImage: 0
        },
        metadataPersistenceSettings: {
          moveFilesToLibraryPattern: false,
          saveToOriginalFile: {
            epub: {enabled: false, maxFileSizeInMb: 0},
            pdf: {enabled: false, maxFileSizeInMb: 0},
            cbx: {enabled: false, maxFileSizeInMb: 0}
          },
          convertCbrCb7ToCbz: false
        },
        metadataPublicReviewsSettings: {
          downloadEnabled: false,
          autoDownloadEnabled: false,
          providers: []
        },
        koboSettings: {
          convertToKepub: false,
          conversionLimitInMb: 0,
          conversionImageCompressionPercentage: 0,
          convertCbxToEpub: false,
          conversionLimitInMbForCbx: 0,
          forceEnableHyphenation: false
        },
        coverCroppingSettings: {
          verticalCroppingEnabled: false,
          horizontalCroppingEnabled: false,
          aspectRatioThreshold: 0,
          smartCroppingEnabled: false
        },
        metadataDownloadOnBookdrop: false,
        telemetryEnabled: false,
        metadataProviderSpecificFields: {
          asin: false,
          amazonRating: false,
          amazonReviewCount: false,
          googleId: false,
          goodreadsId: false,
          goodreadsRating: false,
          goodreadsReviewCount: false,
          hardcoverId: false,
          hardcoverBookId: false,
          hardcoverRating: false,
          hardcoverReviewCount: false,
          comicvineId: false,
          lubimyczytacId: false,
          lubimyczytacRating: false,
          ranobedbId: false,
          ranobedbRating: false
        }
      };
      httpClientMock.get.mockReturnValue(of(mockSettings));
      service['fetchAppSettings']().subscribe(settings => {
        expect(settings).toHaveProperty('oidcEnabled');
        expect(settings).toHaveProperty('remoteAuthEnabled');
        expect(settings).toHaveProperty('oidcProviderDetails');
      });
    });

    it('should expect PublicAppSettings from fetchPublicSettings', () => {
      const mockSettings: PublicAppSettings = {
        oidcEnabled: false,
        remoteAuthEnabled: true,
        oidcProviderDetails: {
          providerName: 'Provider',
          clientId: 'id',
          issuerUri: 'issuer',
          claimMapping: {
            username: 'username',
            email: 'email',
            name: 'name'
          }
        }
      };
      httpClientMock.get.mockReturnValue(of(mockSettings));
      service['fetchPublicSettings']().subscribe(settings => {
        expect(settings).toHaveProperty('oidcEnabled');
        expect(settings).toHaveProperty('remoteAuthEnabled');
        expect(settings).toHaveProperty('oidcProviderDetails');
      });
    });
  });
});

