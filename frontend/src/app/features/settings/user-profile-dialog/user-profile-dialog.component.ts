import {Component, DestroyRef, computed, effect, inject} from '@angular/core';
import {Button} from 'primeng/button';
import {AbstractControl, FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators} from '@angular/forms';
import {InputText} from 'primeng/inputtext';
import {Password} from 'primeng/password';
import {User, UserProfileUpdateRequest, UserService} from '../user-management/user.service';
import {MessageService} from 'primeng/api';
import {DynamicDialogRef} from 'primeng/dynamicdialog';
import {TranslocoDirective, TranslocoPipe, TranslocoService} from '@jsverse/transloco';
import {Select} from 'primeng/select';
import {takeUntilDestroyed, toSignal} from '@angular/core/rxjs-interop';
import {from, switchMap} from 'rxjs';
import {AVAILABLE_LANGS, LANG_LABELS} from '../../../core/config/transloco-loader';
import {AppThemeService} from '../../../shared/service/app-theme.service';
import {AppLocaleService} from '../../../shared/service/app-locale.service';
import {
  AppearancePreference,
  AppTheme,
  CUSTOM_PRIMARY_OPTIONS,
  CustomPrimary,
  DEFAULT_APP_THEME,
  DEFAULT_CUSTOM_PRIMARY,
} from '../../../shared/model/app-state.model';
import {ToggleSwitch} from 'primeng/toggleswitch';

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
    ToggleSwitch,
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
  protected readonly themeService = inject(AppThemeService);
  protected readonly appLocaleService = inject(AppLocaleService);
  private readonly messageService = inject(MessageService);
  private readonly fb = inject(FormBuilder);
  private readonly dialogRef = inject(DynamicDialogRef);
  private readonly t = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly activeLang = toSignal(this.t.langChanges$, {initialValue: this.t.getActiveLang()});
  protected readonly selectedThemePreference = computed(() => this.themeService.appState().themePreference ?? DEFAULT_APP_THEME);
  protected readonly selectedAppearancePreference = computed(() => this.themeService.appState().appearancePreference ?? 'system');
  protected readonly selectedCustomPrimary = computed<CustomPrimary>(
    () => this.themeService.appState().customPrimary ?? DEFAULT_CUSTOM_PRIMARY,
  );
  protected readonly selectedThemeSyncEnabled = computed(() => this.themeService.appState().themeSyncEnabled !== false);
  protected readonly customPrimaryOptions = CUSTOM_PRIMARY_OPTIONS;
  protected readonly themeOptions = computed(() => {
    this.activeLang();
    return this.themeService.themes.map((theme) => ({
      value: theme.name,
      label: this.t.translate(theme.labelKey),
    }));
  });
  protected readonly appearanceOptions = computed(() => {
    this.activeLang();
    return [
      {value: 'light' as AppearancePreference, label: this.t.translate('layout.theme.light')},
      {value: 'dark' as AppearancePreference, label: this.t.translate('layout.theme.dark')},
      {value: 'system' as AppearancePreference, label: this.t.translate('layout.theme.system')},
    ];
  });

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
    if (!this.currentUser) return;

    this.userService.updateUserProfile(this.currentUser.id, {locale: lang}).pipe(
      switchMap(user => from(this.appLocaleService.applyLocale(user.locale))),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe();
  }

  updateThemePreference(themePreference: AppTheme): void {
    if (!this.currentUser) return;

    if (!this.selectedThemeSyncEnabled()) {
      this.themeService.applyDeviceTheme(themePreference, this.selectedCustomPrimary());
      return;
    }

    const updateRequest: UserProfileUpdateRequest = {
      theme: themePreference,
      ...(themePreference === 'custom' ? {themeAccent: this.selectedCustomPrimary()} : {}),
    };

    this.userService.updateUserProfile(this.currentUser.id, updateRequest).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (user) => this.themeService.applySyncedTheme(user.theme, user.themeAccent ?? DEFAULT_CUSTOM_PRIMARY),
    });
  }

  updateCustomPrimary(customPrimary: CustomPrimary): void {
    if (!this.currentUser) return;

    if (!this.selectedThemeSyncEnabled()) {
      this.themeService.applyDeviceTheme('custom', customPrimary);
      return;
    }

    this.userService.updateUserProfile(this.currentUser.id, {theme: 'custom', themeAccent: customPrimary}).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (user) => this.themeService.applySyncedTheme(user.theme, user.themeAccent ?? DEFAULT_CUSTOM_PRIMARY),
    });
  }

  updateThemeSyncEnabled(themeSyncEnabled: boolean): void {
    if (!this.currentUser) return;

    if (!themeSyncEnabled) {
      this.userService.updateUserProfile(this.currentUser.id, {themeSyncEnabled: false}).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: () => this.themeService.applyDeviceTheme(this.selectedThemePreference(), this.selectedCustomPrimary()),
      });
      return;
    }

    const theme = this.selectedThemePreference();
    const updateRequest: UserProfileUpdateRequest = {
      themeSyncEnabled: true,
      theme,
      ...(theme === 'custom' ? {themeAccent: this.selectedCustomPrimary()} : {}),
    };
    this.userService.updateUserProfile(this.currentUser.id, updateRequest).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (user) => this.themeService.applySyncedTheme(user.theme, user.themeAccent ?? DEFAULT_CUSTOM_PRIMARY),
    });
  }

  updateAppearancePreference(appearancePreference: AppearancePreference): void {
    this.themeService.setAppearancePreference(appearancePreference);
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

    const updateRequest: UserProfileUpdateRequest = {
      name: this.editUserData.name,
      email: this.editUserData.email,
    };
    this.userService.updateUserProfile(this.currentUser.id, updateRequest).subscribe({
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
