import { cn } from '../cn';

export const checkIndicatorBaseClass =
  'flex size-3.5 shrink-0 items-center justify-center rounded border-[1.5px] pointer-coarse:size-4.5';
export const checkIndicatorUncheckedClass = 'border-text-muted/50';
export const checkIndicatorCheckedClass = 'border-transparent bg-primary text-primary-contrast';
export const checkIndicatorIconClass = 'size-2.5 shrink-0 pointer-coarse:size-3';

export function checkIndicatorClass(checked: boolean): string {
  return cn(
    checkIndicatorBaseClass,
    checked ? checkIndicatorCheckedClass : checkIndicatorUncheckedClass,
  );
}
