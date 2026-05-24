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
      | 'custom'
      | undefined
    >();
    expectTypeOf<AppState['colorScheme']>().toEqualTypeOf<'light' | 'dark' | 'system' | undefined>();
    expectTypeOf<AppState['customPrimary']>().toEqualTypeOf<
      | 'red'
      | 'orange'
      | 'amber'
      | 'yellow'
      | 'lime'
      | 'green'
      | 'emerald'
      | 'teal'
      | 'cyan'
      | 'sky'
      | 'blue'
      | 'indigo'
      | 'violet'
      | 'purple'
      | 'fuchsia'
      | 'pink'
      | 'rose'
      | undefined
    >();
  });

  it('defines the curated theme options', () => {
    expect(APP_THEME_OPTIONS).toEqual([
      {
        name: 'grimmory',
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
    ]);
  });
});
