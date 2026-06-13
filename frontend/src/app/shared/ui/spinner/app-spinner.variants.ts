import { cva } from 'class-variance-authority';

export type SpinnerSize = 'sm' | 'md' | 'lg';

export const SPINNER_RING = 'inline-block animate-spin rounded-full border-current border-t-transparent [animation-duration:0.85s]';

export const spinnerVariants = cva(SPINNER_RING, {
  variants: {
    size: {
      sm: 'size-4 border-2',
      md: 'size-6 border-2',
      lg: 'size-10 border-[3px]',
    },
  },
  defaultVariants: { size: 'md' },
});
