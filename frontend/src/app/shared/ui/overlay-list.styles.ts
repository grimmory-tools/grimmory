import { cn } from './cn';

export const overlayListSurfaceClass =
  'z-50 overflow-visible rounded-md border border-border bg-card p-1 text-text shadow-pop';
export const overlayListRootClass = 'm-0 flex list-none flex-col p-0 outline-none';

const overlayListItemStateClass = 'hover:bg-surface-hover hover:text-text-strong';
export const overlayListActiveItemStateClass = 'bg-surface-hover text-text-strong';
export const overlayListItemRowClass =
  'flex h-7 w-full select-none items-center gap-2 px-2 py-0 text-sm leading-5 text-inherit no-underline outline-hidden';
export const overlayListItemContentClass = cn(
  'rounded-sm text-text',
  overlayListItemStateClass,
);
export const overlayListOptionClass = cn(
  overlayListItemRowClass,
  'cursor-pointer rounded-sm text-text',
  overlayListItemStateClass,
  'aria-disabled:pointer-events-none aria-disabled:opacity-50',
);
export const overlayListEmptyItemClass = 'flex h-7 items-center px-2 text-sm leading-5 text-text-muted';

export const overlayListSectionLabelClass =
  'flex h-5 w-full cursor-default select-none items-center px-2 py-0 text-xs font-semibold leading-5 text-inherit no-underline outline-hidden';

export const overlayListSeparatorClass = '-mx-1 my-0.5 h-px bg-border';
export const overlayListShortcutClass = 'ml-auto text-xs tracking-widest text-text-muted';
