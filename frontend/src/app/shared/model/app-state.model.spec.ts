import {describe, expect, it} from 'vitest';

import {
  APP_THEME_OPTIONS,
  DEFAULT_APP_THEME,
  THEME_REGISTRY,
} from './app-state.model';

describe('app-state.model', () => {
  it('derives theme options from the registry', () => {
    expect(APP_THEME_OPTIONS).toBe(THEME_REGISTRY);
    expect(APP_THEME_OPTIONS.some(option => option.name === DEFAULT_APP_THEME)).toBe(true);
  });
});
