import {Component, inject, OnDestroy, OnInit} from '@angular/core';

import {Button} from 'primeng/button';
import {InputText} from 'primeng/inputtext';
import {API_CONFIG} from '../../../core/config/api-config';
import {Tooltip} from 'primeng/tooltip';
import {TableModule} from 'primeng/table';
import {Dialog} from 'primeng/dialog';
import {FormsModule} from '@angular/forms';
import {ConfirmDialog} from 'primeng/confirmdialog';
import {ConfirmationService, MessageService} from 'primeng/api';
import {OpdsService, OpdsSortOrder, OpdsUserV2, OpdsUserV2CreateRequest} from './opds.service';
import {catchError, filter, take, takeUntil, tap} from 'rxjs/operators';
import {UserService} from '../user-management/user.service';
import {of, Subject} from 'rxjs';
import {Password} from 'primeng/password';
import {ToggleSwitch} from 'primeng/toggleswitch';
import {AppSettingsService} from '../../../shared/service/app-settings.service';
import {AppSettingKey} from '../../../shared/model/app-settings.model';
import {ExternalDocLinkComponent} from '../../../shared/components/external-doc-link/external-doc-link.component';
import {Select} from 'primeng/select';

@Component({
  selector: 'app-opds-settings',
  imports: [
    Button,
    InputText,
    Tooltip,
    Dialog,
    FormsModule,
    ConfirmDialog,
    TableModule,
    Password,
    ToggleSwitch,
    ExternalDocLinkComponent,
    Select
],
  providers: [ConfirmationService],
  templateUrl: './opds-settings.html',
  styleUrl: './opds-settings.scss'
})
export class OpdsSettings implements OnInit, OnDestroy {

  opdsEndpoint = `${API_CONFIG.BASE_URL}/api/v1/opds`;
  komgaEndpoint = `${API_CONFIG.BASE_URL}/komga`;
  opdsEnabled = false;
  komgaApiEnabled = false;
  komgaGroupUnknown = true;

  private opdsService = inject(OpdsService);
  private confirmationService = inject(ConfirmationService);
  private messageService = inject(MessageService);
  private userService = inject(UserService);
  private appSettingsService = inject(AppSettingsService);

  users: OpdsUserV2[] = [];
  loading = false;
  showCreateUserDialog = false;
  newUser: OpdsUserV2CreateRequest = {username: '', password: '', sortOrder: 'RECENT'};
  passwordVisibility: boolean[] = [];
  hasPermission = false;

  editingUserId: number | null = null;
  editingSortOrder: OpdsSortOrder | null = null;

  private readonly destroy$ = new Subject<void>();
  dummyPassword: string = "***********************";

  sortOrderOptions = [
    { label: 'Recently Added', value: 'RECENT' as OpdsSortOrder },
    { label: 'Title (A-Z)', value: 'TITLE_ASC' as OpdsSortOrder },
    { label: 'Title (Z-A)', value: 'TITLE_DESC' as OpdsSortOrder },
    { label: 'Author (A-Z)', value: 'AUTHOR_ASC' as OpdsSortOrder },
    { label: 'Author (Z-A)', value: 'AUTHOR_DESC' as OpdsSortOrder },
    { label: 'Series (A-Z)', value: 'SERIES_ASC' as OpdsSortOrder },
    { label: 'Series (Z-A)', value: 'SERIES_DESC' as OpdsSortOrder },
    { label: 'Rating (Low to High)', value: 'RATING_ASC' as OpdsSortOrder },
    { label: 'Rating (High to Low)', value: 'RATING_DESC' as OpdsSortOrder }
  ];

  ngOnInit(): void {
    this.loading = true;

    let prevHasPermission = false;
    this.userService.userState$.pipe(
      filter(state => !!state?.user && state.loaded),
      takeUntil(this.destroy$),
      tap(state => {
        this.hasPermission = !!(state.user?.permissions.canAccessOpds || state.user?.permissions.admin);
      }),
      filter(() => {
        const shouldRun = this.hasPermission && !prevHasPermission;
        prevHasPermission = this.hasPermission;
        return shouldRun;
      }),
      tap(() => this.loadAppSettings())
    ).subscribe();
  }

  private loadAppSettings(): void {
    this.appSettingsService.appSettings$
      .pipe(
        filter((settings): settings is NonNullable<typeof settings> => settings != null),
        take(1)
      )
      .subscribe(settings => {
        this.opdsEnabled = settings.opdsServerEnabled ?? false;
        this.komgaApiEnabled = settings.komgaApiEnabled ?? false;
        this.komgaGroupUnknown = settings.komgaGroupUnknown ?? true;
        if (this.opdsEnabled || this.komgaApiEnabled) {
          this.loadUsers();
        } else {
          this.loading = false;
        }
      });
  }

  private loadUsers(): void {
    this.opdsService.getUser().pipe(
      takeUntil(this.destroy$),
      catchError(err => {
        console.error('Error loading users:', err);
        this.showMessage('error', 'Error', 'Failed to load users');
        return of([]);
      })
    ).subscribe(users => {
      this.users = users;
      this.passwordVisibility = new Array(users.length).fill(false);
      this.loading = false;
    });
  }

  createUser(): void {
    if (!this.newUser.username || !this.newUser.password) return;

    this.opdsService.createUser(this.newUser).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: user => {
        this.users.push(user);
        this.resetCreateUserDialog();
        this.showMessage('success', 'Success', 'User created successfully');
      },
      error: err => {
        console.error('Error creating user:', err);
        const message = err?.error?.message || 'Failed to create user';
        this.showMessage('error', 'Error', message);
      }
    });
  }

  confirmDelete(user: OpdsUserV2): void {
    this.confirmationService.confirm({
      message: `Are you sure you want to delete user "${user.username}"?`,
      header: 'Delete Confirmation',
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => this.deleteUser(user)
    });
  }

  deleteUser(user: OpdsUserV2): void {
    if (!user.id) return;

    this.opdsService.deleteCredential(user.id).pipe(
      takeUntil(this.destroy$),
      catchError(err => {
        console.error('Error deleting user:', err);
        this.showMessage('error', 'Error', 'Failed to delete user');
        return of(null);
      })
    ).subscribe(() => {
      this.users = this.users.filter(u => u.id !== user.id);
      this.showMessage('success', 'Success', 'User deleted successfully');
    });
  }

  cancelCreateUser(): void {
    this.resetCreateUserDialog();
  }

  copyEndpoint(): void {
    navigator.clipboard.writeText(this.opdsEndpoint).then(() => {
      this.showMessage('success', 'Copied', 'OPDS endpoint copied to clipboard');
    });
  }

  toggleOpdsServer(): void {
    this.saveSetting(AppSettingKey.OPDS_SERVER_ENABLED, this.opdsEnabled);
    if (this.opdsEnabled || this.komgaApiEnabled) {
      this.loadUsers();
    } else {
      this.users = [];
    }
  }

  toggleKomgaApi(): void {
    this.saveKomgaSetting(AppSettingKey.KOMGA_API_ENABLED, this.komgaApiEnabled);
    if (this.opdsEnabled || this.komgaApiEnabled) {
      this.loadUsers();
    } else {
      this.users = [];
    }
  }

  copyKomgaEndpoint(): void {
    navigator.clipboard.writeText(this.komgaEndpoint).then(() => {
      this.showMessage('success', 'Copied', 'Komga API endpoint copied to clipboard');
    });
  }

  toggleKomgaGroupUnknown(): void {
    this.appSettingsService.saveSettings([{key: AppSettingKey.KOMGA_GROUP_UNKNOWN, newValue: this.komgaGroupUnknown}]).subscribe({
      next: () => {
        const successMessage = (this.komgaGroupUnknown === true)
          ? 'Books without series will be grouped under "Unknown Series".'
          : 'Books without series will appear as individual series.';
        this.showMessage('success', 'Settings Saved', successMessage);
      },
      error: () => {
        this.showMessage('error', 'Error', 'There was an error saving the settings.');
      }
    });
  }

  private saveSetting(key: string, value: unknown): void {
    this.appSettingsService.saveSettings([{key, newValue: value}]).subscribe({
      next: () => {
        const successMessage = (value === true)
          ? 'OPDS Server Enabled.'
          : 'OPDS Server Disabled.';
        this.showMessage('success', 'Settings Saved', successMessage);
      },
      error: () => {
        this.showMessage('error', 'Error', 'There was an error saving the settings.');
      }
    });
  }

  private saveKomgaSetting(key: string, value: unknown): void {
    this.appSettingsService.saveSettings([{key, newValue: value}]).subscribe({
      next: () => {
        const successMessage = (value === true)
          ? 'Komga API Enabled.'
          : 'Komga API Disabled.';
        this.showMessage('success', 'Settings Saved', successMessage);
      },
      error: () => {
        this.showMessage('error', 'Error', 'There was an error saving the settings.');
      }
    });
  }

  private resetCreateUserDialog(): void {
    this.showCreateUserDialog = false;
    this.newUser = {username: '', password: '', sortOrder: 'RECENT'};
  }

  private showMessage(severity: string, summary: string, detail: string): void {
    this.messageService.add({severity, summary, detail});
  }

  getSortOrderLabel(sortOrder?: OpdsSortOrder): string {
    if (!sortOrder) return 'Recently Added';
    const option = this.sortOrderOptions.find(o => o.value === sortOrder);
    return option ? option.label : 'Recently Added';
  }

  startEdit(user: OpdsUserV2): void {
    this.editingUserId = user.id;
    this.editingSortOrder = user.sortOrder || 'RECENT';
  }

  cancelEdit(): void {
    this.editingUserId = null;
    this.editingSortOrder = null;
  }

  saveSortOrder(user: OpdsUserV2): void {
    if (!this.editingSortOrder || !user.id) return;

    this.opdsService.updateUser(user.id, this.editingSortOrder).pipe(
      takeUntil(this.destroy$),
      catchError(err => {
        console.error('Error updating sort order:', err);
        this.showMessage('error', 'Error', 'Failed to update sort order');
        return of(null);
      })
    ).subscribe(updatedUser => {
      if (updatedUser) {
        const index = this.users.findIndex(u => u.id === user.id);
        if (index !== -1) {
          this.users[index] = updatedUser;
        }
        this.showMessage('success', 'Success', 'Sort order updated successfully');
      }
      this.cancelEdit();
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}