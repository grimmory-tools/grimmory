import {Component, inject} from '@angular/core';
import {Button} from 'primeng/button';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {Message} from 'primeng/message';

import {Password} from 'primeng/password';
import {MessageService} from 'primeng/api';
import {UserService} from '../../../features/settings/user-management/user.service';
import {AuthService} from '../../service/auth.service';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

@Component({
  selector: 'app-change-password',
  standalone: true,
  imports: [
    Button,
    FormsModule,
    Message,
    Password,
    ReactiveFormsModule,
    TranslocoDirective
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
  private readonly t = inject(TranslocoService);

  get passwordsMatch(): boolean {
    return this.newPassword === this.confirmNewPassword;
  }

  changePassword() {
    this.errorMessage = null;
    this.successMessage = null;

    if (!this.currentPassword || !this.newPassword || !this.confirmNewPassword) {
      this.errorMessage = this.t.translate('shared.changePassword.validation.allFieldsRequired');
      return;
    }

    if (!this.passwordsMatch) {
      this.errorMessage = this.t.translate('shared.changePassword.validation.passwordsDoNotMatch');
      return;
    }

    if (this.currentPassword === this.newPassword) {
      this.errorMessage = this.t.translate('shared.changePassword.validation.sameAsCurrentPassword');
      return;
    }

    this.userService.changePassword(this.currentPassword, this.newPassword).subscribe({
      next: () => {
        this.successMessage = this.t.translate('shared.changePassword.toast.success');
        this.logout();
      },
      error: (err) => {
        this.errorMessage = err.message;
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('shared.changePassword.toast.failedSummary'),
          detail: this.errorMessage ?? this.t.translate('shared.changePassword.toast.failedDetailDefault')
        });
      }
    });
  }

  logout() {
    this.authService.logout();
  }
}
