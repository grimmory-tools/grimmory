import {Injectable, inject} from '@angular/core';
import {AuthService} from './auth.service';
import {User, UserService} from '../../features/settings/user-management/user.service';
import {QueryClient} from '@tanstack/angular-query-experimental';
import {AppThemeService} from './app-theme.service';
import {DEFAULT_CUSTOM_PRIMARY} from '../model/app-state.model';
import {AppLocaleService} from './app-locale.service';

@Injectable({providedIn: 'root'})
export class StartupService {
  private authService = inject(AuthService);
  private userService = inject(UserService);
  private queryClient = inject(QueryClient);
  private appThemeService = inject(AppThemeService);
  private appLocaleService = inject(AppLocaleService);

  async load(): Promise<void> {
    if (this.authService.token()) {
      const user = await this.queryClient.fetchQuery(this.userService.getUserQueryOptions());
      await this.applyUserPreferences(user);
      return undefined;
    }

    await this.appLocaleService.applyLocale(this.appLocaleService.getDisplayLocale());
  }

  private async applyUserPreferences(user: User): Promise<void> {
    this.applyTheme(user);
    await this.appLocaleService.applyLocale(user.locale);
  }

  private applyTheme(user: User): void {
    const customPrimary = user.themeAccent ?? DEFAULT_CUSTOM_PRIMARY;
    if (user.themeSyncEnabled) {
      this.appThemeService.applySyncedTheme(user.theme, customPrimary);
      return;
    }

    if (this.appThemeService.appState().themeSyncEnabled === false) {
      this.appThemeService.useStoredDeviceTheme();
      return;
    }

    this.appThemeService.applyDeviceTheme(user.theme, customPrimary);
  }
}
