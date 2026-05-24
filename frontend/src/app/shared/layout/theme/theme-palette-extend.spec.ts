import {describe, expect, it} from 'vitest';

import ExtendedAura from './theme-palette-extend';

interface AppThemePreset {
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

interface AppThemePresetComponents {
  components?: {
    button?: {
      colorScheme?: {
        light?: {
          outlined?: Record<string, { borderColor?: string; color?: string }>;
        };
      };
    };
  };
}

describe('theme-palette-extend', () => {
  it('bridges app content surfaces', () => {
    const preset = ExtendedAura as AppThemePreset;

    expect(preset.semantic?.colorScheme?.light?.content?.background).toBe('var(--color-page)');
    expect(preset.semantic?.colorScheme?.dark?.content?.background).toBe('var(--color-page)');
  });

  it('uses balanced light outlined button tokens', () => {
    const outlined = (ExtendedAura as AppThemePreset & AppThemePresetComponents)
      .components?.button?.colorScheme?.light?.outlined;

    expect(outlined?.['info']).toMatchObject({ borderColor: '{sky.400}', color: '{sky.600}' });
    expect(outlined?.['help']).toMatchObject({ borderColor: '{purple.400}', color: '{purple.600}' });
    expect(outlined?.['secondary']).toMatchObject({
      borderColor: '{surface.400}',
      color: '{surface.600}',
    });
  });
});
