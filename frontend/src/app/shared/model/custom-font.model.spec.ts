import {describe, expect, it} from 'vitest';

import {FontFormat, formatFileSize} from './custom-font.model';

describe('custom-font model', () => {
  it('exposes stable font format enums', () => {
    expect(FontFormat.TTF).toBe('TTF');
    expect(FontFormat.OTF).toBe('OTF');
    expect(FontFormat.WOFF).toBe('WOFF');
    expect(FontFormat.WOFF2).toBe('WOFF2');
  });

  it('formats file sizes across byte, kilobyte, and megabyte ranges', () => {
    expect(formatFileSize(0)).toBe('0 Bytes');
    expect(formatFileSize(512)).toBe('512 Bytes');
    expect(formatFileSize(1536)).toBe('1.5 KB');
    expect(formatFileSize(2 * 1024 * 1024)).toBe('2 MB');
  });
});
