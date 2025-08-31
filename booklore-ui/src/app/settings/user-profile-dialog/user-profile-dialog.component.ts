import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {Button} from 'primeng/button';
import {Divider} from 'primeng/divider';
import {FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators} from '@angular/forms';
import {InputText} from 'primeng/inputtext';

import {Password} from 'primeng/password';
import {User, UserService} from '../user-management/user.service';
import {MessageService} from 'primeng/api';
import {Subject} from 'rxjs';
import {Message} from 'primeng/message';
import {filter, takeUntil} from 'rxjs/operators';

@Component({
  selector: 'app-user-profile-dialog',
  imports: [
    Button,
    Divider,
    FormsModule,
    ReactiveFormsModule,
    InputText,
    Password,
    Message
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

  constructor() {
    this.changePasswordForm = this.fb.group(
      {
        currentPassword: ['', Validators.required],
        newPassword: ['', [Validators.required, Validators.minLength(6)]],
        confirmNewPassword: ['', [Validators.required, Validators.minLength(6)]]
      },
      {
        validators: [UserProfileDialogComponent.passwordsMatchValidator]
      }
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

  static passwordsMatchValidator(form: FormGroup) {
    const newPassword = form.get('newPassword')?.value;
    const confirmPassword = form.get('confirmNewPassword')?.value;
    return newPassword === confirmPassword ? null : {mismatch: true};
  }

  get passwordsMismatch() {
    return this.changePasswordForm.hasError('mismatch') &&
      this.changePasswordForm.get('confirmNewPassword')?.touched;
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
        summary: 'Error',
        detail: 'User data not available.',
      });
      return;
    }

    if (this.editUserData.name === this.currentUser.name && this.editUserData.email === this.currentUser.email) {
      this.messageService.add({severity: 'info', summary: 'Info', detail: 'No changes detected.'});
      this.isEditing = false;
      return;
    }

    this.userService.updateUser(this.currentUser.id, this.editUserData).subscribe({
      next: () => {
        this.messageService.add({severity: 'success', summary: 'Success', detail: 'Profile updated successfully'});
        this.isEditing = false;
        this.resetEditForm();
      },
      error: (err) => {
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: err.error?.message || 'Failed to update profile',
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
        this.messageService.add({severity: 'success', summary: 'Success', detail: 'Password changed successfully'});
        this.resetPasswordForm();
      },
      error: (err) => {
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: err?.message || 'Failed to change password',
        });
      }
    });
  }

  resetPasswordForm(): void {
    this.changePasswordForm.reset();
  }
}
