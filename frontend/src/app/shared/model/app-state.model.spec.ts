import {describe, expect, expectTypeOf, it} from 'vitest';

import {
  APP_THEME_OPTIONS,
  AppearancePreference,
  AppState,
  AppTheme,
  CustomPrimary,
  DEFAULT_APP_THEME,
  THEME_REGISTRY,
} from './app-state.model';

describe('app-state.model', () => {
  it('supports partially specified visual theme state', () => {
    const appState: AppState = {
      themePreference: DEFAULT_APP_THEME,
      appearancePreference: 'system',
    };

    expect(appState.themePreference).toBe('grimmory');
    expect(appState.appearancePreference).toBe('system');
  });

  it('keeps each field optional and constrained to supported values', () => {
    expectTypeOf<AppState['themePreference']>().toEqualTypeOf<AppTheme | undefined>();
    expectTypeOf<AppState['appearancePreference']>().toEqualTypeOf<AppearancePreference | undefined>();
    expectTypeOf<AppState['customPrimary']>().toEqualTypeOf<CustomPrimary | undefined>();
  });

  it('derives theme options from the registry', () => {
    expect(APP_THEME_OPTIONS).toBe(THEME_REGISTRY);
    expect(APP_THEME_OPTIONS.some(option => option.name === DEFAULT_APP_THEME)).toBe(true);
  });
});
