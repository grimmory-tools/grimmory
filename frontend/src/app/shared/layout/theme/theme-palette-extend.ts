import Aura from '@primeuix/themes/aura';
import {$t, definePreset} from '@primeuix/themes';

export type ColorPalette = Record<string, string>;

export interface ResolvedThemePalettes {
  primary: ColorPalette;
  surface: ColorPalette;
}

const COLOR_STOPS = ['50', '100', '200', '300', '400', '500', '600', '700', '800', '900', '950'] as const;
const SURFACE_STOPS = ['0', ...COLOR_STOPS] as const;

/*
 * PrimeNG theme bridge.
 *
 * App-owned CSS tokens define the native shell/page foundation. This file only
 * extends Prime's Aura preset so Prime components can use the same app-selected
 * palettes and the same Tailwind-owned page background.
 */

/** Popup menu surfaces use card elevation, not page canvas. */
const menuRoot = { background: 'var(--color-card)' };

/*
 * Aura light outlined buttons default to *-200 borders — too faint on --color-card.
 * Use *-400 borders and *-600 text: readable without feeling heavy.
 */
const lightOutlinedButton = {
  primary: { borderColor: 'var(--color-primary)', color: 'var(--color-primary)' },
  secondary: { borderColor: '{surface.400}', color: '{surface.600}' },
  plain: { borderColor: '{surface.400}', color: '{surface.600}' },
  success: { borderColor: '{green.400}', color: '{green.600}' },
  info: { borderColor: '{sky.400}', color: '{sky.600}' },
  warn: { borderColor: '{orange.400}', color: '{orange.600}' },
  danger: { borderColor: '{red.400}', color: '{red.600}' },
  help: { borderColor: '{purple.400}', color: '{purple.600}' },
};

const appPrimary = {
  color: 'var(--color-primary)',
  contrastColor: 'var(--color-primary-contrast)',
  hoverColor: 'var(--color-primary-hover)',
  activeColor: 'var(--color-primary-active)',
};

const appHighlight = {
  background: 'color-mix(in srgb, var(--color-primary), transparent 84%)',
  focusBackground: 'color-mix(in srgb, var(--color-primary), transparent 76%)',
  color: 'var(--color-primary-text)',
  focusColor: 'var(--color-primary-text)',
};

const contentSurface = {
  background: 'var(--color-page)',
  borderColor: 'var(--color-border)',
  color: 'var(--color-text)',
};

const overlaySurface = {
  background: 'var(--color-card)',
  borderColor: 'var(--color-border)',
  color: 'var(--color-text)',
};

function buildAppTokenPalette(prefix: 'primary' | 'surface', stops: readonly string[]): ColorPalette {
  return Object.fromEntries(
    stops.map((stop) => [stop, `var(--color-${prefix}-${stop})`])
  );
}

const appTokenPalettes: ResolvedThemePalettes = {
  primary: buildAppTokenPalette('primary', COLOR_STOPS),
  surface: buildAppTokenPalette('surface', SURFACE_STOPS),
};

export function primeThemeTokenPalettes(): ResolvedThemePalettes {
  return appTokenPalettes;
}

function buildPrimePalettePreset(theme: ResolvedThemePalettes): object {
  return {
    semantic: {
      primary: theme.primary,
      colorScheme: {
        light: {
          primary: appPrimary,
          highlight: appHighlight,
        },
        dark: {
          primary: appPrimary,
          highlight: appHighlight,
        },
      },
    },
  };
}

const AppPrimePreset = definePreset(Aura, {
  semantic: {
    colorScheme: {
      light: {
        content: contentSurface,
        overlay: {
          popover: overlaySurface,
          modal: overlaySurface,
        },
      },
      dark: {
        content: contentSurface,
        overlay: {
          popover: overlaySurface,
          modal: overlaySurface,
        },
        navigation: {
          item: {
            focusBackground: 'color-mix(in srgb, {text.color}, transparent 92%)',
            activeBackground: 'color-mix(in srgb, {text.color}, transparent 92%)',
          },
        },
      },
    },
  },
  components: {
    menu: { root: menuRoot },
    tieredmenu: { root: menuRoot },
    contextmenu: { root: menuRoot },
    menubar: { submenu: menuRoot },
    button: {
      colorScheme: {
        light: { outlined: lightOutlinedButton },
      },
    },
  },
});

export function applyPrimeTheme(theme: ResolvedThemePalettes): void {
  $t()
    .preset(AppPrimePreset)
    .preset(buildPrimePalettePreset(theme))
    .surfacePalette(theme.surface)
    .use({useDefaultOptions: true});
}

export default AppPrimePreset;
