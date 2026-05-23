import {describe, expect, expectTypeOf, it} from 'vitest';

import {APP_THEME_OPTIONS, AppState, DEFAULT_APP_THEME} from './app-state.model';

describe('app-state.model', () => {
  it('supports partially specified visual state presets', () => {
    const appState: AppState = {
      preset: 'midnight',
      theme: DEFAULT_APP_THEME,
      colorScheme: 'dark',
    };

    expect(appState.preset).toBe('midnight');
    expect(appState.theme).toBe('grimmory');
    expect(appState.colorScheme).toBe('dark');
  });

  it('keeps each field optional and constrained to supported values', () => {
    expectTypeOf<AppState['preset']>().toEqualTypeOf<string | undefined>();
    expectTypeOf<AppState['theme']>().toEqualTypeOf<
      | 'grimmory'
      | 'cobalt'
      | 'ember'
      | 'crimson'
      | 'rose'
      | 'forest'
      | 'meadow'
      | 'teal'
      | 'lagoon'
      | 'violet'
      | 'fuchsia'
      | 'slate'
      | undefined
    >();
    expectTypeOf<AppState['colorScheme']>().toEqualTypeOf<'light' | 'dark' | undefined>();
  });

  it('defines the curated theme options', () => {
    expect(APP_THEME_OPTIONS).toEqual([
      {
        name: 'grimmory',
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
    ]);
  });
});
