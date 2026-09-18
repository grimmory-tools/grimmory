import { type ScrollStrategy } from '@angular/cdk/overlay';

let lockCount = 0;
let previousOverflow = '';
let previousPaddingRight = '';

function lockRoot(): void {
  if (++lockCount > 1) return;
  const root = document.documentElement;
  previousOverflow = root.style.overflow;
  previousPaddingRight = root.style.paddingRight;
  const scrollbarWidth = window.innerWidth - root.clientWidth;
  if (scrollbarWidth > 0) {
    const currentPaddingRight = Number.parseFloat(getComputedStyle(root).paddingRight) || 0;
    root.style.paddingRight = `${currentPaddingRight + scrollbarWidth}px`;
  }
  root.style.overflow = 'hidden';
}

function unlockRoot(): void {
  if (--lockCount > 0) return;
  const root = document.documentElement;
  root.style.overflow = previousOverflow;
  root.style.paddingRight = previousPaddingRight;
}

export function scrollLockStrategy(): ScrollStrategy {
  let enabled = false;
  return {
    attach: () => undefined,
    enable: () => {
      if (enabled) return;
      enabled = true;
      lockRoot();
    },
    disable: () => {
      if (!enabled) return;
      enabled = false;
      unlockRoot();
    },
  };
}
