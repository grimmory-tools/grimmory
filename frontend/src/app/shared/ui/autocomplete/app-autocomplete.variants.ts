import { cva } from 'class-variance-authority';
import { neutralControlBorderClass } from '../control.styles';

export const autocompleteBoxVariants = cva(
  `flex w-full flex-wrap items-center gap-1.5 rounded-md border ${neutralControlBorderClass} bg-card text-text-strong shadow-control outline-hidden ` +
    'transition-[background-color,border-color,color] duration-150 ' +
    'focus-within:border-primary/60 focus-within:outline-1 focus-within:outline-offset-0 focus-within:outline-primary/60',
  {
    variants: {
      size: {
        sm: 'min-h-8 px-3 py-1 text-xs',
        md: 'min-h-9 px-3 py-1 text-sm',
        lg: 'min-h-10 px-4 py-1.5 text-sm',
      },
      disabled: { true: 'pointer-events-none opacity-50', false: '' },
      invalid: {
        true: 'border-danger focus-within:border-danger focus-within:outline-danger',
        false: '',
      },
    },
    defaultVariants: { size: 'md', disabled: false, invalid: false },
  },
);
