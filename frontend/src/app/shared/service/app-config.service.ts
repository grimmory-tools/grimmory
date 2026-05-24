import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { effect, inject, Injectable, PLATFORM_ID, signal } from '@angular/core';
import { $t } from '@primeuix/themes';
import { FaviconService } from '../layout/theme-configurator/favicon-service';
import Aura from '../layout/theme-palette-extend';
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

type ColorPalette = Record<string, string>;

const COLOR_STOPS = ['0', '50', '100', '200', '300', '400', '500', '600', '700', '800', '900', '950'];
const DEFAULT_APPEARANCE_PREFERENCE: AppearancePreference = 'system';
const LEGACY_APPEARANCE_PREFERENCE: AppearancePreference = 'dark';

type StoredAppState = Partial<AppState> & {
  preset?: unknown;
  primary?: unknown;
  surface?: unknown;
};

interface ResolvedThemePalettes {
  primary: ColorPalette;
  surface: ColorPalette;
}

@Injectable({
  providedIn: 'root',
})
export class AppConfigService {
  private readonly STORAGE_KEY = 'appConfigState';
  readonly themes = APP_THEME_OPTIONS;
  appState = signal<AppState>({});
  document = inject(DOCUMENT);
  platformId = inject(PLATFORM_ID);
  faviconService = inject(FaviconService);
  private initialized = false;

  constructor() {
    const initialState = this.loadAppState();
    this.appState.set(initialState);

    if (isPlatformBrowser(this.platformId)) {
      this.saveAppState(initialState);
      this.applyCurrentTheme();
    } else {
      this.applyThemeAttributes(initialState);
    }

    effect(() => {
      const state = this.appState();
      if (!this.initialized || !state) {
        this.initialized = true;
        return;
      }
      this.saveAppState(state);
      this.applyCurrentTheme();
    });
  }

  private loadAppState(): AppState {
    if (isPlatformBrowser(this.platformId)) {
      const storedState = localStorage.getItem(this.STORAGE_KEY);
      if (storedState) {
        try {
          return this.normalizeStoredState(JSON.parse(storedState) as StoredAppState);
        } catch {
          return this.withDefaults({});
        }
      }
    }

    return this.withDefaults({});
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
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }

    const state = this.withDefaults(this.appState());
    this.applyThemeAttributes(state);

    const theme = this.readActiveTheme();
    this.applyPrimeTheme(theme);
    this.updateFavicon(theme);
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
    const stops = COLOR_STOPS.filter((stop) => stop !== '0');
    if (theme === 'custom') {
      stops.forEach((stop) => {
        root.style.setProperty(`--color-primary-${stop}`, `var(--color-${customPrimary}-${stop})`);
      });
    } else {
      stops.forEach((stop) => root.style.removeProperty(`--color-primary-${stop}`));
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

  private readActiveTheme(): ResolvedThemePalettes {
    const styles = getComputedStyle(this.document.documentElement);

    return {
      primary: this.readPalette(styles, '--color-primary'),
      surface: this.readPalette(styles, '--color-surface'),
    };
  }

  private readPalette(styles: CSSStyleDeclaration, prefix: string): ColorPalette {
    return Object.fromEntries(
      COLOR_STOPS.map((stop) => [stop, styles.getPropertyValue(`${prefix}-${stop}`).trim()])
    );
  }

  private buildPrimePreset(theme: ResolvedThemePalettes): object {
    return {
      semantic: {
        primary: theme.primary,
        colorScheme: {
          light: {
            primary: {
              color: '{primary.600}',
              contrastColor: '{surface.0}',
              hoverColor: '{primary.700}',
              activeColor: '{primary.800}',
            },
            highlight: {
              background: 'color-mix(in srgb, {primary.500}, transparent 84%)',
              focusBackground: 'color-mix(in srgb, {primary.500}, transparent 76%)',
              color: '{primary.900}',
              focusColor: '{primary.950}',
            },
          },
          dark: {
            primary: {
              color: '{primary.400}',
              contrastColor: '{surface.950}',
              hoverColor: '{primary.300}',
              activeColor: '{primary.200}',
            },
            highlight: {
              background: 'color-mix(in srgb, {primary.400}, transparent 84%)',
              focusBackground: 'color-mix(in srgb, {primary.400}, transparent 76%)',
              color: '{primary.50}',
              focusColor: '{primary.0}',
            },
          },
        },
      },
    };
  }

  private applyPrimeTheme(theme: ResolvedThemePalettes): void {
    $t()
      .preset(Aura)
      .preset(this.buildPrimePreset(theme))
      .surfacePalette(theme.surface)
      .use({ useDefaultOptions: true });
  }

  private updateFavicon(theme: ResolvedThemePalettes): void {
    this.faviconService.updateFavicon(theme.primary['300'], theme.primary['500']);
  }
}
