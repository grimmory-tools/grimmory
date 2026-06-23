import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { describe, expect, it, beforeEach, vi } from 'vitest';

import { EditUserDataRowComponent } from './edit-user-data-row.component';
import { LibraryService } from '../../../book/service/library.service';
import { TranslocoService } from '@jsverse/transloco';
import { UserWithEditing } from '../user-management.component';
import { User } from '../user.service';

function createUser(
  overrides: Partial<UserWithEditing> = {}
): UserWithEditing {
  return {
    id: 1,
    name: 'Test User',
    email: 'test@test.com',
    username: 'test',
    locale: 'en',
    theme: 'grimmory',
    themeAccent: null,
    themeSyncEnabled: false,

    userSettings: {} as User['userSettings'],

    permissions: {
      admin: false,
      ...(overrides.permissions ?? {}),
    } as User['permissions'],

    assignedLibraries: [],
    selectedLibraryIds: [1, 2],
    libraryNames: '',
    isEditing: true,

    ...overrides,
  };
}

describe('EditUserDataRowComponent', () => {
  let fixture: ComponentFixture<EditUserDataRowComponent>;
  let component: EditUserDataRowComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EditUserDataRowComponent],
      providers: [
        {
          provide: LibraryService,
          useValue: {
            libraries: signal([
              { id: 1, name: 'Lib 1' },
              { id: 2, name: 'Lib 2' },
            ]),
          },
        },
        {
          provide: TranslocoService,
          useValue: {
            translate: (key: string) => key,
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(EditUserDataRowComponent);
    component = fixture.componentInstance;

    (component as any).user = signal(createUser());

    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should sync localLibraryIds from input user', () => {
    expect(component.localLibraryIds).toEqual([1, 2]);
  });

  it('should emit libraryIdsChange', () => {
    const spy = vi.fn();
    component.libraryIdsChange.subscribe(spy);

    component.localLibraryIds = [3, 4];
    component.onLibraryIdsChange();

    expect(spy).toHaveBeenCalledWith([3, 4]);
  });

  it('should disable permissions when user is admin', () => {
    (component as any).user = signal(
      createUser({
        permissions: { admin: true } as any,
      })
    );

    expect(component.isPermissionDisabled).toBe(true);
  });

  it('should enable admin permissions when admin checkbox is triggered', () => {
    const user = createUser({
      permissions: { admin: true } as any,
    });

    (component as any).user = signal(user);

    component.onAdminCheckboxChange();

    expect(user.permissions.canUpload).toBe(true);
    expect(user.permissions.canManageLibrary).toBe(true);
    expect(user.permissions.canBulkResetBookReadStatus).toBe(true);
  });
});