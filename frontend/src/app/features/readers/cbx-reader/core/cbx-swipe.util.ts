export interface CbxSwipeGesture {
  deltaX: number;
  deltaY: number;
  /** How far the page scroll container scrolled during the gesture. */
  scrollDeltaX: number;
  scrollDeltaY: number;
  threshold: number;
}

/** Scroll movement below this (px) is treated as sub-pixel rounding, not a pan. */
const SCROLL_EPSILON = 2;

/**
 * Page-turn only for mostly-horizontal swipes that didn't scroll the page container,
 * i.e. the page fits the screen or is already at its edge.
 */
export function isPageTurnSwipe(gesture: CbxSwipeGesture): boolean {
  const absX = Math.abs(gesture.deltaX);
  if (absX < gesture.threshold || absX <= Math.abs(gesture.deltaY)) return false;
  return Math.abs(gesture.scrollDeltaX) <= SCROLL_EPSILON && Math.abs(gesture.scrollDeltaY) <= SCROLL_EPSILON;
}
