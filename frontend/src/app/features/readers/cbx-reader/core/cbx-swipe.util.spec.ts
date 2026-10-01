import {describe, expect, it} from 'vitest';
import {CbxSwipeGesture, isPageTurnSwipe} from './cbx-swipe.util';

function gesture(overrides: Partial<CbxSwipeGesture> = {}): CbxSwipeGesture {
  return {
    deltaX: -120,
    deltaY: 0,
    scrollDeltaX: 0,
    threshold: 75,
    ...overrides,
  };
}

describe('isPageTurnSwipe', () => {
  it('turns the page for a horizontal swipe that cannot scroll the page sideways', () => {
    expect(isPageTurnSwipe(gesture())).toBe(true);
    expect(isPageTurnSwipe(gesture({deltaX: 120}))).toBe(true);
  });

  it('ignores movement below the threshold', () => {
    expect(isPageTurnSwipe(gesture({deltaX: -50}))).toBe(false);
  });

  it('ignores mostly-vertical gestures', () => {
    expect(isPageTurnSwipe(gesture({deltaX: -90, deltaY: 200}))).toBe(false);
  });

  it('does not turn the page when the gesture panned a zoomed page horizontally', () => {
    expect(isPageTurnSwipe(gesture({deltaX: -300, scrollDeltaX: 300}))).toBe(false);
  });

  it('turns the page despite vertical drift, which may scroll a zoomed page vertically', () => {
    expect(isPageTurnSwipe(gesture({deltaX: -600, deltaY: 80}))).toBe(true);
  });

  it('treats sub-pixel scroll jitter as no pan', () => {
    expect(isPageTurnSwipe(gesture({scrollDeltaX: 1}))).toBe(true);
  });
});
