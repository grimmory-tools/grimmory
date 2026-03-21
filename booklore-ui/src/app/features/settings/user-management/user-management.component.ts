import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {Button} from 'primeng/button';
import {DynamicDialogRef} from 'primeng/dynamicdialog';
import {TableModule} from 'primeng/table';
import {LowerCasePipe, TitleCasePipe} from '@angular/common';
import {User, UserService, UserUpdateRequest} from './user.service';
import {MessageService} from 'primeng/api';
import {Checkbox} from 'primeng/checkbox';
import {MultiSelect} from 'primeng/multiselect';
import {Library} from '../../book/model/library.model';
import {LibraryService} from '../../book/service/library.service';
import {Dialog} from 'primeng/dialog';
import {Password} from 'primeng/password';
import {InputText} from 'primeng/inputtext';
import {filter, take, takeUntil} from 'rxjs/operators';
import {Subject} from 'rxjs';
import {Tooltip} from 'primeng/tooltip';
import {DialogLauncherService} from '../../../shared/services/dialog-launcher.service';
import {ContentRestrictionsEditorComponent} from './content-restrictions-editor/content-restrictions-editor.component';
import {TranslocoDirective, TranslocoPipe, TranslocoService} from '@jsverse/transloco';

interface UserWithEditing extends User {
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
    Checkbox,
    MultiSelect,
    Dialog,
    Password,
    InputText,
    LowerCasePipe,
    TitleCasePipe,
    Tooltip,
    ContentRestrictionsEditorComponent,
    TranslocoDirective,
    TranslocoPipe
  ],
  templateUrl: './user-management.component.html',
  styleUrls: ['./user-management.component.scss'],
})
export class UserManagementComponent implements OnInit, OnDestroy {
  ref: DynamicDialogRef | undefined | null;
  private dialogLauncherService = inject(DialogLauncherService);
  private userService = inject(UserService);
  private libraryService = inject(LibraryService);
  private messageService = inject(MessageService);
  private t = inject(TranslocoService);
  private readonly destroy$ = new Subject<void>();

  users: UserWithEditing[] = [];
  currentUser: User | null = null;
  editingLibraryIds: number[] = [];
  allLibraries: Library[] = [];
  expandedRows: Record<string, boolean> = {};

  isPasswordDialogVisible = false;
  selectedUser: User | null = null;
  newPassword = '';
  confirmNewPassword = '';
  passwordError = '';
  isAdmin = false;

  ngOnInit() {
    this.loadUsers();

    this.userService.userState$
      .pipe(
        filter(userState => !!userState?.user && userState.loaded),
        take(1),
        takeUntil(this.destroy$)
      )
      .subscribe(userState => {
        this.currentUser = userState.user;
        this.isAdmin = userState.user?.permissions?.admin || false;
      });

    this.libraryService.libraryState$
      .pipe(
        filter(state => !!state?.loaded),
        take(1),
        takeUntil(this.destroy$)
      )
      .subscribe(libraries => this.allLibraries = libraries.libraries ?? []);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }


  loadUsers() {
    this.userService.getUsers().subscribe({
      next: (data) => {
        this.users = data.map((user) => ({
          ...user,
          isEditing: false,
          selectedLibraryIds: user.assignedLibraries?.map((lib) => lib.id!).filter(id => id !== undefined) as number[] || [],
          libraryNames:
            user.assignedLibraries?.map((lib) => lib.name).join(', ') || '',
        }));
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

  openCreateUserDialog() {
    this.ref = this.dialogLauncherService.openCreateUserDialog();
    this.ref?.onClose.subscribe((result) => {
      if (result) {
        this.loadUsers();
      }
    });
  }

  toggleEdit(user: UserWithEditing) {
    user.isEditing = !user.isEditing;
    if (user.isEditing) {
      this.editingLibraryIds = [...(user.selectedLibraryIds || [])];
    } else {
      user.libraryNames =
        user.assignedLibraries
          ?.map((lib: Library) => lib.name)
          .join(', ') || '';
    }
  }

  saveUser(user: UserWithEditing) {
    user.selectedLibraryIds = [...this.editingLibraryIds];
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
    const permissions = user.permissions;
    let count = 0;
    if (permissions.canUpload) count++;
    if (permissions.canDownload) count++;
    if (permissions.canDeleteBook) count++;
    if (permissions.canManageLibrary) count++;
    if (permissions.canEmailBook) count++;
    return count;
  }

  getDeviceSyncPermissionsCount(user: User): number {
    const permissions = user.permissions;
    let count = 0;
    if (permissions.canSyncKoReader) count++;
    if (permissions.canSyncKobo) count++;
    if (permissions.canAccessOpds) count++;
    return count;
  }

  getSystemAccessPermissionsCount(user: User): number {
    const permissions = user.permissions;
    let count = 0;
    if (permissions.canAccessBookdrop) count++;
    if (permissions.canAccessLibraryStats) count++;
    if (permissions.canAccessUserStats) count++;
    return count;
  }

  getSystemConfigPermissionsCount(user: User): number {
    const permissions = user.permissions;
    let count = 0;
    if (permissions.canAccessTaskManager) count++;
    if (permissions.canManageGlobalPreferences) count++;
    if (permissions.canManageMetadataConfig) count++;
    if (permissions.canManageIcons) count++;
    if (permissions.canManageFonts) count++;
    return count;
  }

  getMetadataEditingPermissionsCount(user: User): number {
    const permissions = user.permissions;
    let count = 0;
    if (permissions.canEditMetadata) count++;
    if (permissions.canBulkAutoFetchMetadata) count++;
    if (permissions.canBulkCustomFetchMetadata) count++;
    if (permissions.canBulkEditMetadata) count++;
    if (permissions.canBulkRegenerateCover) count++;
    if (permissions.canMoveOrganizeFiles) count++;
    if (permissions.canBulkLockUnlockMetadata) count++;
    return count;
  }

  getBulkResetPermissionsCount(user: User): number {
    const permissions = user.permissions;
    let count = 0;
    if (permissions.canBulkResetGrimmoryReadProgress ?? permissions.canBulkResetBookloreReadProgress) count++;
    if (permissions.canBulkResetKoReaderReadProgress) count++;
    if (permissions.canBulkResetBookReadStatus) count++;
    return count;
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

  onAdminCheckboxChange(user: User) {
    if (user.permissions.admin) {
      user.permissions.canUpload = true;
      user.permissions.canDownload = true;
      user.permissions.canDeleteBook = true;
      user.permissions.canEditMetadata = true;
      user.permissions.canManageLibrary = true;
      user.permissions.canEmailBook = true;
      user.permissions.canSyncKoReader = true;
      user.permissions.canSyncKobo = true;
      user.permissions.canAccessOpds = true;
      user.permissions.canAccessBookdrop = true;
      user.permissions.canAccessLibraryStats = true;
      user.permissions.canAccessUserStats = true;
      user.permissions.canManageMetadataConfig = true;
      user.permissions.canManageGlobalPreferences = true;
      user.permissions.canAccessTaskManager = true;
      user.permissions.canManageEmailConfig = true;
      user.permissions.canManageIcons = true;
      user.permissions.canManageFonts = true;
      user.permissions.canBulkAutoFetchMetadata = true;
      user.permissions.canBulkCustomFetchMetadata = true;
      user.permissions.canBulkEditMetadata = true;
      user.permissions.canBulkRegenerateCover = true;
      user.permissions.canMoveOrganizeFiles = true;
      user.permissions.canBulkLockUnlockMetadata = true;
      user.permissions.canBulkResetGrimmoryReadProgress = true;
      user.permissions.canBulkResetBookloreReadProgress = true;
      user.permissions.canBulkResetKoReaderReadProgress = true;
      user.permissions.canBulkResetBookReadStatus = true;
    }
  }

  isPermissionDisabled(user: UserWithEditing): boolean {
    return !user.isEditing || user.permissions.admin;
  }
}
