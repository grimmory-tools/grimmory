import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {of, Subject, type Observable} from 'rxjs';
import {MessageService} from 'primeng/api';
import {describe, expect, it, vi} from 'vitest';

import {getTranslocoModule} from '../../../core/testing/transloco-testing';
import {type Library} from '../../book/model/library.model';
import {LibraryService} from '../../book/service/library.service';
import {DialogLauncherService} from '../../../shared/services/dialog-launcher.service';
import {UserManagementComponent} from './user-management.component';
import {type User, UserService} from './user.service';

function buildPermissions(overrides: Partial<User['permissions']> = {}): User['permissions'] {
  return {
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
    canBulkResetGrimmoryReadProgress: false,
    canBulkResetBookloreReadProgress: false,
    canBulkResetKoReaderReadProgress: false,
    canBulkResetBookReadStatus: false,
    ...overrides,
  };
}

function buildUser(overrides: Partial<User> = {}): User {
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
    permissions: buildPermissions(),
    userSettings: {} as User['userSettings'],
    ...overrides,
  };
}

function buildLibrary(overrides: Partial<Library> = {}): Library {
  return {
    id: 1,
    name: 'Library',
    watch: false,
    paths: [],
    ...overrides,
  };
}

interface UserManagementTestEnv {
  currentUser: ReturnType<typeof signal<User | null>>;
  getUsers: () => Observable<User[]>;
}

function setupUserManagementTest(env: UserManagementTestEnv, useRealTemplate = false): void {
  TestBed.configureTestingModule({
    imports: [UserManagementComponent, getTranslocoModule()],
    providers: [
      {
        provide: UserService,
        useValue: {
          currentUser: () => env.currentUser(),
          getUsers: env.getUsers,
        },
      },
      {provide: LibraryService, useValue: {libraries: signal([])}},
      {provide: DialogLauncherService, useValue: {openCreateUserDialog: vi.fn()}},
      {provide: MessageService, useValue: {add: vi.fn()}},
    ],
  });

  if (useRealTemplate) {
    return;
  }

  TestBed.overrideComponent(UserManagementComponent, {
    set: {
      template: `
        @if (users().length === 0) {
          <p class="empty-state">empty</p>
        } @else {
          <ul>
            @for (user of users(); track user.id) {
              <li>{{ user.username }}:{{ user.libraryNames }}</li>
            }
          </ul>
        }
      `,
    },
  });
}

describe('UserManagementComponent', () => {
  it('renders users when the initial load resolves after the first change detection pass', () => {
    const usersSubject = new Subject<User[]>();
    const currentUser = signal<User | null>(buildUser({id: 99, permissions: buildPermissions({admin: true})}));
    const getUsers = vi.fn(() => usersSubject.asObservable());

    setupUserManagementTest({currentUser, getUsers}, true);

    const fixture = TestBed.createComponent(UserManagementComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.empty-state')).not.toBeNull();

    usersSubject.next([
      buildUser({
        id: 7,
        username: 'slow-reader',
        assignedLibraries: [buildLibrary({id: 3, name: 'Main Library'})],
      }),
    ]);
    fixture.detectChanges();

    expect(getUsers).toHaveBeenCalledOnce();
    expect(fixture.nativeElement.querySelector('.empty-state')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('slow-reader');
    expect(fixture.componentInstance.users()).toHaveLength(1);

    fixture.destroy();
  });

  it('keeps loaded users in a fresh mapped array for later refreshes', () => {
    const currentUser = signal<User | null>(buildUser());
    const getUsers = vi.fn(() => of([
      buildUser({
        id: 8,
        username: 'library-reader',
        assignedLibraries: [buildLibrary({id: 2, name: 'Sci-Fi'})],
      }),
    ]));

    setupUserManagementTest({currentUser, getUsers});

    const fixture = TestBed.createComponent(UserManagementComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance.users()).toEqual([
      expect.objectContaining({
        id: 8,
        username: 'library-reader',
        isEditing: false,
        selectedLibraryIds: [2],
        libraryNames: 'Sci-Fi',
      }),
    ]);

    fixture.destroy();
  });
});
