import {Component, inject, OnInit} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {NgIf} from '@angular/common';
import {TableModule} from 'primeng/table';
import {Button} from 'primeng/button';
import {InputText} from 'primeng/inputtext';
import {Dialog} from 'primeng/dialog';
import {Tooltip} from 'primeng/tooltip';
import {MessageService} from 'primeng/api';
import {filter, take} from 'rxjs/operators';

import {OpdsUser, OpdsUserService} from './opds-user.service';
import {AppSettingsService} from '../../../core/service/app-settings.service';
import {AppSettings} from '../../../core/model/app-settings.model';
import {Password} from 'primeng/password';
import {API_CONFIG} from '../../../config/api-config';
import {ToggleSwitch} from 'primeng/toggleswitch';

@Component({
  selector: 'app-opds-settings',
  standalone: true,
  imports: [
    FormsModule,
    NgIf,
    TableModule,
    Button,
    InputText,
    Dialog,
    Tooltip,
    Password,
    ToggleSwitch
  ],
  templateUrl: './opds-settings.component.html',
  styleUrl: './opds-settings.component.scss'
})
export class OpdsSettingsComponent implements OnInit {
  opdsEnabled = false;
  users: OpdsUser[] = [];
  selectedUser: OpdsUser | null = null;
  createUserDialogVisible = false;
  resetPasswordDialogVisible = false;
  newUser = {username: '', password: ''};
  newPassword = '';
  opdsEndpoint: string = `${API_CONFIG.BASE_URL}/api/v1/opds/catalog`;
  dummyPassword: string = '********************************';

  private readonly appSettings$ = inject(AppSettingsService).appSettings$;
  private readonly opdsUserService = inject(OpdsUserService);
  private readonly messageService = inject(MessageService);
  private readonly appSettingsService = inject(AppSettingsService);

  ngOnInit(): void {
    this.appSettings$
      .pipe(
        filter((settings): settings is AppSettings => settings != null),
        take(1)
      )
      .subscribe(settings => {
        this.opdsEnabled = settings.opdsServerEnabled ?? false;
        if (this.opdsEnabled) {
          this.loadUsers();
        }
      });
  }

  toggleOpdsServer(): void {
    this.saveSetting('opds_server_enabled', this.opdsEnabled);
    if (this.opdsEnabled) {
      this.loadUsers();
    } else {
      this.users = [];
    }
  }

  loadUsers(): void {
    this.opdsUserService.getUsers().subscribe({
      next: (users) => this.users = users,
      error: () => this.showError('Load Users Failed', 'Unable to fetch users.')
    });
  }

  openCreateUserDialog(): void {
    this.newUser = {username: '', password: ''};
    this.createUserDialogVisible = true;
  }

  cancelCreateUser(): void {
    this.createUserDialogVisible = false;
  }

  saveNewUser(): void {
    const {username, password} = this.newUser;

    if (username.trim() && password.trim()) {
      this.opdsUserService.createUser(this.newUser).subscribe({
        next: () => {
          this.createUserDialogVisible = false;
          this.loadUsers();
          this.showSuccess('User Created', 'New OPDS user created successfully.');
        },
        error: (err) => {
          const errorMessage = err?.error?.message || 'Unable to create user.';
          this.showError('Create User Failed', errorMessage);
        }
      });
    } else {
      this.showWarning('Invalid Input', 'Username and Password are required.');
    }
  }

  openResetPasswordDialog(user: OpdsUser): void {
    this.selectedUser = user;
    this.newPassword = '';
    this.resetPasswordDialogVisible = true;
  }

  cancelResetPassword(): void {
    this.resetPasswordDialogVisible = false;
    this.selectedUser = null;
    this.newPassword = '';
  }

  copyOpdsEndpoint() {
    navigator.clipboard.writeText(this.opdsEndpoint)
      .then(() => {
      })
      .catch(err => {
      });
  }

  confirmResetPassword(): void {
    if (this.selectedUser && this.newPassword.trim()) {
      this.opdsUserService.resetPassword(this.selectedUser.id, this.newPassword).subscribe({
        next: () => {
          this.resetPasswordDialogVisible = false;
          this.showSuccess('Password Reset', 'User password reset successfully.');
        },
        error: (err) => {
          const errorMessage = err?.error?.message || 'Unable to reset password.';
          this.showError('Reset Password Failed', errorMessage);
        }
      });
    } else {
      this.showWarning('Invalid Input', 'New password is required.');
    }
  }

  deleteUser(userId: number): void {
    this.opdsUserService.deleteUser(userId).subscribe({
      next: () => {
        this.loadUsers();
        this.showSuccess('User Deleted', 'User deleted successfully.');
      },
      error: () => {
        this.showError('Delete User Failed', 'Unable to delete user.');
      }
    });
  }

  private saveSetting(key: string, value: unknown): void {
    this.appSettingsService.saveSettings([{key, newValue: value}]).subscribe({
      next: () => {
        let successMessage = 'Settings saved successfully.';
        if (key === 'opds_server_enabled') {
          successMessage = (value === true)
            ? 'OPDS Server Enabled.'
            : 'OPDS Server Disabled.';
        }
        this.showSuccess('Settings Saved', successMessage);
      },
      error: () => {
        this.showError('Error', 'There was an error saving the settings.');
      }
    });
  }

  private showSuccess(summary: string, detail: string): void {
    this.messageService.add({severity: 'success', summary, detail});
  }

  private showError(summary: string, detail: string): void {
    this.messageService.add({severity: 'error', summary, detail});
  }

  private showWarning(summary: string, detail: string): void {
    this.messageService.add({severity: 'warn', summary, detail});
  }
}
