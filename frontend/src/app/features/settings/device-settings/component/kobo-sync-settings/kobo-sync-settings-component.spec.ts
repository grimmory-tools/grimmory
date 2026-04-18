import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {of, Subject, type Observable} from 'rxjs';
import {afterEach, describe, expect, it, vi} from 'vitest';
import {TranslocoService} from '@jsverse/transloco';

import {AppSettings, KoboSettings} from '../../../../../shared/model/app-settings.model';
import {getTranslocoModule} from '../../../../../core/testing/transloco-testing';
import {SettingsHelperService} from '../../../../../shared/service/settings-helper.service';
import {ShelfService} from '../../../../book/service/shelf.service';
import {UserService, type User} from '../../../user-management/user.service';
import {KoboService, type KoboSyncSettings} from './kobo.service';
import {KoboSyncSettingsComponent} from './kobo-sync-settings-component';
import {AppSettingsService} from '../../../../../shared/service/app-settings.service';

const DEFAULT_KOBO_SYNC_SETTINGS: KoboSyncSettings = {
  token: '',
  syncEnabled: false,
  progressMarkAsReadingThreshold: 1,
  progressMarkAsFinishedThreshold: 99,
  autoAddToShelf: false,
  twoWayProgressSync: false,
};

const DEFAULT_KOBO_ADMIN_SETTINGS: KoboSettings = {
  convertToKepub: false,
  conversionLimitInMb: 100,
  convertCbxToEpub: false,
  conversionImageCompressionPercentage: 85,
  conversionLimitInMbForCbx: 100,
  forceEnableHyphenation: false,
  forwardToKoboStore: false,
};

function buildUser(overrides: Partial<User['permissions']> = {}): User {
  return {
    id: 1,
    username: 'admin',
    name: 'Admin',
    email: 'admin@example.com',
    assignedLibraries: [],
    permissions: {admin: false, ...overrides} as User['permissions'],
    userSettings: {} as User['userSettings'],
  };
}

function buildAppSettings(koboSettings: KoboSettings): AppSettings {
  return {koboSettings} as AppSettings;
}

interface KoboTestEnv {
  userState: ReturnType<typeof signal<User | null>>;
  appSettingsState: ReturnType<typeof signal<AppSettings | null>>;
  getUser: () => Observable<KoboSyncSettings>;
}

function setupKoboTest(env: KoboTestEnv): void {
  TestBed.configureTestingModule({
    imports: [KoboSyncSettingsComponent],
    providers: [
      {provide: UserService, useValue: {currentUser: () => env.userState()}},
      {provide: AppSettingsService, useValue: {appSettings: () => env.appSettingsState()}},
      {provide: KoboService, useValue: {getUser: env.getUser}},
      {provide: SettingsHelperService, useValue: {saveSetting: vi.fn()}},
      {provide: ShelfService, useValue: {reloadShelves: vi.fn()}},
      {provide: TranslocoService, useValue: {translate: vi.fn((key: string) => key)}},
    ],
  });
  TestBed.overrideComponent(KoboSyncSettingsComponent, {set: {template: ''}});
}

describe('KoboSyncSettingsComponent', () => {
  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('hydrates admin kobo settings when app settings arrive after the user', () => {
    const userState = signal<User | null>(buildUser({admin: true}));
    const appSettingsState = signal<AppSettings | null>(null);

    setupKoboTest({userState, appSettingsState, getUser: () => of(DEFAULT_KOBO_SYNC_SETTINGS)});

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

  it('does not overwrite an in-flight admin slider edit when settings arrive late', () => {
    const userState = signal<User | null>(buildUser({admin: true}));
    const appSettingsState = signal<AppSettings | null>(null);

    setupKoboTest({userState, appSettingsState, getUser: () => of(DEFAULT_KOBO_SYNC_SETTINGS)});

    const fixture = TestBed.createComponent(KoboSyncSettingsComponent);
    const component = fixture.componentInstance;

    TestBed.flushEffects();

    appSettingsState.set(buildAppSettings(DEFAULT_KOBO_ADMIN_SETTINGS));
    TestBed.flushEffects();

    component.koboSettings.conversionLimitInMb = 250;

    appSettingsState.set(buildAppSettings(DEFAULT_KOBO_ADMIN_SETTINGS));
    TestBed.flushEffects();

    expect(component.koboSettings.conversionLimitInMb).toBe(250);

    fixture.destroy();
  });

  it('hydrates the sync form and rendered toggle when user settings load after mount', async () => {
    const userState = signal<User | null>(buildUser({canSyncKobo: true}));
    const appSettingsState = signal<AppSettings | null>(null);
    const koboSettings$ = new Subject<KoboSyncSettings>();

    await TestBed.configureTestingModule({
      imports: [KoboSyncSettingsComponent, getTranslocoModule()],
      providers: [
        {provide: UserService, useValue: {currentUser: () => userState()}},
        {provide: AppSettingsService, useValue: {appSettings: () => appSettingsState()}},
        {provide: KoboService, useValue: {getUser: () => koboSettings$.asObservable()}},
        {provide: SettingsHelperService, useValue: {saveSetting: vi.fn()}},
        {provide: ShelfService, useValue: {reloadShelves: vi.fn()}},
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(KoboSyncSettingsComponent);
    fixture.detectChanges();
    await fixture.whenStable();

    koboSettings$.next({
      token: 'token-123',
      syncEnabled: true,
      progressMarkAsReadingThreshold: 2,
      progressMarkAsFinishedThreshold: 95,
      autoAddToShelf: true,
      twoWayProgressSync: true,
    });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const syncToggle = fixture.nativeElement.querySelector('input#koboSyncEnabled') as HTMLInputElement | null;

    expect(syncToggle).not.toBeNull();
    expect(syncToggle?.checked).toBe(true);
    expect(fixture.componentInstance.syncForm.getRawValue()).toEqual({
      token: 'token-123',
      syncEnabled: true,
      progressMarkAsReadingThreshold: 2,
      progressMarkAsFinishedThreshold: 95,
      autoAddToShelf: true,
      twoWayProgressSync: true,
    });
    expect(fixture.nativeElement.querySelector('input#koboToken')).not.toBeNull();

    fixture.destroy();
  });
});
