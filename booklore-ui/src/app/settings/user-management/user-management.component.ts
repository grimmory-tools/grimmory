import {Component, inject, OnInit} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {Button} from 'primeng/button';
import {DialogService, DynamicDialogRef} from 'primeng/dynamicdialog';
import {CreateUserDialogComponent} from './create-user-dialog/create-user-dialog.component';
import {TableModule} from 'primeng/table';
import {NgIf, NgStyle} from '@angular/common';
import {User, UserService} from './user.service';
import {MessageService} from 'primeng/api';
import {Checkbox} from 'primeng/checkbox';
import {MultiSelect} from 'primeng/multiselect';
import {Library} from '../../book/model/library.model';
import {LibraryService} from '../../book/service/library.service';
import {Dialog} from 'primeng/dialog';
import {Password} from 'primeng/password';
import {Tooltip} from 'primeng/tooltip';

@Component({
  selector: 'app-user-management',
  imports: [
    FormsModule,
    Button,
    TableModule,
    NgIf,
    Checkbox,
    NgStyle,
    MultiSelect,
    Dialog,
    Password,
    Tooltip,
  ],
  templateUrl: './user-management.component.html',
  styleUrls: ['./user-management.component.scss'],
})
export class UserManagementComponent implements OnInit {
  ref: DynamicDialogRef | undefined;
  private dialogService = inject(DialogService);
  private userService = inject(UserService);
  private libraryService = inject(LibraryService);
  private messageService = inject(MessageService);

  users: User[] = [];
  editingLibraryIds: number[] = [];
  allLibraries: Library[] = [];

  isPasswordDialogVisible = false;
  selectedUser: User | null = null;
  newPassword = '';
  confirmNewPassword = '';
  passwordError = '';

  ngOnInit() {
    this.loadUsers();
    this.libraryService.getAllLibrariesFromAPI().subscribe({
      next: (libraries) => {
        this.allLibraries = libraries;
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: 'Failed to load libraries',
        });
      },
    });
  }

  loadUsers() {
    this.userService.getUsers().subscribe({
      next: (data) => {
        this.users = data.map((user) => ({
          ...user,
          isEditing: false,
          selectedLibraryIds: user.assignedLibraries?.map((lib) => lib.id) || [],
          libraryNames:
            user.assignedLibraries?.map((lib) => lib.name).join(', ') || '',
        }));
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: 'Failed to fetch users',
        });
      },
    });
  }

  openCreateUserDialog() {
    this.ref = this.dialogService.open(CreateUserDialogComponent, {
      header: 'Create New User',
      modal: true,
      closable: true,
      style: {position: 'absolute', top: '15%'},
    });
    this.ref.onClose.subscribe((result) => {
      if (result) {
        this.loadUsers();
      }
    });
  }

  toggleEdit(user: any) {
    user.isEditing = !user.isEditing;
    if (user.isEditing) {
      this.editingLibraryIds = [...user.selectedLibraryIds];
    } else {
      user.libraryNames =
        user.assignedLibraries
          ?.map((lib: Library) => lib.name)
          .join(', ') || '';
    }
  }

  saveUser(user: any) {
    user.selectedLibraryIds = [...this.editingLibraryIds];
    this.userService
      .updateUser(user.id, {
        name: user.name,
        email: user.email,
        permissions: user.permissions,
        assignedLibraries: user.selectedLibraryIds,
      })
      .subscribe({
        next: () => {
          user.isEditing = false;
          this.loadUsers();
          this.messageService.add({
            severity: 'success',
            summary: 'Success',
            detail: 'User updated successfully',
          });
        },
        error: () => {
          this.messageService.add({
            severity: 'error',
            summary: 'Error',
            detail: 'Failed to update user',
          });
        },
      });
  }

  deleteUser(user: User) {
    if (confirm(`Are you sure you want to delete ${user.username}?`)) {
      this.userService.deleteUser(user.id).subscribe({
        next: () => {
          this.messageService.add({
            severity: 'success',
            summary: 'Success',
            detail: `User ${user.username} deleted successfully`,
          });
          this.loadUsers();
        },
        error: (err) => {
          this.messageService.add({
            severity: 'error',
            summary: 'Error',
            detail:
              err.error?.message ||
              `Failed to delete user ${user.username}`,
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
      this.passwordError = 'Both fields are required';
      return;
    }

    if (this.newPassword !== this.confirmNewPassword) {
      this.passwordError = 'Passwords do not match';
      return;
    }

    if (this.selectedUser) {
      this.userService
        .changeUserPassword(this.selectedUser.id, this.newPassword)
        .subscribe({
          next: () => {
            this.messageService.add({
              severity: 'success',
              summary: 'Success',
              detail: 'Password changed successfully',
            });
            this.isPasswordDialogVisible = false;
          },
          error: (err) => {
            this.passwordError = err;
          }
        });
    }
  }
}
