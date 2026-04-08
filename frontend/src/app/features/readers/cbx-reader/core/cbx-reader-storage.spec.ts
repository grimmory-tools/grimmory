import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {
  clampStripMaxWidthPercent,
  readStripWidthPercent,
  shouldOfferWebtoonHint,
  writeStripWidthPercentPerBook
} from './cbx-reader-storage';

describe('cbx-reader-storage', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    vi.stubGlobal('localStorage', localStorage);
    vi.stubGlobal('sessionStorage', sessionStorage);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  describe('clampStripMaxWidthPercent', () => {
    it('clamps to 50–100 and rounds', () => {
      expect(clampStripMaxWidthPercent(40)).toBe(50);
      expect(clampStripMaxWidthPercent(110)).toBe(100);
      expect(clampStripMaxWidthPercent(72.4)).toBe(72);
      expect(clampStripMaxWidthPercent(NaN)).toBe(100);
      expect(clampStripMaxWidthPercent(undefined)).toBe(100);
    });
  });

  describe('readStripWidthPercent / writeStripWidthPercentPerBook', () => {
    it('uses server-backed value when global CBX settings are enabled', () => {
      expect(readStripWidthPercent(1, true, 65, 99)).toBe(65);
      expect(readStripWidthPercent(1, true, undefined, 99)).toBe(100);
    });

    it('reads per-book legacy key when no user id', () => {
      localStorage.setItem('grimmory.cbx.stripWidth.book.7', '80');
      expect(readStripWidthPercent(7, false, undefined, null)).toBe(80);
    });

    it('reads namespaced key when user id is set', () => {
      localStorage.setItem('grimmory.cbx.stripWidth.u42.book.3', '55');
      expect(readStripWidthPercent(3, false, undefined, 42)).toBe(55);
    });

    it('falls back from namespaced key to legacy per-book key', () => {
      localStorage.setItem('grimmory.cbx.stripWidth.book.3', '60');
      expect(readStripWidthPercent(3, false, undefined, 42)).toBe(60);
    });

    it('writes namespaced storage when user id is provided', () => {
      writeStripWidthPercentPerBook(2, 88, 5);
      expect(localStorage.getItem('grimmory.cbx.stripWidth.u5.book.2')).toBe('88');
    });
  });

  describe('shouldOfferWebtoonHint', () => {
    it('returns false when session dismissed for book', () => {
      sessionStorage.setItem('grimmory.cbx.webtoonHint.sessionBook.9', '1');
      expect(shouldOfferWebtoonHint(9)).toBe(false);
    });

    it('returns false when never-again is set', () => {
      localStorage.setItem('grimmory.cbx.webtoonHint.never', '1');
      expect(shouldOfferWebtoonHint(1)).toBe(false);
    });
  });
});
