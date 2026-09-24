export interface CbxSwipeGesture {
  deltaX: number;
  deltaY: number;
  /** Horizontal scroll offset of the page scroll container when the touch started. */
  startScrollLeft: number;
  startScrollTop: number;
  /** Horizontal scroll offset of the page scroll container when the touch ended. */
  endScrollLeft: number;
  endScrollTop: number;
  threshold: number;
}

/** Scroll movement below this (px) is treated as sub-pixel rounding, not a pan. */
const SCROLL_EPSILON = 2;

/**
 * Decide whether a touch gesture should turn the page.
 *
 * A gesture that scrolled the page container (panning a zoomed / overflowing page) is a pan,
 * not a swipe. Page turns only happen for mostly-horizontal swipes that could not scroll the
 * content any further, e.g. when the page fits the screen or is already at its edge.
 */
export function isPageTurnSwipe(gesture: CbxSwipeGesture): boolean {
  const absX = Math.abs(gesture.deltaX);
  if (absX < gesture.threshold) return false;
  if (absX <= Math.abs(gesture.deltaY)) return false;

  const pannedX = Math.abs(gesture.endScrollLeft - gesture.startScrollLeft) > SCROLL_EPSILON;
  const pannedY = Math.abs(gesture.endScrollTop - gesture.startScrollTop) > SCROLL_EPSILON;
  return !pannedX && !pannedY;
}
