import {Component, inject, OnInit} from '@angular/core';
import {InputText} from 'primeng/inputtext';
import {FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators} from '@angular/forms';
import {Checkbox} from 'primeng/checkbox';
import {MultiSelectModule} from 'primeng/multiselect';
import {Library} from '../../../book/model/library.model';
import {Button} from 'primeng/button';
import {LibraryService} from '../../../book/service/library.service';
import {UserService} from '../user.service';
import {MessageService} from 'primeng/api';
import {DynamicDialogRef} from 'primeng/dynamicdialog';
import {passwordMatchValidator} from '../../../../shared/validators/password-match.validator';
import {TranslocoDirective, TranslocoPipe, TranslocoService} from '@jsverse/transloco';


@Component({
  selector: 'app-create-user-dialog',
  standalone: true,
  imports: [
    InputText,
    ReactiveFormsModule,
    FormsModule,
    Checkbox,
    MultiSelectModule,
    Button,
    TranslocoDirective,
    TranslocoPipe
  ],
  templateUrl: './create-user-dialog.component.html',
  styleUrl: './create-user-dialog.component.scss'
})
export class CreateUserDialogComponent implements OnInit {
  userForm!: FormGroup;
  libraries: Library[] = [];

  private fb = inject(FormBuilder);
  private libraryService = inject(LibraryService);
  private userService = inject(UserService);
  private messageService = inject(MessageService);
  private ref = inject(DynamicDialogRef);
  private t = inject(TranslocoService);

  ngOnInit() {
    this.libraries = this.libraryService.getLibrariesFromState();

    this.userForm = this.fb.group({
      name: ['', [Validators.required, Validators.minLength(3)]],
      email: ['', [Validators.required, Validators.email]],
      username: ['', Validators.required],
      password: ['', [Validators.required, Validators.minLength(8)]],
      confirmPassword: ['', Validators.required],
      selectedLibraries: [[], Validators.required],
      permissionUpload: [false],
      permissionDownload: [false],
      permissionEditMetadata: [false],
      permissionManipulateLibrary: [false],
      permissionEmailBook: [false],
      permissionDeleteBook: [false],
      permissionAccessOpds: [false],
      permissionSyncKoreader: [false],
      permissionSyncKobo: [false],
      permissionManageMetadataConfig: [false],
      permissionAccessBookdrop: [false],
      permissionAccessLibraryStats: [false],
      permissionAccessUserStats: [false],
      permissionAccessTaskManager: [false],
      permissionManageEmailConfig: [false],
      permissionManageGlobalPreferences: [false],
      permissionManageIcons: [false],
      permissionManageFonts: [false],
      permissionAdmin: [false],
      permissionBulkAutoFetchMetadata: [false],
      permissionBulkCustomFetchMetadata: [false],
      permissionBulkEditMetadata: [false],
      permissionBulkRegenerateCover: [false],
      permissionMoveOrganizeFiles: [false],
      permissionBulkLockUnlockMetadata: [false],
      permissionBulkResetGrimmoryReadProgress: [false],
      permissionBulkResetKoReaderReadProgress: [false],
      permissionBulkResetBookReadStatus: [false],
    }, {validators: [passwordMatchValidator('password', 'confirmPassword')]});

    this.userForm.get('permissionAdmin')?.valueChanges.subscribe((isAdmin: boolean) => {
      const controls = this.userForm.controls;
      Object.keys(controls).forEach(key => {
        if (key !== 'permissionAdmin' && key.startsWith('permission')) {
          controls[key].setValue(isAdmin, {emitEvent: false});
        }
      });
    });
  }

  createUser() {
    if (this.userForm.invalid) {
      this.messageService.add({
        severity: 'warn',
        summary: this.t.translate('common.error'),
        detail: this.t.translate('settingsUsers.createDialog.validationError')
      });
      return;
    }
    // Detele confirmPassword from form, it's not necessary to keep once validation has passed
    const {confirmPassword, ...formValue} = this.userForm.value;
    void confirmPassword;

    const userData = {
      ...formValue,
      selectedLibraries: formValue.selectedLibraries.map((lib: Library) => lib.id)
    };

    this.userService.createUser(userData).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('common.success'),
          detail: this.t.translate('settingsUsers.createDialog.createSuccess')
        });
        this.ref.close(true);
      },
      error: (err) => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('common.error'),
          detail: err?.error?.message
            ? this.t.translate('settingsUsers.createDialog.createFailed', {message: err.error.message})
            : this.t.translate('settingsUsers.createDialog.createError')
        });
      }
    });
  }

  closeDialog(): void {
    this.ref.close();
  }
}
