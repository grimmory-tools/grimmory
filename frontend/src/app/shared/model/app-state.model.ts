import type {AppTheme, CustomPrimary} from './theme-registry';

export type AppearancePreference = 'light' | 'dark' | 'system';

export {
  APP_THEME_OPTIONS,
  CUSTOM_PRIMARY_OPTIONS,
  DEFAULT_APP_THEME,
  DEFAULT_CUSTOM_PRIMARY,
  THEME_REGISTRY,
} from './theme-registry';
export type {AppTheme, CustomPrimary} from './theme-registry';

export interface AppState {
  themePreference?: AppTheme;
  appearancePreference?: AppearancePreference;
  customPrimary?: CustomPrimary;
}
