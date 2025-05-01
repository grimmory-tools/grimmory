import {Component, inject} from '@angular/core';
import {Button} from 'primeng/button';
import {Card} from 'primeng/card';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {Message} from 'primeng/message';
import {NgIf} from '@angular/common';
import {Password} from 'primeng/password';
import {MessageService, PrimeTemplate} from 'primeng/api';
import {UserService} from '../../../settings/user-management/user.service';
import {AuthService} from '../../service/auth.service';

@Component({
  selector: 'app-change-password',
  standalone: true,
  imports: [
    Button,
    Card,
    FormsModule,
    Message,
    NgIf,
    Password,
    PrimeTemplate,
    ReactiveFormsModule
  ],
  templateUrl: './change-password.component.html',
  styleUrl: './change-password.component.scss'
})
export class ChangePasswordComponent {
  currentPassword: string = '';
  newPassword: string = '';
  confirmNewPassword: string = '';
  errorMessage: string | null = null;
  successMessage: string | null = null;

  protected userService = inject(UserService);
  protected authService = inject(AuthService);
  protected messageService = inject(MessageService);

  get passwordsMatch(): boolean {
    return this.newPassword === this.confirmNewPassword;
  }

  changePassword() {
    this.errorMessage = null;
    this.successMessage = null;

    if (!this.currentPassword || !this.newPassword || !this.confirmNewPassword) {
      this.errorMessage = 'All fields are required.';
      return;
    }

    if (!this.passwordsMatch) {
      this.errorMessage = 'New passwords do not match.';
      return;
    }

    if (this.currentPassword === this.newPassword) {
      this.errorMessage = 'New password cannot be the same as the current password.';
      return;
    }

    this.userService.changePassword(this.currentPassword, this.newPassword).subscribe({
      next: () => {
        this.successMessage = 'Password changed successfully!';
        this.logout();
      },
      error: (err) => {
        this.errorMessage = err.message;
        this.messageService.add({
          severity: 'error',
          summary: 'Password Change Failed',
          detail: this.errorMessage ?? 'An unknown error occurred.'
        });
      }
    });
  }

  logout() {
    this.authService.logout();
    window.location.href = '/login';
  }
}
