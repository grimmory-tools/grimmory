import {describe, expect, expectTypeOf, it} from 'vitest';

import {ResetProgressType, ResetProgressTypes} from './reset-progress-type';

describe('reset-progress-type', () => {
  it('keeps the backend compatibility alias for the grimmory reset type', () => {
    expect(ResetProgressTypes).toEqual({
      KOREADER: 'KOREADER',
      GRIMMORY: 'BOOKLORE',
      KOBO: 'KOBO'
    });
  });

  it('narrows the union to the exported reset progress values', () => {
    expectTypeOf<ResetProgressType>().toEqualTypeOf<'KOREADER' | 'BOOKLORE' | 'KOBO'>();
  });
});
