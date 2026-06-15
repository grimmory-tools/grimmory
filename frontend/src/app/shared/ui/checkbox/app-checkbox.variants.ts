import { cva } from 'class-variance-authority';
import { neutralControlBorderClass } from '../control.styles';

export type AppCheckboxSize = 'sm' | 'md' | 'lg';

export const appCheckboxRootVariants = cva('relative inline-grid shrink-0 place-items-center align-middle', {
  variants: {
    size: {
      sm: 'size-4',
      md: 'size-5',
      lg: 'size-6',
    },
  },
  defaultVariants: { size: 'md' },
});

export const appCheckboxBoxVariants = cva(
  `size-full rounded border ${neutralControlBorderClass} bg-card dark:bg-app shadow-control ` +
    'transition-[background-color,border-color,opacity] duration-150 ' +
    'peer-hover:border-primary/50 peer-checked:border-transparent peer-checked:bg-primary dark:peer-checked:bg-primary ' +
    'peer-checked:hover:bg-primary-hover peer-checked:active:bg-primary-active ' +
    'peer-indeterminate:border-transparent peer-indeterminate:bg-primary dark:peer-indeterminate:bg-primary ' +
    'peer-indeterminate:hover:bg-primary-hover peer-indeterminate:active:bg-primary-active ' +
    'peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-primary',
  {
    variants: {
      disabled: {
        true: 'opacity-50',
        false: '',
      },
      invalid: {
        true: 'border-danger',
        false: '',
      },
    },
    defaultVariants: { disabled: false, invalid: false },
  },
);

export const appCheckboxIndicatorVariants = cva(
  'pointer-events-none absolute inset-0 m-auto text-primary-contrast opacity-0 transition-opacity duration-150',
  {
    variants: {
      size: {
        sm: 'size-3',
        md: 'size-3.5',
        lg: 'size-4',
      },
    },
    defaultVariants: { size: 'md' },
  },
);
