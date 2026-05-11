import Aura from '@primeuix/themes/aura';
import {describe, expect, it} from 'vitest';

import ExtendedAura from './theme-palette-extend';

interface AppThemePreset {
  primitive?: Record<string, Record<string, string>>;
  semantic?: AppThemeSemantic;
}

interface AppThemeSemantic {
  colorScheme?: {
    light?: AppThemeColorScheme;
    dark?: AppThemeColorScheme;
  };
}

interface AppThemeColorScheme {
  content?: {
    background?: string;
  };
}

describe('theme-palette-extend', () => {
  it('extends Aura with custom primitive palettes and app content surfaces', () => {
    const auraPrimitive = Aura.primitive as Record<string, Record<string, string>>;
    const preset = ExtendedAura as AppThemePreset;

    expect(auraPrimitive).toBeTruthy();
    expect(auraPrimitive['coralSunset']['500']).toBe('#ef7550');
    expect(auraPrimitive['skyBlue']['700']).toBe('#2e88e6');
    expect(auraPrimitive['dustyNeutral']['950']).toBe('#2f2821');
    expect(preset.primitive?.['coralSunset']['500']).toBe('#ef7550');
    expect(preset.semantic?.colorScheme?.light?.content?.background).toBe('var(--page-background)');
    expect(preset.semantic?.colorScheme?.dark?.content?.background).toBe('var(--page-background)');
  });
});
