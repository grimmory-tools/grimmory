import {Component, DestroyRef, inject, OnInit, signal, WritableSignal} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {FormsModule} from '@angular/forms';
import {Button} from 'primeng/button';
import {DynamicDialogRef} from 'primeng/dynamicdialog';
import {TableModule} from 'primeng/table';
import {LowerCasePipe, TitleCasePipe} from '@angular/common';
import {User, UserService, UserUpdateRequest} from './user.service';
import {MessageService} from 'primeng/api';
import {Library} from '../../book/model/library.model';
import {LibraryService} from '../../book/service/library.service';
import {Dialog} from 'primeng/dialog';
import {Password} from 'primeng/password';
import {Tooltip} from 'primeng/tooltip';
import {DialogLauncherService} from '../../../shared/services/dialog-launcher.service';
import {TranslocoDirective, TranslocoPipe, TranslocoService} from '@jsverse/transloco';
import { EditUserDataRowComponent } from './edit-user-data-row/edit-user-data-row.component';

export interface UserWithEditing extends User {
  isEditing?: boolean;
  selectedLibraryIds?: number[];
  libraryNames?: string;
}

@Component({
  selector: 'app-user-management',
  imports: [
    FormsModule,
    Button,
    TableModule,
    Dialog,
    Password,
    LowerCasePipe,
    TitleCasePipe,
    Tooltip,
    TranslocoDirective,
    TranslocoPipe,
    EditUserDataRowComponent,
  ],
  templateUrl: './user-management.component.html',
  styleUrls: ['./user-management.component.scss'],
})
export class UserManagementComponent implements OnInit {
  ref: DynamicDialogRef | undefined | null;
  private dialogLauncherService = inject(DialogLauncherService);
  private userService = inject(UserService);
  private libraryService = inject(LibraryService);
  private messageService = inject(MessageService);
  private t = inject(TranslocoService);
  private destroyRef = inject(DestroyRef);
  get allLibraries() { return this.libraryService.libraries(); }

  users: WritableSignal<UserWithEditing[]> = signal([]);
  currentUser: User | null = null;
  expandedRows: Record<string, boolean> = {};

  isPasswordDialogVisible = false;
  selectedUser: User | null = null;
  newPassword = '';
  confirmNewPassword = '';
  passwordError = '';
  isAdmin = false;

  ngOnInit() {
    this.loadUsers();

    const user = this.userService.currentUser();
    if (user) {
      this.currentUser = user;
      this.isAdmin = user.permissions?.admin || false;
    }
  }

  loadUsers() {
    this.userService.getUsers().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (data) => {
        this.users.set(data.map((user: UserWithEditing) => ({
          ...user,
          isEditing: false,
          selectedLibraryIds: (user.assignedLibraries ?? [])
            .map((lib) => lib.id)
            .filter((id): id is number => id !== undefined),
          libraryNames: user.assignedLibraries?.map((lib) => lib.name).join(', ') || '',
        })));
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('common.error'),
          detail: this.t.translate('settingsUsers.fetchError'),
        });
      },
    });
  }

  async openCreateUserDialog() {
    this.ref = await this.dialogLauncherService.openCreateUserDialog().catch(() => null);
    this.ref?.onClose.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((result) => {
      if (result) {
        this.loadUsers();
      }
    });
  }

  toggleEdit(user: UserWithEditing) {
    user.isEditing = !user.isEditing;
    if (!user.isEditing) {
      user.libraryNames =
        user.assignedLibraries
          ?.map((lib: Library) => lib.name)
          .join(', ') || '';
    }
  }

  onLibraryIdsChange(user: UserWithEditing, ids: number[]) {
    user.selectedLibraryIds = ids;
  }

  saveUser(user: UserWithEditing) {
    const updateRequest: UserUpdateRequest = {
      name: user.name,
      email: user.email,
      permissions: user.permissions,
      assignedLibraries: user.selectedLibraryIds || [],
    };
    this.userService
      .updateUser(user.id, updateRequest)
      .subscribe({
        next: () => {
          user.isEditing = false;
          this.loadUsers();
          this.messageService.add({
            severity: 'success',
            summary: this.t.translate('common.success'),
            detail: this.t.translate('settingsUsers.updateSuccess'),
          });
        },
        error: () => {
          this.messageService.add({
            severity: 'error',
            summary: this.t.translate('common.error'),
            detail: this.t.translate('settingsUsers.updateError'),
          });
        },
      });
  }

  deleteUser(user: User) {
    if (confirm(this.t.translate('settingsUsers.deleteConfirm', {username: user.username}))) {
      this.userService.deleteUser(user.id).subscribe({
        next: () => {
          this.messageService.add({
            severity: 'success',
            summary: this.t.translate('common.success'),
            detail: this.t.translate('settingsUsers.deleteSuccess', {username: user.username}),
          });
          this.loadUsers();
        },
        error: (err) => {
          this.messageService.add({
            severity: 'error',
            summary: this.t.translate('common.error'),
            detail:
              err.error?.message ||
              this.t.translate('settingsUsers.deleteError', {username: user.username}),
          });
        },
      });
    }
  }

  openChangePasswordDialog(user: User) {
    this.selectedUser = user;
    this.newPassword = '';
    this.confirmNewPassword = '';
    this.passwordError = '';
    this.isPasswordDialogVisible = true;
  }

  submitPasswordChange() {
    if (!this.newPassword || !this.confirmNewPassword) {
      this.passwordError = this.t.translate('settingsUsers.passwordDialog.bothRequired');
      return;
    }

    if (this.newPassword !== this.confirmNewPassword) {
      this.passwordError = this.t.translate('settingsUsers.passwordDialog.mismatch');
      return;
    }

    if (this.selectedUser) {
      this.userService
        .changeUserPassword(this.selectedUser.id, this.newPassword)
        .subscribe({
          next: () => {
            this.messageService.add({
              severity: 'success',
              summary: this.t.translate('common.success'),
              detail: this.t.translate('settingsUsers.passwordDialog.success'),
            });
            this.isPasswordDialogVisible = false;
          },
          error: (err) => {
            this.passwordError = err;
          }
        });
    }
  }

  getBookManagementPermissionsCount(user: User): number {
    const p = user.permissions;
    return [p.canUpload, p.canDownload, p.canDeleteBook, p.canManageLibrary, p.canEmailBook]
      .filter(Boolean).length;
  }

  getDeviceSyncPermissionsCount(user: User): number {
    const p = user.permissions;
    return [p.canSyncKoReader, p.canSyncKobo, p.canAccessOpds].filter(Boolean).length;
  }

  getSystemAccessPermissionsCount(user: User): number {
    const p = user.permissions;
    return [p.canAccessBookdrop, p.canAccessLibraryStats, p.canAccessUserStats].filter(Boolean).length;
  }

  getSystemConfigPermissionsCount(user: User): number {
    const p = user.permissions;
    return [p.canAccessTaskManager, p.canManageGlobalPreferences, p.canManageMetadataConfig, p.canManageIcons, p.canManageFonts]
      .filter(Boolean).length;
  }

  getMetadataEditingPermissionsCount(user: User): number {
    const p = user.permissions;
    return [p.canEditMetadata, p.canBulkAutoFetchMetadata, p.canBulkCustomFetchMetadata,
            p.canBulkEditMetadata, p.canBulkRegenerateCover, p.canMoveOrganizeFiles, p.canBulkLockUnlockMetadata]
      .filter(Boolean).length;
  }

  getBulkResetPermissionsCount(user: User): number {
    const p = user.permissions;
    return [(p.canBulkResetGrimmoryReadProgress ?? p.canBulkResetBookloreReadProgress),
            p.canBulkResetKoReaderReadProgress, p.canBulkResetBookReadStatus]
      .filter(Boolean).length;
  }

  getPermissionLevel(count: number, total: number): string {
    if (count === 0) return 'none';
    const ratio = count / total;
    if (ratio < 0.4) return 'low';
    if (ratio < 0.8) return 'medium';
    return 'high';
  }

  toggleRowExpansion(user: User) {
    if (this.expandedRows[user.id]) {
      delete this.expandedRows[user.id];
    } else {
      this.expandedRows[user.id] = true;
    }
  }

  isRowExpanded(user: User): boolean {
    return this.expandedRows[user.id];
  }

  editUserBtnEnabled(user: UserWithEditing): boolean {
    return !((user.selectedLibraryIds?.length === 0) && !user.permissions.admin);
  }
}
