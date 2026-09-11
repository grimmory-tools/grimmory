import {type DestroyRef} from '@angular/core';

export function runOnNextTwoFrames(callback: () => void, destroyRef: DestroyRef): void {
  if (destroyRef.destroyed) return;
  let frame: number | undefined;
  const unregister = destroyRef.onDestroy(() => {
    if (frame !== undefined) cancelAnimationFrame(frame);
  });
  queueMicrotask(() => {
    if (destroyRef.destroyed) return;
    frame = requestAnimationFrame(() => {
      callback();
      if (destroyRef.destroyed) return;
      frame = requestAnimationFrame(() => {
        unregister();
        callback();
      });
    });
  });
}
