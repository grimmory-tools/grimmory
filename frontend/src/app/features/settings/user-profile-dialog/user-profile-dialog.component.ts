import {NgClass} from '@angular/common';
import {Component, DestroyRef, computed, effect, inject} from '@angular/core';
import {Button} from 'primeng/button';
import {AbstractControl, FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators} from '@angular/forms';
import {InputText} from 'primeng/inputtext';
import {Password} from 'primeng/password';
import {User, UserService, UserUpdateRequest} from '../user-management/user.service';
import {MessageService} from 'primeng/api';
import {DynamicDialogRef} from 'primeng/dynamicdialog';
import {TranslocoDirective, TranslocoPipe, TranslocoService} from '@jsverse/transloco';
import {Select} from 'primeng/select';
import {takeUntilDestroyed, toSignal} from '@angular/core/rxjs-interop';
import {AVAILABLE_LANGS, LANG_LABELS} from '../../../core/config/transloco-loader';
import {LANG_STORAGE_KEY} from '../../../core/config/language-initializer';
import {AppConfigService} from '../../../shared/service/app-config.service';
import Aura from '../../../shared/layout/theme-palette-extend';

type ColorPalette = Record<string, string>;

interface Palette {
  name: string;
  palette: ColorPalette;
}

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
    Select,
    NgClass,
    TranslocoDirective,
    TranslocoPipe,
  ],
  templateUrl: './user-profile-dialog.component.html',
  styleUrls: ['./user-profile-dialog.component.scss']
})
export class UserProfileDialogComponent {

  isEditing = false;
  currentUser: User | null = null;
  editUserData: Partial<User> = {};
  changePasswordForm: FormGroup;
  readonly languageOptions = AVAILABLE_LANGS.map(value => ({
    value,
    label: LANG_LABELS[value] ?? value,
  }));

  protected readonly userService = inject(UserService);
  protected readonly configService = inject(AppConfigService);
  private readonly messageService = inject(MessageService);
  private readonly fb = inject(FormBuilder);
  private readonly dialogRef = inject(DynamicDialogRef);
  private readonly t = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly activeLang = toSignal(this.t.langChanges$, {initialValue: this.t.getActiveLang()});
  protected readonly selectedPrimaryColor = computed(() => this.configService.appState().primary);
  protected readonly selectedSurfaceColor = computed(() => this.configService.appState().surface);
  protected readonly surfaces = this.configService.surfaces;
  protected readonly primaryColors = this.getPrimaryColors();

  constructor() {
    this.changePasswordForm = this.fb.group(
      {
        currentPassword: ['', Validators.required],
        newPassword: ['', [Validators.required, Validators.minLength(8)]],
        confirmNewPassword: ['', Validators.required]
      },
      {validators: passwordMatchValidator}
    );
  }

  private readonly userSyncEffect = effect(() => {
    const user = this.userService.currentUser();
    if (user) {
      this.currentUser = user;
      this.resetEditForm();
    }
  });

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

  onLanguageChange(lang: string): void {
    if (lang === this.activeLang()) return;
    this.t.load(lang).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.t.setActiveLang(lang);
        localStorage.setItem(LANG_STORAGE_KEY, lang);
      },
    });
  }

  updateThemeColor(event: Event, type: 'primary' | 'surface', color: { name: string; palette?: ColorPalette }): void {
    this.configService.appState.update((state) => ({
      ...state,
      [type]: color.name,
    }));
    event.stopPropagation();
  }

  private getPrimaryColors(): Palette[] {
    const presetPalette = (Aura.primitive ?? {}) as Record<string, ColorPalette>;
    const colors = [
      'orange', 'amber', 'yellow', 'lime', 'green', 'emerald',
      'teal', 'cyan', 'sky', 'blue', 'indigo', 'violet',
      'purple', 'fuchsia', 'pink', 'rose', 'red',
      'coralSunset', 'roseBlush', 'melonBlush', 'cottonCandy',
      'apricotSunrise', 'antiqueBronze', 'butteryYellow', 'vanillaCream',
      'citrusMint', 'freshMint', 'sagePearl', 'skyBlue', 'periwinkleCream',
      'pastelRoyalBlue', 'lavenderDream', 'dustyNeutral',
    ];
    return [{name: 'noir', palette: {}}].concat(
      colors.map(name => ({
        name,
        palette: presetPalette[name] ?? {},
      })),
    );
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
