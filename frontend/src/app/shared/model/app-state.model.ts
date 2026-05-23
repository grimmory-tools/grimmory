export type AppColorScheme = 'light' | 'dark';

export const DEFAULT_APP_THEME = 'grimmory';

export const APP_THEME_OPTIONS = [
  {
    name: DEFAULT_APP_THEME,
    label: 'Grimmory',
  },
  {
    name: 'cobalt',
    label: 'Cobalt',
  },
  {
    name: 'ember',
    label: 'Ember',
  },
  {
    name: 'crimson',
    label: 'Crimson',
  },
  {
    name: 'rose',
    label: 'Rose',
  },
  {
    name: 'forest',
    label: 'Forest',
  },
  {
    name: 'meadow',
    label: 'Meadow',
  },
  {
    name: 'teal',
    label: 'Teal',
  },
  {
    name: 'lagoon',
    label: 'Lagoon',
  },
  {
    name: 'violet',
    label: 'Violet',
  },
  {
    name: 'fuchsia',
    label: 'Fuchsia',
  },
  {
    name: 'slate',
    label: 'Slate',
  },
] as const;

export type AppTheme = (typeof APP_THEME_OPTIONS)[number]['name'];

export interface AppThemeOption {
  name: AppTheme;
  label: string;
}

export interface AppState {
  preset?: string;
  theme?: AppTheme;
  colorScheme?: AppColorScheme;
}
