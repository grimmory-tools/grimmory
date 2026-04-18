import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {of} from 'rxjs';
import {describe, expect, it, vi} from 'vitest';
import {TranslocoService} from '@jsverse/transloco';

import {AppSettings, KoboSettings} from '../../../../../shared/model/app-settings.model';
import {SettingsHelperService} from '../../../../../shared/service/settings-helper.service';
import {ShelfService} from '../../../../book/service/shelf.service';
import {UserService, type User} from '../../../user-management/user.service';
import {KoboService} from './kobo.service';
import {KoboSyncSettingsComponent} from './kobo-sync-settings-component';
import {AppSettingsService} from '../../../../../shared/service/app-settings.service';

function buildUser(overrides: Partial<User['permissions']> = {}): User {
  return {
    id: 1,
    username: 'admin',
    name: 'Admin',
    email: 'admin@example.com',
    assignedLibraries: [],
    permissions: {
      admin: false,
      canUpload: false,
      canDownload: false,
      canEmailBook: false,
      canDeleteBook: false,
      canEditMetadata: false,
      canManageLibrary: false,
      canManageMetadataConfig: false,
      canSyncKoReader: false,
      canSyncKobo: false,
      canAccessOpds: false,
      canAccessBookdrop: false,
      canAccessLibraryStats: false,
      canAccessUserStats: false,
      canAccessTaskManager: false,
      canManageEmailConfig: false,
      canManageGlobalPreferences: false,
      canManageIcons: false,
      canManageFonts: false,
      demoUser: false,
      canBulkAutoFetchMetadata: false,
      canBulkCustomFetchMetadata: false,
      canBulkEditMetadata: false,
      canBulkRegenerateCover: false,
      canMoveOrganizeFiles: false,
      canBulkLockUnlockMetadata: false,
      ...overrides,
    },
    userSettings: {} as User['userSettings'],
  };
}

function buildAppSettings(koboSettings: KoboSettings): AppSettings {
  return {
    koboSettings,
  } as AppSettings;
}

describe('KoboSyncSettingsComponent', () => {
  it('hydrates admin kobo settings when app settings arrive after the user', () => {
    const userState = signal<User | null>(buildUser({admin: true}));
    const appSettingsState = signal<AppSettings | null>(null);

    TestBed.configureTestingModule({
      imports: [KoboSyncSettingsComponent],
      providers: [
        {
          provide: UserService,
          useValue: {
            currentUser: () => userState(),
          },
        },
        {
          provide: AppSettingsService,
          useValue: {
            appSettings: () => appSettingsState(),
          },
        },
        {
          provide: KoboService,
          useValue: {
            getUser: () => of({
              token: '',
              syncEnabled: false,
              progressMarkAsReadingThreshold: 1,
              progressMarkAsFinishedThreshold: 99,
              autoAddToShelf: false,
              twoWayProgressSync: false,
            }),
          },
        },
        {provide: SettingsHelperService, useValue: {saveSetting: vi.fn()}},
        {provide: ShelfService, useValue: {reloadShelves: vi.fn()}},
        {provide: TranslocoService, useValue: {translate: vi.fn((key: string) => key)}},
      ],
    });
    TestBed.overrideComponent(KoboSyncSettingsComponent, {
      set: {template: ''},
    });

    const fixture = TestBed.createComponent(KoboSyncSettingsComponent);
    const component = fixture.componentInstance;

    TestBed.flushEffects();
    expect(component.koboSettings.convertToKepub).toBe(false);

    appSettingsState.set(buildAppSettings({
      convertToKepub: true,
      conversionLimitInMb: 42,
      convertCbxToEpub: true,
      conversionImageCompressionPercentage: 73,
      conversionLimitInMbForCbx: 84,
      forceEnableHyphenation: true,
      forwardToKoboStore: true,
    }));

    TestBed.flushEffects();

    expect(component.koboSettings).toEqual({
      convertToKepub: true,
      conversionLimitInMb: 42,
      convertCbxToEpub: true,
      conversionImageCompressionPercentage: 73,
      conversionLimitInMbForCbx: 84,
      forceEnableHyphenation: true,
      forwardToKoboStore: true,
    });

    fixture.destroy();
  });
});
