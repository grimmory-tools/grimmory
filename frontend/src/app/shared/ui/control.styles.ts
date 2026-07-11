export const neutralControlBorderClass =
  'border-[color-mix(in_srgb,var(--color-text)_4%,var(--color-border))] dark:border-border';
export const neutralSurfaceHoverClass =
  'hover:bg-[color-mix(in_srgb,var(--color-text)_6%,var(--color-card))] ' +
  'dark:hover:bg-[color-mix(in_srgb,var(--color-text)_9%,var(--color-card))]';
export const invisibleControlInputClass =
  'peer absolute inset-0 z-10 m-0 size-full cursor-pointer opacity-0 disabled:cursor-not-allowed';

export const expandedTouchTargetInputClass =
  `${invisibleControlInputClass} pointer-coarse:left-1/2 pointer-coarse:top-1/2 ` +
  'pointer-coarse:-translate-x-1/2 pointer-coarse:-translate-y-1/2 ' +
  'pointer-coarse:h-11 pointer-coarse:w-[max(2.75rem,calc(100%+1.5rem))]';

export const controlHeightBySize = {
  sm: 'h-8 pointer-coarse:h-10',
  md: 'h-9 pointer-coarse:h-11',
  lg: 'h-10 pointer-coarse:h-12',
} as const;
export const controlMinHeightBySize = {
  sm: 'min-h-8 pointer-coarse:min-h-10',
  md: 'min-h-9 pointer-coarse:min-h-11',
  lg: 'min-h-10 pointer-coarse:min-h-12',
} as const;
export const controlIconOnlyWidthBySize = {
  sm: 'w-8 pointer-coarse:w-10',
  md: 'w-9 pointer-coarse:w-11',
  lg: 'w-10 pointer-coarse:w-12',
} as const;

export const pressedOverlayClass =
  'relative ' +
  "after:pointer-events-none after:absolute after:inset-0 after:rounded-[inherit] after:content-[''] " +
  'after:bg-black/10 after:opacity-0 after:transition-opacity after:duration-300 after:ease-out dark:after:bg-white/10 ' +
  'motion-reduce:after:transition-none ' +
  'pointer-coarse:active:after:opacity-100 pointer-coarse:active:after:duration-0';
