import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {of} from 'rxjs';
import {describe, expect, it, vi} from 'vitest';
import {MessageService} from 'primeng/api';
import {TranslocoService} from '@jsverse/transloco';

import {AppSettings} from '../../../shared/model/app-settings.model';
import {AppSettingsService} from '../../../shared/service/app-settings.service';
import {UserService, type User} from '../user-management/user.service';
import {OpdsService} from './opds.service';
import {OpdsSettings} from './opds-settings';

function buildUser(overrides: Partial<User['permissions']> = {}): User {
  return {
    id: 1,
    username: 'reader',
    name: 'Reader',
    email: 'reader@example.com',
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
      canAccessOpds: true,
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

function buildAppSettings(overrides: Partial<AppSettings> = {}): AppSettings {
  return {
    opdsServerEnabled: false,
    komgaApiEnabled: false,
    komgaGroupUnknown: true,
    ...overrides,
  } as AppSettings;
}

describe('OpdsSettings', () => {
  it('clears the loading state when app settings arrive after permission resolution', () => {
    const userState = signal<User | null>(buildUser());
    const appSettingsState = signal<AppSettings | null>(null);
    const getUser = vi.fn(() => of([]));

    TestBed.configureTestingModule({
      imports: [OpdsSettings],
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
            saveSettings: () => of(void 0),
          },
        },
        {
          provide: OpdsService,
          useValue: {
            getUser,
          },
        },
        {provide: MessageService, useValue: {add: vi.fn()}},
        {provide: TranslocoService, useValue: {translate: vi.fn((key: string) => key)}},
      ],
    });
    TestBed.overrideComponent(OpdsSettings, {
      set: {template: ''},
    });

    const fixture = TestBed.createComponent(OpdsSettings);
    const component = fixture.componentInstance;
    component.ngOnInit();

    TestBed.flushEffects();
    expect(component.loading).toBe(true);

    appSettingsState.set(buildAppSettings());
    TestBed.flushEffects();

    expect(getUser).not.toHaveBeenCalled();
    expect(component.loading).toBe(false);

    fixture.destroy();
  });

  it('loads OPDS users when delayed app settings enable the catalog', () => {
    const userState = signal<User | null>(buildUser());
    const appSettingsState = signal<AppSettings | null>(null);
    const getUser = vi.fn(() => of([
      {id: 1, username: 'reader', sortOrder: 'RECENT'},
    ]));

    TestBed.configureTestingModule({
      imports: [OpdsSettings],
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
            saveSettings: () => of(void 0),
          },
        },
        {
          provide: OpdsService,
          useValue: {
            getUser,
          },
        },
        {provide: MessageService, useValue: {add: vi.fn()}},
        {provide: TranslocoService, useValue: {translate: vi.fn((key: string) => key)}},
      ],
    });
    TestBed.overrideComponent(OpdsSettings, {
      set: {template: ''},
    });

    const fixture = TestBed.createComponent(OpdsSettings);
    const component = fixture.componentInstance;
    component.ngOnInit();

    TestBed.flushEffects();
    appSettingsState.set(buildAppSettings({opdsServerEnabled: true}));
    TestBed.flushEffects();

    expect(getUser).toHaveBeenCalledOnce();
    expect(component.loading).toBe(false);
    expect(component.users).toHaveLength(1);

    fixture.destroy();
  });
});
