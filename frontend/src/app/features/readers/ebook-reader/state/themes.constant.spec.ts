import {describe, expect, it} from 'vitest';

import {themes, Theme} from './themes.constant';

describe('themes.constant', () => {
  it('defines unique theme names', () => {
    const names = themes.map((theme) => theme.name);
    expect(new Set(names).size).toBe(names.length);
  });

  it('includes the expected default and amoled themes', () => {
    expect(themes.find((theme) => theme.name === 'default')?.label).toBe('Default');
    expect(themes.find((theme) => theme.name === 'amoled')?.dark.bg).toBe('#000000');
  });

  it('provides light and dark palettes with link colors for every theme', () => {
    for (const theme of themes) {
      expect(theme).toMatchObject<Partial<Theme>>({
        light: expect.objectContaining({
          fg: expect.any(String),
          bg: expect.any(String),
          link: expect.any(String)
        }),
        dark: expect.objectContaining({
          fg: expect.any(String),
          bg: expect.any(String),
          link: expect.any(String)
        })
      });
    }
  });
});
