import {describe, expect, it} from 'vitest';

import {fileSizeDisplayUnit, formatFileSizeKb} from './file-size';

describe('file-size util', () => {
  it('picks display units and formats with adaptive precision', () => {
    expect(fileSizeDisplayUnit(358).label).toBe('KB');
    expect(fileSizeDisplayUnit(1468006).label).toBe('GB');
    expect(formatFileSizeKb(700)).toBe('700 KB');
    expect(formatFileSizeKb(15360)).toBe('15.0 MB');
    expect(formatFileSizeKb(1468006)).toBe('1.40 GB');
  });
});
