import {ChangeDetectionStrategy, Component, computed, inject} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {toSignal} from '@angular/core/rxjs-interop';
import {Select} from 'primeng/select';
import {ToggleSwitch} from 'primeng/toggleswitch';
import {MessageService} from 'primeng/api';
import {TranslocoDirective, TranslocoPipe, TranslocoService} from '@jsverse/transloco';

import {AVAILABLE_LANGS, LANG_LABELS} from '../../../../core/config/transloco-loader';
import {AppLocaleService} from '../../../../shared/service/app-locale.service';
import {AppThemeService} from '../../../../shared/service/app-theme.service';
import {
  CUSTOM_PRIMARY_OPTIONS,
  DEFAULT_CUSTOM_PRIMARY,
} from '../../../../shared/model/app-state.model';
import type {AppearancePreference, AppTheme, CustomPrimary} from '../../../../shared/model/app-state.model';
import {APPEARANCE_OPTIONS} from '../../../../shared/layout/theme/appearance-options';
import {UserService} from '../../user-management/user.service';
import type {User, UserProfileUpdateRequest} from '../../user-management/user.service';
import {AppUiFontService} from '../../../../shared/service/app-ui-font.service';
import {ACCESSIBLE_UI_FONT, DEFAULT_UI_FONT} from '../../../../shared/model/ui-font.model';

@Component({
  selector: 'app-display-preferences',
  standalone: true,
  imports: [
    FormsModule,
    Select,
    ToggleSwitch,
    TranslocoDirective,
    TranslocoPipe,
  ],
  templateUrl: './display-preferences.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DisplayPreferencesComponent {
  private readonly themeService = inject(AppThemeService);
  private readonly localeService = inject(AppLocaleService);
  private readonly uiFontService = inject(AppUiFontService);
  private readonly userService = inject(UserService);
  private readonly messageService = inject(MessageService);
  private readonly t = inject(TranslocoService);

  protected readonly activeLang = toSignal(this.t.langChanges$, {initialValue: this.t.getActiveLang()});
  protected readonly appearanceOptions = computed(() => {
    const activeLang = this.activeLang();
    return APPEARANCE_OPTIONS.map((option) => ({
      value: option.value,
      label: this.t.translate(option.labelKey, {}, activeLang),
    }));
  });
  protected readonly languageOptions = AVAILABLE_LANGS.map(value => ({
    value,
    label: LANG_LABELS[value] ?? value,
  }));
  protected readonly selectedAppearancePreference = this.themeService.appearancePreference;
  protected readonly selectedUiFont = this.uiFontService.uiFont;
  protected readonly useAccessibleUiFont = computed(() => this.selectedUiFont() === ACCESSIBLE_UI_FONT);
  protected readonly selectedThemePreference = this.themeService.themePreference;
  protected readonly oledDarkMode = this.themeService.oledDarkMode;
  protected readonly showOledDarkModeToggle = computed(() => this.themeService.effectiveAppearance() === 'dark');
  protected readonly selectedCustomPrimary = this.themeService.customPrimary;
  protected readonly selectedThemeSyncEnabled = this.themeService.themeSyncEnabled;
  protected readonly customPrimaryOptions = computed(() => {
    const activeLang = this.activeLang();
    return CUSTOM_PRIMARY_OPTIONS.map((value) => {
      const color = this.t.translate(`settingsView.theme.customPrimaryColors.${value}`, {}, activeLang);
      return {
        value,
        ariaLabel: this.t.translate('settingsView.theme.customPrimaryOptionLabel', {color}, activeLang),
      };
    });
  });
  protected readonly themeOptions = computed(() => {
    const activeLang = this.activeLang();
    return this.themeService.themes.map((theme) => ({
      value: theme.name,
      label: this.t.translate(theme.labelKey, {}, activeLang),
    }));
  });

  protected async onLanguageChange(lang: string): Promise<void> {
    if (lang === this.activeLang()) return;

    const updatedUser = await this.updateCurrentUserProfile({locale: lang});
    if (!updatedUser) return;

    await this.localeService.applyLocale(updatedUser.locale);
    this.showSavedToast('settingsView.language.savedDetail');
  }

  protected async updateAccessibleUiFont(useAccessibleFont: boolean): Promise<void> {
    const uiFont = useAccessibleFont ? ACCESSIBLE_UI_FONT : DEFAULT_UI_FONT;
    if (uiFont === this.selectedUiFont()) return;

    const previousUiFont = this.selectedUiFont();
    this.uiFontService.applyUiFont(uiFont);

    if (!await this.updateCurrentUserProfile({uiFont})) {
      this.uiFontService.applyUiFont(previousUiFont);
      return;
    }

    this.showSavedToast('settingsView.uiFont.savedDetail');
  }

  protected async updateThemePreference(themePreference: AppTheme): Promise<void> {
    if (!this.selectedThemeSyncEnabled()) {
      this.themeService.applyDeviceTheme(themePreference, this.selectedCustomPrimary());
      return;
    }

    const updateRequest: UserProfileUpdateRequest = {
      theme: themePreference,
      ...(themePreference === 'custom' ? {themeAccent: this.selectedCustomPrimary()} : {}),
    };

    const updatedUser = await this.updateCurrentUserProfile(updateRequest);
    if (!updatedUser) return;

    this.applySyncedUserTheme(updatedUser);
    this.showSavedToast();
  }

  protected updateAppearancePreference(appearancePreference: AppearancePreference): void {
    this.themeService.setAppearancePreference(appearancePreference);
  }

  protected async updateCustomPrimary(customPrimary: CustomPrimary): Promise<void> {
    if (!this.selectedThemeSyncEnabled()) {
      this.themeService.applyDeviceTheme('custom', customPrimary);
      return;
    }

    const updatedUser = await this.updateCurrentUserProfile({theme: 'custom', themeAccent: customPrimary});
    if (!updatedUser) return;

    this.applySyncedUserTheme(updatedUser);
    this.showSavedToast();
  }

  protected async updateThemeSyncEnabled(themeSyncEnabled: boolean): Promise<void> {
    if (!themeSyncEnabled) {
      const updatedUser = await this.updateCurrentUserProfile({themeSyncEnabled: false});
      if (!updatedUser) return;

      this.themeService.applyDeviceTheme(this.selectedThemePreference(), this.selectedCustomPrimary());
      this.showSavedToast('settingsView.theme.syncSavedDetail');
      return;
    }

    const theme = this.selectedThemePreference();
    const updateRequest: UserProfileUpdateRequest = {
      themeSyncEnabled: true,
      theme,
      ...(theme === 'custom' ? {themeAccent: this.selectedCustomPrimary()} : {}),
    };
    const updatedUser = await this.updateCurrentUserProfile(updateRequest);
    if (!updatedUser) return;

    this.applySyncedUserTheme(updatedUser);
    this.showSavedToast('settingsView.theme.syncSavedDetail');
  }

  protected updateOledDarkMode(oledDarkMode: boolean): void {
    this.themeService.setOledDarkMode(oledDarkMode);
  }

  private applySyncedUserTheme(user: User): void {
    this.themeService.applySyncedTheme(user.theme, user.themeAccent ?? DEFAULT_CUSTOM_PRIMARY);
  }

  private async updateCurrentUserProfile(updateRequest: UserProfileUpdateRequest): Promise<User | null> {
    try {
      return await this.userService.updateCurrentUserProfile(updateRequest);
    } catch {
      this.messageService.add({
        severity: 'error',
        summary: this.t.translate('common.error'),
        detail: this.t.translate('settingsView.saveFailedDetail'),
      });
      return null;
    }
  }

  private showSavedToast(detailKey = 'settingsView.theme.savedDetail'): void {
    this.messageService.add({
      severity: 'success',
      summary: this.t.translate('common.success'),
      detail: this.t.translate(detailKey),
    });
  }
}
