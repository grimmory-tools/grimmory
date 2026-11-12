import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {of, type Observable} from 'rxjs';
import {afterEach, describe, expect, it, vi} from 'vitest';
import {MessageService, ToastMessageOptions} from '@openng/optimus-ui/api';
import {TranslocoService} from '@jsverse/transloco';

import {AppSettings} from '../../../shared/model/app-settings.model';
import {AppSettingsService} from '../../../shared/service/app-settings.service';
import {UserService, type User} from '../user-management/user.service';
import {OpdsService, type OpdsUserV2} from './opds.service';
import {OpdsSettings} from './opds-settings';

function buildUser(overrides: Partial<User['permissions']> = {}): User {
  return {
    id: 1,
    username: 'reader',
    name: 'Reader',
    email: 'reader@example.com',
    locale: 'en',
    theme: 'grimmory',
    themeAccent: null,
    themeSyncEnabled: true,
    assignedLibraries: [],
    permissions: {admin: false, canAccessOpds: true, ...overrides} as User['permissions'],
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

interface OpdsTestEnv {
  userState: ReturnType<typeof signal<User | null>>;
  appSettingsState: ReturnType<typeof signal<AppSettings | null>>;
  getUser: () => Observable<OpdsUserV2[]>;
  messageServiceAdd?: (options: ToastMessageOptions) => void;
}

function setupOpdsTest(env: OpdsTestEnv): void {
  TestBed.configureTestingModule({
    imports: [OpdsSettings],
    providers: [
      {provide: UserService, useValue: {currentUser: () => env.userState()}},
      {
        provide: AppSettingsService,
        useValue: {
          appSettings: () => env.appSettingsState(),
          saveSettings: () => of(void 0),
        },
      },
      {provide: OpdsService, useValue: {getUser: env.getUser}},
      {provide: MessageService, useValue: {add: env.messageServiceAdd ?? vi.fn()}},
      {provide: TranslocoService, useValue: {translate: vi.fn((key: string) => key)}},
    ],
  });
  TestBed.overrideComponent(OpdsSettings, {set: {template: ''}});
}

describe('OpdsSettings', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('clears the loading state when app settings arrive after permission resolution', () => {
    const userState = signal<User | null>(buildUser());
    const appSettingsState = signal<AppSettings | null>(null);
    const getUser = vi.fn(() => of([] as OpdsUserV2[]));

    setupOpdsTest({userState, appSettingsState, getUser});

    const fixture = TestBed.createComponent(OpdsSettings);
    const component = fixture.componentInstance;
    component.ngOnInit();

    TestBed.flushEffects();
    expect(component.loading()).toBe(true);

    appSettingsState.set(buildAppSettings());
    TestBed.flushEffects();

    expect(getUser).not.toHaveBeenCalled();
    expect(component.loading()).toBe(false);

    fixture.destroy();
  });

  it('loads OPDS users when delayed app settings enable the catalog', () => {
    const userState = signal<User | null>(buildUser());
    const appSettingsState = signal<AppSettings | null>(null);
    const getUser = vi.fn(() => of([
      {id: 1, username: 'reader', sortOrder: 'RECENT'} as OpdsUserV2,
    ]));

    setupOpdsTest({userState, appSettingsState, getUser});

    const fixture = TestBed.createComponent(OpdsSettings);
    const component = fixture.componentInstance;
    component.ngOnInit();

    TestBed.flushEffects();
    appSettingsState.set(buildAppSettings({opdsServerEnabled: true}));
    TestBed.flushEffects();

    expect(getUser).toHaveBeenCalledOnce();
    expect(component.loading()).toBe(false);
    expect(component.users()).toHaveLength(1);

    fixture.destroy();
  });

  it('shows feedback after copying opds endpoint', async () => {
    const userState = signal<User | null>(buildUser());
    const appSettingsState = signal<AppSettings | null>(null);
    const getUser = vi.fn(() => of([] as OpdsUserV2[]));
    const messageServiceAdd = vi.fn();

    const writeText = vi.fn().mockResolvedValue(null);
    vi.stubGlobal('navigator', { clipboard: { writeText } });

    setupOpdsTest({userState, appSettingsState, getUser, messageServiceAdd});

    const fixture = TestBed.createComponent(OpdsSettings);
    const component = fixture.componentInstance;

    component.copyEndpoint();

    expect(writeText).toHaveBeenCalledWith("http://localhost:6060/api/v1/opds");
    await vi.waitFor(() => expect(messageServiceAdd).toHaveBeenCalledWith({
      severity: 'success',
      summary: 'common.success',
      detail: 'settingsOpds.opdsCopied',
    }));
  });

  it('shows error after copying opds endpoint failure', async () => {
    const userState = signal<User | null>(buildUser());
    const appSettingsState = signal<AppSettings | null>(null);
    const getUser = vi.fn(() => of([] as OpdsUserV2[]));
    const messageServiceAdd = vi.fn();

    const writeText = vi.fn().mockRejectedValue(new Error());
    vi.stubGlobal('navigator', { clipboard: { writeText } });

    setupOpdsTest({userState, appSettingsState, getUser, messageServiceAdd});

    const fixture = TestBed.createComponent(OpdsSettings);
    const component = fixture.componentInstance;

    component.copyEndpoint();

    expect(writeText).toHaveBeenCalledWith("http://localhost:6060/api/v1/opds");
    await vi.waitFor(() => expect(messageServiceAdd).toHaveBeenCalledWith({
      severity: 'error',
      summary: 'common.error',
      detail: 'settingsOpds.copyFailed',
    }));
  });

  it('shows feedback after copying komga endpoint', async () => {
    const userState = signal<User | null>(buildUser());
    const appSettingsState = signal<AppSettings | null>(null);
    const getUser = vi.fn(() => of([] as OpdsUserV2[]));
    const messageServiceAdd = vi.fn();

    const writeText = vi.fn().mockResolvedValue(null);
    vi.stubGlobal('navigator', { clipboard: { writeText } });

    setupOpdsTest({userState, appSettingsState, getUser, messageServiceAdd});

    const fixture = TestBed.createComponent(OpdsSettings);
    const component = fixture.componentInstance;

    component.copyKomgaEndpoint();

    expect(writeText).toHaveBeenCalledWith("http://localhost:6060/komga");
    await vi.waitFor(() => expect(messageServiceAdd).toHaveBeenCalledWith({
      severity: 'success',
      summary: 'common.success',
      detail: 'settingsOpds.komgaCopied',
    }));
  });

  it('shows error after copying komga endpoint failure', async () => {
    const userState = signal<User | null>(buildUser());
    const appSettingsState = signal<AppSettings | null>(null);
    const getUser = vi.fn(() => of([] as OpdsUserV2[]));
    const messageServiceAdd = vi.fn();

    const writeText = vi.fn().mockRejectedValue(new Error());
    vi.stubGlobal('navigator', { clipboard: { writeText } });

    setupOpdsTest({userState, appSettingsState, getUser, messageServiceAdd});

    const fixture = TestBed.createComponent(OpdsSettings);
    const component = fixture.componentInstance;

    component.copyKomgaEndpoint();

    expect(writeText).toHaveBeenCalledWith("http://localhost:6060/komga");
    await vi.waitFor(() => expect(messageServiceAdd).toHaveBeenCalledWith({
      severity: 'error',
      summary: 'common.error',
      detail: 'settingsOpds.copyFailed',
    }));
  });
});
