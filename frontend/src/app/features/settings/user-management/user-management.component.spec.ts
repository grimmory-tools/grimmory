import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {MessageService} from 'primeng/api';
import {of, type Observable} from 'rxjs';
import {describe, expect, it, vi} from 'vitest';

import {getTranslocoModule} from '../../../core/testing/transloco-testing';
import {AppSettings} from '../../../shared/model/app-settings.model';
import {AppSettingsService} from '../../../shared/service/app-settings.service';
import {DialogLauncherService} from '../../../shared/services/dialog-launcher.service';
import {LibraryService} from '../../book/service/library.service';
import {UserManagementComponent, type UserWithEditing} from './user-management.component';
import {User, UserService} from './user.service';

function buildUser(overrides: Partial<User['permissions']> = {}): User {
  return {
    id: 1,
    username: 'admin',
    name: 'Admin',
    email: 'admin@example.com',
    locale: 'en',
    theme: 'grimmory',
    themeAccent: null,
    themeSyncEnabled: true,
    assignedLibraries: [],
    permissions: {admin: false, ...overrides} as User['permissions'],
    userSettings: {} as User['userSettings'],
  };
}

interface UserManagementTestEnv {
  userState: ReturnType<typeof signal<User | null>>;
  appSettingsState: ReturnType<typeof signal<AppSettings | null>>;
  getUsers: () => Observable<UserWithEditing[]>;
}

function setupUserManagementTest(env: UserManagementTestEnv): void {
  TestBed.configureTestingModule({
    imports: [UserManagementComponent, getTranslocoModule()],
    providers: [
      {
        provide: UserService,
        useValue: {
          currentUser: () => env.userState(),
          getUsers: env.getUsers
        }
      },
      { provide: LibraryService, useValue: { libraries: signal([]) } },
      {
        provide: DialogLauncherService,
        useValue: {
          openCreateUserDialog: vi.fn(() => Promise.resolve(null)),
        },
      },
      {provide: AppSettingsService, useValue: {appSettings: () => env.appSettingsState()}},
      {provide: MessageService, useValue: {add: vi.fn()}},
    ],
  });
}

describe('UserManagementComponent', () => {
  it('loads and hydrates users on init', () => {
    const userState = signal<User | null>(buildUser({admin: true}));
    const appSettingsState = signal<AppSettings | null>(null);
    const getUsers = vi.fn(() => of([buildUser({admin: true})] as UserWithEditing[]));

    setupUserManagementTest({userState, appSettingsState, getUsers}); 

    const fixture = TestBed.createComponent(UserManagementComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    expect(getUsers).toHaveBeenCalledOnce();
    expect(component.users()).toHaveLength(1);

    fixture.destroy();
  });


  it('counts and grants book conversion permission with metadata permissions', () => {
    const userState = signal<User | null>(buildUser({admin: true}));
    const appSettingsState = signal<AppSettings | null>(null);
    const getUsers = vi.fn(() => of([] as UserWithEditing[]));
    const user = buildUser({canEditMetadata: true, canConvertBook: true});
    const adminUser = buildUser({admin: true});

    setupUserManagementTest({userState, appSettingsState, getUsers});

    const fixture = TestBed.createComponent(UserManagementComponent);
    const component = fixture.componentInstance;

    expect(component.getMetadataEditingPermissionsCount(user)).toBe(2);

    component.onAdminCheckboxChange(adminUser);
    expect(adminUser.permissions.canConvertBook).toBe(true);

    fixture.destroy();
  });
  it.skip('needs service seams to verify user loading, edit toggling, and save payload shaping', () => {
    // TODO(seam): Cover loadUsers, toggleEdit, and saveUser once the table-editing state and async user-service flows are isolated.
  });

  it.skip('needs dialog seams to verify create-user refresh, delete confirmation, and password-change validation', () => {
    // TODO(seam): Cover openCreateUserDialog, deleteUser, and submitPasswordChange after extracting confirm and modal runtime concerns.
  });
});
