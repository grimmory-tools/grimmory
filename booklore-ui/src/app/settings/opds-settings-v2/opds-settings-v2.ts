import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Button} from 'primeng/button';
import {InputText} from 'primeng/inputtext';
import {API_CONFIG} from '../../config/api-config';
import {Tooltip} from 'primeng/tooltip';
import {TableModule} from 'primeng/table';
import {Dialog} from 'primeng/dialog';
import {FormsModule} from '@angular/forms';
import {ConfirmDialog} from 'primeng/confirmdialog';
import {ConfirmationService, MessageService} from 'primeng/api';
import {OpdsUserV2, OpdsUserV2CreateRequest, OpdsV2Service} from './opds-v2.service';
import {catchError, filter, take, takeUntil, tap} from 'rxjs/operators';
import {UserService} from '../user-management/user.service';
import {of, Subject} from 'rxjs';
import {Password} from 'primeng/password';
import {ToggleSwitch} from 'primeng/toggleswitch';
import {AppSettingsService} from '../../core/service/app-settings.service';
import {AppSettingKey} from '../../core/model/app-settings.model';
import {ExternalDocLinkComponent} from '../../shared/components/external-doc-link/external-doc-link.component';

@Component({
  selector: 'app-opds-settings-v2',
  imports: [
    CommonModule,
    Button,
    InputText,
    Tooltip,
    Dialog,
    FormsModule,
    ConfirmDialog,
    TableModule,
    Password,
    ToggleSwitch,
    ExternalDocLinkComponent
  ],
  providers: [ConfirmationService],
  templateUrl: './opds-settings-v2.html',
  styleUrl: './opds-settings-v2.scss'
})
export class OpdsSettingsV2 implements OnInit, OnDestroy {

  opdsEndpoint = `${API_CONFIG.BASE_URL}/api/v2/opds/catalog`;
  opdsV1Endpoint = `${API_CONFIG.BASE_URL}/api/v2/opds/catalog`;
  opdsV2Endpoint = `${API_CONFIG.BASE_URL}/api/v2/opds`;
  opdsEnabled = false;

  private opdsService = inject(OpdsV2Service);
  private confirmationService = inject(ConfirmationService);
  private messageService = inject(MessageService);
  private userService = inject(UserService);
  private appSettingsService = inject(AppSettingsService);

  users: OpdsUserV2[] = [];
  loading = false;
  showCreateUserDialog = false;
  newUser: OpdsUserV2CreateRequest = {username: '', password: ''};
  passwordVisibility: boolean[] = [];
  hasPermission = false;

  private readonly destroy$ = new Subject<void>();
  dummyPassword: string = "***********************";

  ngOnInit(): void {
    this.loading = true;

    this.userService.userState$.pipe(
      filter(state => !!state?.user && state.loaded),
      takeUntil(this.destroy$),
      tap(state => {
        this.hasPermission = !!(state.user?.permissions.canAccessOpds || state.user?.permissions.admin);
      }),
      filter(() => this.hasPermission),
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
        if (this.opdsEnabled) {
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

  copyV1Endpoint(): void {
    navigator.clipboard.writeText(this.opdsV1Endpoint).then(() => {
      this.showMessage('success', 'Copied', 'OPDS v1 endpoint copied to clipboard');
    });
  }

  copyV2Endpoint(): void {
    navigator.clipboard.writeText(this.opdsV2Endpoint).then(() => {
      this.showMessage('success', 'Copied', 'OPDS v2 endpoint copied to clipboard');
    });
  }

  toggleOpdsServer(): void {
    this.saveSetting(AppSettingKey.OPDS_SERVER_ENABLED, this.opdsEnabled);
    if (this.opdsEnabled) {
      this.loadUsers();
    } else {
      this.users = [];
    }
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

  private resetCreateUserDialog(): void {
    this.showCreateUserDialog = false;
    this.newUser = {username: '', password: ''};
  }

  private showMessage(severity: string, summary: string, detail: string): void {
    this.messageService.add({severity, summary, detail});
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
