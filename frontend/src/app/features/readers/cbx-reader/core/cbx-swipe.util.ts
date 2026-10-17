export interface CbxSwipeGesture {
  deltaX: number;
  deltaY: number;
  /** How far the page scroll container scrolled horizontally during the gesture. */
  scrollDeltaX: number;
  threshold: number;
}

/** Scroll movement below this (px) is treated as sub-pixel rounding, not a pan. */
const SCROLL_EPSILON = 2;

/**
 * Page-turn only for mostly-horizontal swipes that couldn't scroll the page sideways,
 * i.e. the page fits the screen or is already at its edge. Vertical drift that scrolls a
 * zoomed page doesn't count against the swipe.
 */
export function isPageTurnSwipe(gesture: CbxSwipeGesture): boolean {
  const absX = Math.abs(gesture.deltaX);
  if (absX < gesture.threshold || absX <= Math.abs(gesture.deltaY)) return false;
  return Math.abs(gesture.scrollDeltaX) <= SCROLL_EPSILON;
}
