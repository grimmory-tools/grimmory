import {describe, expect, it} from 'vitest';

import ExtendedAura, {primeThemeTokenPalettes} from './theme-palette-extend';

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
  primary?: {
    color?: string;
    contrastColor?: string;
    hoverColor?: string;
    activeColor?: string;
  };
  highlight?: {
    background?: string;
    color?: string;
  };
}

interface AppThemePresetComponents {
  components?: {
    inputtext?: {
      root?: {
        focusBorderColor?: string;
        focusRing?: {
          shadow?: string;
        };
      };
    };
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

  it('bridges Prime input focus styles to app control tokens', () => {
    const inputtext = (ExtendedAura as AppThemePresetComponents).components?.inputtext?.root;

    expect(inputtext?.focusBorderColor).toBe('var(--control-focus-border)');
    expect(inputtext?.focusRing?.shadow).toBe('none');
  });

  it('uses balanced light outlined button tokens', () => {
    const outlined = (ExtendedAura as AppThemePreset & AppThemePresetComponents)
      .components?.button?.colorScheme?.light?.outlined;

    expect(outlined?.['primary']).toMatchObject({
      borderColor: 'var(--color-primary)',
      color: 'var(--color-primary)',
    });
    expect(outlined?.['info']).toMatchObject({ borderColor: '{sky.400}', color: '{sky.600}' });
    expect(outlined?.['help']).toMatchObject({ borderColor: '{purple.400}', color: '{purple.600}' });
    expect(outlined?.['secondary']).toMatchObject({
      borderColor: '{surface.400}',
      color: '{surface.600}',
    });
  });

  it('defines Prime palettes from app CSS tokens', () => {
    const palettes = primeThemeTokenPalettes();

    expect(palettes.primary['0']).toBeUndefined();
    expect(palettes.primary['500']).toBe('var(--color-primary-500)');
    expect(palettes.surface['0']).toBe('var(--color-surface-0)');
    expect(palettes.surface['950']).toBe('var(--color-surface-950)');
  });
});
