import {describe, expect, it} from 'vitest';
import {isPrimaryButtonHeld} from './pointer-gestures';

describe('isPrimaryButtonHeld', () => {
  const move = (buttons: number) => new MouseEvent('mousemove', {buttons});

  it('is true while the primary button is held, alone or with others', () => {
    expect(isPrimaryButtonHeld(move(1))).toBe(true);
    expect(isPrimaryButtonHeld(move(1 | 2))).toBe(true);
  });

  it('is false when the primary button is released, even if another button is held', () => {
    expect(isPrimaryButtonHeld(move(0))).toBe(false);
    expect(isPrimaryButtonHeld(move(2))).toBe(false);
    expect(isPrimaryButtonHeld(move(4))).toBe(false);
  });
});
