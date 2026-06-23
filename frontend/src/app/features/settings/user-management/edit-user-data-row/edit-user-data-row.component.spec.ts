import { Component, input, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { describe, expect, it, vi } from 'vitest';
import { getTranslocoModule } from '../../../../core/testing/transloco-testing';
import { LibraryService } from '../../../book/service/library.service';
import { BookService } from '../../../book/service/book.service';
import { ContentRestrictionsEditorComponent } from '../content-restrictions-editor/content-restrictions-editor.component';
import { EditUserDataRowComponent } from './edit-user-data-row.component';
import { type UserWithEditing } from '../user-management.component';
import { type User } from '../user.service';

@Component({ selector: 'app-content-restrictions-editor', template: '', standalone: true })
class ContentRestrictionsEditorStub {  
  userId = input<number>();
  isEditing = input<boolean>();
}

function buildUser(permOverrides: Partial<User['permissions']> = {}): UserWithEditing {
  return {
    id: 1,
    username: 'testuser',
    name: 'Test User',
    email: 'test@example.com',
    locale: 'en',
    theme: 'grimmory',
    themeAccent: null,
    themeSyncEnabled: true,
    assignedLibraries: [],
    isEditing: false,
    selectedLibraryIds: [],
    permissions: {
      admin: false,
      canUpload: false,
      canDownload: false,
      canDeleteBook: false,
      canEditMetadata: false,
      canManageLibrary: false,
      canEmailBook: false,
      canSyncKoReader: false,
      canSyncKobo: false,
      canAccessOpds: false,
      canAccessBookdrop: false,
      canAccessLibraryStats: false,
      canAccessUserStats: false,
      canManageMetadataConfig: false,
      canManageGlobalPreferences: false,
      canAccessTaskManager: false,
      canManageEmailConfig: false,
      canManageIcons: false,
      canManageFonts: false,
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
      ...permOverrides,
    } as User['permissions'],
    userSettings: {} as User['userSettings'],
  };
}

interface EditUserDataRowTestEnv {
  user: UserWithEditing;
}

function setupEditUserDataRowTest(env: EditUserDataRowTestEnv) {
  TestBed.configureTestingModule({
    imports: [EditUserDataRowComponent, getTranslocoModule()],
    providers: [
      { provide: LibraryService, useValue: { libraries: signal([]) } },
      { provide: BookService, useValue: { uniqueMetadata: signal({ categories: [], tags: [], authors: [] }) } },
      { provide: MessageService, useValue: { add: vi.fn() } },
    ],
  }).overrideComponent(EditUserDataRowComponent, {
    remove: { imports: [ContentRestrictionsEditorComponent] },
    add: { imports: [ContentRestrictionsEditorStub] },
  });

  const fixture = TestBed.createComponent(EditUserDataRowComponent);
  fixture.componentRef.setInput('user', env.user);
  fixture.detectChanges();
  return fixture;
}

describe('EditUserDataRowComponent', () => {

  it('emits localLibraryIds when onLibraryIdsChange is called', () => {
    const fixture = setupEditUserDataRowTest({ user: buildUser() });
    const component = fixture.componentInstance;

    const emitted: number[][] = [];
    component.libraryIdsChange.subscribe((ids) => emitted.push(ids));

    component.localLibraryIds = [5, 6];
    component.onLibraryIdsChange();

    expect(emitted).toEqual([[5, 6]]);
    fixture.destroy();
  });

  describe('isPermissionDisabled', () => {
    it('is true when not editing', () => {
      const user = buildUser();
      user.isEditing = false;
      const fixture = setupEditUserDataRowTest({ user });

      expect(fixture.componentInstance.isPermissionDisabled).toBe(true);
      fixture.destroy();
    });

    it('is true when editing as admin', () => {
      const user = buildUser({ admin: true });
      user.isEditing = true;
      const fixture = setupEditUserDataRowTest({ user });

      expect(fixture.componentInstance.isPermissionDisabled).toBe(true);
      fixture.destroy();
    });

    it('is false when editing as non-admin', () => {
      const user = buildUser({ admin: false });
      user.isEditing = true;
      const fixture = setupEditUserDataRowTest({ user });

      expect(fixture.componentInstance.isPermissionDisabled).toBe(false);
      fixture.destroy();
    });
  });

  describe('onAdminCheckboxChange', () => {
    it('grants all permissions when admin is true', () => {
      const user = buildUser({ admin: true });
      const fixture = setupEditUserDataRowTest({ user });
      const component = fixture.componentInstance;

      component.onAdminCheckboxChange();

      const p = component.user().permissions;
      expect(p.canUpload).toBe(true);
      expect(p.canDownload).toBe(true);
      expect(p.canDeleteBook).toBe(true);
      expect(p.canEditMetadata).toBe(true);
      expect(p.canManageLibrary).toBe(true);
      expect(p.canEmailBook).toBe(true);
      expect(p.canSyncKoReader).toBe(true);
      expect(p.canSyncKobo).toBe(true);
      expect(p.canAccessOpds).toBe(true);
      expect(p.canBulkEditMetadata).toBe(true);
      expect(p.canMoveOrganizeFiles).toBe(true);
      fixture.destroy();
    });

    it('does not change permissions when admin is false', () => {
      const user = buildUser({ admin: false });
      const fixture = setupEditUserDataRowTest({ user });
      const component = fixture.componentInstance;

      component.onAdminCheckboxChange();

      expect(component.user().permissions.canUpload).toBe(false);
      expect(component.user().permissions.canDownload).toBe(false);
      fixture.destroy();
    });
  });

  it.skip('needs seams to verify two-way binding of permission checkboxes and library multiselect in the DOM', () => {
    // TODO(seam): Cover template bindings once PrimeNG components are testable in this setup.
  });
});