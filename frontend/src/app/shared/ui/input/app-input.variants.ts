import { cva } from 'class-variance-authority';
import { neutralControlBorderClass } from '../control.styles';

export type AppInputSize = 'sm' | 'md' | 'lg';
export type AppInputVariant = 'outlined' | 'bare';

export const appInputVariants = cva(
  `block w-full text-text-strong outline-hidden ` +
    'placeholder:text-text-muted transition-[background-color,border-color,color] duration-150 ' +
    '[&::-webkit-search-cancel-button]:hidden [&::-webkit-search-decoration]:hidden ' +
    'disabled:pointer-events-none disabled:opacity-50',
  {
    variants: {
      size: {
        sm: 'text-xs',
        md: 'text-sm',
        lg: 'text-sm',
      },
      variant: {
        outlined:
          `rounded-md border ${neutralControlBorderClass} bg-card shadow-control ` +
          'focus:border-primary/60 focus:outline-1 focus:outline-offset-0 focus:outline-primary/60 ' +
          'aria-invalid:border-danger aria-invalid:focus:border-danger aria-invalid:focus:outline-danger',
        bare: 'rounded-none border-0 bg-transparent shadow-none aria-invalid:text-danger',
      },
    },
    compoundVariants: [
      { variant: 'outlined', size: 'sm', class: 'h-8 px-3 leading-8' },
      { variant: 'outlined', size: 'md', class: 'h-9 px-3 leading-9' },
      { variant: 'outlined', size: 'lg', class: 'h-10 px-4 leading-10' },
      { variant: 'bare', size: 'sm', class: 'h-full min-h-8 px-3' },
      { variant: 'bare', size: 'md', class: 'h-full min-h-9 px-4' },
      { variant: 'bare', size: 'lg', class: 'h-full min-h-10 px-4' },
    ],
    defaultVariants: { size: 'md', variant: 'outlined' },
  },
);
