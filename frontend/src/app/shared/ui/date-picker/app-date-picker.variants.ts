export type AppDatePickerSize = 'sm' | 'md' | 'lg';

export const appDatePickerDayBase =
  'relative flex size-9 items-center justify-center rounded-md border-0 bg-transparent text-sm font-normal text-text outline-hidden ' +
  'hover:bg-surface-hover hover:text-text-strong ' +
  'focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-primary ' +
  'disabled:pointer-events-none';

export const appDatePickerDaySelected = 'bg-primary/15 text-text-strong hover:bg-primary/25';

export const appDatePickerDayToday = 'font-semibold text-primary';
