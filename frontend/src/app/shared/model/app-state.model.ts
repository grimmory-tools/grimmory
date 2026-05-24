export type AppearancePreference = 'light' | 'dark' | 'system';

export const DEFAULT_APP_THEME = 'grimmory';

export const APP_THEME_OPTIONS = [
  {
    name: DEFAULT_APP_THEME,
    label: 'Grimmory',
    swatch: 'var(--color-orange-500)',
  },
  {
    name: 'cobalt',
    label: 'Cobalt',
    swatch: 'var(--color-blue-500)',
  },
  {
    name: 'ember',
    label: 'Ember',
    swatch: 'var(--color-amber-500)',
  },
  {
    name: 'crimson',
    label: 'Crimson',
    swatch: 'var(--color-red-500)',
  },
  {
    name: 'rose',
    label: 'Rose',
    swatch: 'var(--color-rose-500)',
  },
  {
    name: 'forest',
    label: 'Forest',
    swatch: 'var(--color-emerald-500)',
  },
  {
    name: 'meadow',
    label: 'Meadow',
    swatch: 'var(--color-lime-500)',
  },
  {
    name: 'teal',
    label: 'Teal',
    swatch: 'var(--color-teal-500)',
  },
  {
    name: 'lagoon',
    label: 'Lagoon',
    swatch: 'var(--color-cyan-500)',
  },
  {
    name: 'violet',
    label: 'Violet',
    swatch: 'var(--color-violet-500)',
  },
  {
    name: 'fuchsia',
    label: 'Fuchsia',
    swatch: 'var(--color-fuchsia-500)',
  },
  {
    name: 'slate',
    label: 'Slate',
    swatch: 'var(--color-gray-700)',
  },
  {
    name: 'custom',
    label: 'Custom',
    swatch: null,
  },
] as const;

export type AppTheme = (typeof APP_THEME_OPTIONS)[number]['name'];

export const CUSTOM_PRIMARY_OPTIONS = [
  'red', 'orange', 'amber', 'yellow', 'lime', 'green', 'emerald', 'teal',
  'cyan', 'sky', 'blue', 'indigo', 'violet', 'purple', 'fuchsia', 'pink', 'rose',
] as const;

export type CustomPrimary = (typeof CUSTOM_PRIMARY_OPTIONS)[number];

export const DEFAULT_CUSTOM_PRIMARY: CustomPrimary = 'orange';

export interface AppState {
  themePreference?: AppTheme;
  appearancePreference?: AppearancePreference;
  customPrimary?: CustomPrimary;
}
