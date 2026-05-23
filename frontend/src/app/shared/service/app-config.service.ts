import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { effect, inject, Injectable, PLATFORM_ID, signal } from '@angular/core';
import { $t } from '@primeuix/themes';
import { FaviconService } from '../layout/theme-configurator/favicon-service';
import Aura from '../layout/theme-palette-extend';
import {
  APP_THEME_OPTIONS,
  AppColorScheme,
  AppState,
  AppTheme,
  DEFAULT_APP_THEME,
} from '../model/app-state.model';

type ColorPalette = Record<string, string>;

const COLOR_STOPS = ['0', '50', '100', '200', '300', '400', '500', '600', '700', '800', '900', '950'];
const DEFAULT_COLOR_SCHEME: AppColorScheme = 'dark';

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
      this.onPresetChange();
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
      this.onPresetChange();
    });
  }

  private loadAppState(): AppState {
    if (isPlatformBrowser(this.platformId)) {
      const storedState = localStorage.getItem(this.STORAGE_KEY);
      if (storedState) {
        return this.withDefaults(JSON.parse(storedState) as AppState);
      }
    }

    return this.withDefaults({});
  }

  private withDefaults(state: AppState): AppState {
    return {
      preset: state.preset ?? 'Aura',
      theme: this.resolveTheme(state.theme),
      colorScheme: this.resolveColorScheme(state.colorScheme),
    };
  }

  private resolveTheme(theme: AppState['theme']): AppTheme {
    if (theme && this.themes.some((option) => option.name === theme)) {
      return theme;
    }

    return DEFAULT_APP_THEME;
  }

  private resolveColorScheme(colorScheme: AppState['colorScheme']): AppColorScheme {
    return colorScheme === 'light' ? 'light' : DEFAULT_COLOR_SCHEME;
  }

  private saveAppState(state: AppState): void {
    if (isPlatformBrowser(this.platformId)) {
      localStorage.setItem(this.STORAGE_KEY, JSON.stringify(this.withDefaults(state)));
    }
  }

  onPresetChange(): void {
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
    const theme = this.resolveTheme(state.theme);
    const colorScheme = this.resolveColorScheme(state.colorScheme);

    root.dataset['appTheme'] = theme;
    root.classList.toggle('dark', colorScheme === 'dark');
    root.style.setProperty('color-scheme', colorScheme);
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
