import {computed, effect, signal, type Signal} from '@angular/core';

import {type BrowseStatus} from './results';

const SKELETON_DELAY_MS = 180;

export function createBrowseSkeletonDelay(status: Signal<BrowseStatus>, hasItems: Signal<boolean>): Signal<boolean> {
  const delayElapsed = signal(false);
  let hasEverLoadedItems = false;

  effect(onCleanup => {
    if (hasItems()) {
      hasEverLoadedItems = true;
    }
    if (status() !== 'pending' || hasItems()) {
      delayElapsed.set(false);
      return;
    }
    if (hasEverLoadedItems) {
      delayElapsed.set(true);
      return;
    }
    const timer = setTimeout(() => delayElapsed.set(true), SKELETON_DELAY_MS);
    onCleanup(() => clearTimeout(timer));
  });

  return computed(() => status() === 'pending' && !hasItems() && delayElapsed());
}
