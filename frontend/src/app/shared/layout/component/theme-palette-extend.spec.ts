import Aura from '@primeuix/themes/aura';
import {describe, expect, it} from 'vitest';

import ExtendedAura from './theme-palette-extend';

describe('theme-palette-extend', () => {
  it('extends Aura with the custom primitive palettes', () => {
    const primitive = Aura.primitive as Record<string, Record<string, string>>;

    expect(ExtendedAura).toBe(Aura);
    expect(primitive).toBeTruthy();
    expect(primitive['coralSunset']['500']).toBe('#ef7550');
    expect(primitive['skyBlue']['700']).toBe('#2e88e6');
    expect(primitive['dustyNeutral']['950']).toBe('#2f2821');
  });
});
