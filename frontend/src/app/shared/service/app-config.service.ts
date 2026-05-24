import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { inject, Injectable, PLATFORM_ID, signal } from '@angular/core';
import { FaviconService } from '../layout/theme/favicon-service';
import {applyPrimeTheme, primeThemeTokenPalettes} from '../layout/theme/theme-palette-extend';
import {
  APP_THEME_OPTIONS,
  AppearancePreference,
  AppState,
  AppTheme,
  CUSTOM_PRIMARY_OPTIONS,
  CustomPrimary,
  DEFAULT_APP_THEME,
  DEFAULT_CUSTOM_PRIMARY,
} from '../model/app-state.model';

const PRIMARY_COLOR_STOPS = ['50', '100', '200', '300', '400', '500', '600', '700', '800', '900', '950'];
const DEFAULT_APPEARANCE_PREFERENCE: AppearancePreference = 'system';
const LEGACY_APPEARANCE_PREFERENCE: AppearancePreference = 'dark';

type StoredAppState = Partial<AppState> & {
  preset?: unknown;
  primary?: unknown;
  surface?: unknown;
};

interface LoadedAppState {
  state: AppState;
  shouldPersist: boolean;
}

@Injectable({
  providedIn: 'root',
})
export class AppConfigService {
  private readonly STORAGE_KEY = 'appConfigState';
  readonly themes = APP_THEME_OPTIONS;
  private readonly appStateSignal = signal<AppState>({});
  readonly appState = this.appStateSignal.asReadonly();
  document = inject(DOCUMENT);
  platformId = inject(PLATFORM_ID);
  faviconService = inject(FaviconService);

  constructor() {
    const initialState = this.loadAppState();
    this.appStateSignal.set(initialState.state);

    if (initialState.shouldPersist) {
      this.saveAppState(initialState.state);
    }
    this.applyAppState(initialState.state);
  }

  setThemePreference(themePreference: AppTheme): void {
    this.updateAppState({themePreference});
  }

  setCustomPrimary(customPrimary: CustomPrimary): void {
    this.updateAppState({customPrimary});
  }

  setAppearancePreference(appearancePreference: AppearancePreference): void {
    this.updateAppState({appearancePreference});
  }

  private updateAppState(patch: AppState): void {
    const state = this.withDefaults({
      ...this.appStateSignal(),
      ...patch,
    });
    this.appStateSignal.set(state);
    this.saveAppState(state);
    this.applyAppState(state);
  }

  private loadAppState(): LoadedAppState {
    if (isPlatformBrowser(this.platformId)) {
      const storedState = localStorage.getItem(this.STORAGE_KEY);
      if (storedState) {
        try {
          const parsedState = JSON.parse(storedState) as StoredAppState;
          return {
            state: this.normalizeStoredState(parsedState),
            shouldPersist: this.isLegacyPaletteState(parsedState),
          };
        } catch {
          return {state: this.withDefaults({}), shouldPersist: false};
        }
      }
    }

    return {state: this.withDefaults({}), shouldPersist: false};
  }

  private normalizeStoredState(state: StoredAppState): AppState {
    if (this.isLegacyPaletteState(state)) {
      return this.withDefaults({
        themePreference: DEFAULT_APP_THEME,
        appearancePreference: LEGACY_APPEARANCE_PREFERENCE,
        customPrimary: DEFAULT_CUSTOM_PRIMARY,
      });
    }

    return this.withDefaults({
      themePreference: state.themePreference,
      appearancePreference: state.appearancePreference,
      customPrimary: state.customPrimary,
    });
  }

  private isLegacyPaletteState(state: StoredAppState): boolean {
    return 'preset' in state || 'primary' in state || 'surface' in state;
  }

  private withDefaults(state: AppState): AppState {
    return {
      themePreference: this.resolveThemePreference(state.themePreference),
      appearancePreference: this.resolveAppearancePreference(state.appearancePreference),
      customPrimary: this.resolveCustomPrimary(state.customPrimary),
    };
  }

  private resolveCustomPrimary(customPrimary: AppState['customPrimary']): CustomPrimary {
    if (customPrimary && CUSTOM_PRIMARY_OPTIONS.includes(customPrimary)) {
      return customPrimary;
    }
    return DEFAULT_CUSTOM_PRIMARY;
  }

  private resolveThemePreference(themePreference: AppState['themePreference']): AppTheme {
    if (themePreference && this.themes.some((option) => option.name === themePreference)) {
      return themePreference;
    }

    return DEFAULT_APP_THEME;
  }

  private resolveAppearancePreference(appearancePreference: AppState['appearancePreference']): AppearancePreference {
    if (appearancePreference === 'light' || appearancePreference === 'dark' || appearancePreference === 'system') {
      return appearancePreference;
    }
    return DEFAULT_APPEARANCE_PREFERENCE;
  }

  private effectiveAppearancePreference(appearancePreference: AppearancePreference): 'light' | 'dark' {
    if (appearancePreference !== 'system') {
      return appearancePreference;
    }
    if (isPlatformBrowser(this.platformId) && globalThis.window?.matchMedia) {
      return globalThis.window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    }
    return 'dark';
  }

  private saveAppState(state: AppState): void {
    if (isPlatformBrowser(this.platformId)) {
      localStorage.setItem(this.STORAGE_KEY, JSON.stringify(this.withDefaults(state)));
    }
  }

  applyCurrentTheme(): void {
    this.applyAppState(this.withDefaults(this.appStateSignal()));
  }

  private applyAppState(state: AppState): void {
    this.applyThemeAttributes(state);

    if (!isPlatformBrowser(this.platformId)) {
      return;
    }

    applyPrimeTheme(primeThemeTokenPalettes());
    this.updateFavicon();
  }

  private applyThemeAttributes(state: AppState): void {
    const root = this.document.documentElement;
    const theme = this.resolveThemePreference(state.themePreference);
    const appearancePreference = this.resolveAppearancePreference(state.appearancePreference);
    const effective = this.effectiveAppearancePreference(appearancePreference);

    root.dataset['appTheme'] = theme;
    root.classList.toggle('dark', effective === 'dark');
    root.style.setProperty('color-scheme', effective);
    this.applyCustomPrimary(root, theme, this.resolveCustomPrimary(state.customPrimary));
    this.syncSystemSchemeListener(appearancePreference);
  }

  private applyCustomPrimary(root: HTMLElement, theme: AppTheme, customPrimary: CustomPrimary): void {
    if (theme === 'custom') {
      PRIMARY_COLOR_STOPS.forEach((stop) => {
        root.style.setProperty(`--color-primary-${stop}`, `var(--color-${customPrimary}-${stop})`);
      });
    } else {
      PRIMARY_COLOR_STOPS.forEach((stop) => root.style.removeProperty(`--color-primary-${stop}`));
    }
  }

  private systemSchemeMedia: MediaQueryList | null = null;
  private systemSchemeListener: ((event: MediaQueryListEvent) => void) | null = null;

  private syncSystemSchemeListener(appearancePreference: AppearancePreference): void {
    if (!isPlatformBrowser(this.platformId) || !globalThis.window?.matchMedia) {
      return;
    }
    if (appearancePreference === 'system') {
      if (!this.systemSchemeMedia) {
        this.systemSchemeMedia = globalThis.window.matchMedia('(prefers-color-scheme: dark)');
        this.systemSchemeListener = () => this.applyCurrentTheme();
        this.systemSchemeMedia.addEventListener('change', this.systemSchemeListener);
      }
    } else if (this.systemSchemeMedia && this.systemSchemeListener) {
      this.systemSchemeMedia.removeEventListener('change', this.systemSchemeListener);
      this.systemSchemeMedia = null;
      this.systemSchemeListener = null;
    }
  }

  private updateFavicon(): void {
    const styles = getComputedStyle(this.document.documentElement);
    this.faviconService.updateFavicon(
      styles.getPropertyValue('--color-primary-300').trim(),
      styles.getPropertyValue('--color-primary-500').trim(),
    );
  }
}
