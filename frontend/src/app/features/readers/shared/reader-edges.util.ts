import {afterNextRender, DestroyRef, effect, inject} from '@angular/core';

export const isTouchScreen = () => matchMedia('(pointer: coarse)').matches;

export function setupReaderEdges(stripColor: () => string): () => void {
  let fullscreenPending = isTouchScreen();
  let scrollTimer: ReturnType<typeof setTimeout> | undefined;

  const requestFullscreen = () => {
    if (!fullscreenPending || !document.fullscreenEnabled) return;
    fullscreenPending = false;
    document.documentElement.requestFullscreen().catch(() => fullscreenPending = true);
  };

  const resetScroll = () => {
    window.scrollTo(0, 0);
    clearTimeout(scrollTimer);
    scrollTimer = setTimeout(() => window.scrollTo(0, 0), 400);
  };

  window.scrollTo(0, 0);
  afterNextRender(requestFullscreen);
  effect(() => {
    document.body.style.backgroundColor = stripColor();
  });
  document.addEventListener('click', requestFullscreen, true);
  window.addEventListener('resize', resetScroll);

  inject(DestroyRef).onDestroy(() => {
    document.removeEventListener('click', requestFullscreen, true);
    window.removeEventListener('resize', resetScroll);
    clearTimeout(scrollTimer);
    document.body.style.backgroundColor = '';
    if (document.fullscreenElement) void document.exitFullscreen().catch(() => undefined);
  });

  return requestFullscreen;
}
