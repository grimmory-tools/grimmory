import {describe, expect, expectTypeOf, it} from 'vitest';

import {AppState} from './app-state.model';

describe('app-state.model', () => {
  it('supports partially specified visual state presets', () => {
    const appState: AppState = {
      preset: 'midnight',
      primary: '#112233'
    };

    expect(appState.preset).toBe('midnight');
    expect(appState.surface).toBeUndefined();
  });

  it('keeps each field optional and string-based', () => {
    expectTypeOf<AppState['preset']>().toEqualTypeOf<string | undefined>();
    expectTypeOf<AppState['primary']>().toEqualTypeOf<string | undefined>();
    expectTypeOf<AppState['surface']>().toEqualTypeOf<string | undefined>();
  });
});
