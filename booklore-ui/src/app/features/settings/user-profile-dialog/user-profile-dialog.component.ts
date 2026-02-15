import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {Button} from 'primeng/button';
import {AbstractControl, FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators} from '@angular/forms';
import {InputText} from 'primeng/inputtext';
import {Password} from 'primeng/password';
import {User, UserService, UserUpdateRequest} from '../user-management/user.service';
import {MessageService} from 'primeng/api';
import {Subject} from 'rxjs';
import {filter, takeUntil} from 'rxjs/operators';
import {DynamicDialogRef} from 'primeng/dynamicdialog';
import {TranslocoDirective, TranslocoPipe, TranslocoService} from '@jsverse/transloco';

export const passwordMatchValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const newPassword = control.get('newPassword');
  const confirmNewPassword = control.get('confirmNewPassword');

  if (!newPassword || !confirmNewPassword) {
    return null;
  }

  return newPassword.value === confirmNewPassword.value ? null : {passwordMismatch: true};
};

@Component({
  selector: 'app-user-profile-dialog',
  imports: [
    Button,
    FormsModule,
    ReactiveFormsModule,
    InputText,
    Password,
    TranslocoDirective,
    TranslocoPipe
  ],
  templateUrl: './user-profile-dialog.component.html',
  styleUrls: ['./user-profile-dialog.component.scss']
})
export class UserProfileDialogComponent implements OnInit, OnDestroy {

  isEditing = false;
  currentUser: User | null = null;
  editUserData: Partial<User> = {};
  private readonly destroy$ = new Subject<void>();

  changePasswordForm: FormGroup;

  protected readonly userService = inject(UserService);
  private readonly messageService = inject(MessageService);
  private readonly fb = inject(FormBuilder);
  private readonly dialogRef = inject(DynamicDialogRef);
  private readonly t = inject(TranslocoService);

  constructor() {
    this.changePasswordForm = this.fb.group(
      {
        currentPassword: ['', Validators.required],
        newPassword: ['', [Validators.required, Validators.minLength(6)]],
        confirmNewPassword: ['', Validators.required]
      },
      {validators: passwordMatchValidator}
    );
  }

  ngOnInit(): void {
    this.userService.userState$.pipe(
      filter(userState => !!userState?.user && userState.loaded),
      takeUntil(this.destroy$)
    ).subscribe(userState => {
      this.currentUser = userState.user;
      this.resetEditForm();
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  toggleEdit(): void {
    this.isEditing = !this.isEditing;
    if (this.isEditing) {
      this.resetEditForm();
    }
  }

  resetEditForm(): void {
    if (this.currentUser) {
      this.editUserData = {
        username: this.currentUser.username,
        name: this.currentUser.name,
        email: this.currentUser.email,
      };
    }
  }

  updateProfile(): void {
    if (!this.currentUser) {
      this.messageService.add({
        severity: 'error',
        summary: this.t.translate('common.error'),
        detail: this.t.translate('settingsProfile.toast.errorNoUser'),
      });
      return;
    }

    if (this.editUserData.name === this.currentUser.name && this.editUserData.email === this.currentUser.email) {
      this.messageService.add({severity: 'info', summary: this.t.translate('common.info'), detail: this.t.translate('settingsProfile.toast.noChanges')});
      this.isEditing = false;
      return;
    }

    const updateRequest: UserUpdateRequest = {
      name: this.editUserData.name,
      email: this.editUserData.email,
    };
    this.userService.updateUser(this.currentUser.id, updateRequest).subscribe({
      next: () => {
        this.messageService.add({severity: 'success', summary: this.t.translate('common.success'), detail: this.t.translate('settingsProfile.toast.profileUpdated')});
        this.isEditing = false;
      },
      error: (err) => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('common.error'),
          detail: err.error?.message || this.t.translate('settingsProfile.toast.profileUpdateFailed'),
        });
      },
    });
  }

  submitPasswordChange(): void {
    if (this.changePasswordForm.invalid) {
      this.changePasswordForm.markAllAsTouched();
      return;
    }

    const {currentPassword, newPassword} = this.changePasswordForm.value;

    this.userService.changePassword(currentPassword, newPassword).subscribe({
      next: () => {
        this.messageService.add({severity: 'success', summary: this.t.translate('common.success'), detail: this.t.translate('settingsProfile.toast.passwordChanged')});
        this.resetPasswordForm();
      },
      error: (err) => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('common.error'),
          detail: err?.message || this.t.translate('settingsProfile.toast.passwordChangeFailed'),
        });
      }
    });
  }

  resetPasswordForm(): void {
    this.changePasswordForm.reset();
  }

  closeDialog(): void {
    this.dialogRef.close();
  }
}
