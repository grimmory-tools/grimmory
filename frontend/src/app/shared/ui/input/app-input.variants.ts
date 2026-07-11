import { cva } from 'class-variance-authority';
import { controlHeightBySize, controlMinHeightBySize, neutralControlBorderClass } from '../control.styles';

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
        sm: 'text-xs pointer-coarse:text-base',
        md: 'text-sm pointer-coarse:text-base',
        lg: 'text-sm pointer-coarse:text-base',
      },
      variant: {
        outlined:
          `rounded-md border ${neutralControlBorderClass} bg-card shadow-control ` +
          'focus-within:border-primary/60 focus-within:outline-1 focus-within:outline-offset-0 focus-within:outline-primary/60 ' +
          'aria-invalid:border-danger aria-invalid:focus-within:border-danger aria-invalid:focus-within:outline-danger',
        bare: 'rounded-none border-0 bg-transparent shadow-none aria-invalid:text-danger',
      },
    },
    compoundVariants: [
      { variant: 'outlined', size: 'sm', class: `${controlHeightBySize.sm} px-3 leading-8` },
      { variant: 'outlined', size: 'md', class: `${controlHeightBySize.md} px-3 leading-9` },
      { variant: 'outlined', size: 'lg', class: `${controlHeightBySize.lg} px-4 leading-10` },
      { variant: 'bare', size: 'sm', class: `h-full ${controlMinHeightBySize.sm} px-3` },
      { variant: 'bare', size: 'md', class: `h-full ${controlMinHeightBySize.md} px-4` },
      { variant: 'bare', size: 'lg', class: `h-full ${controlMinHeightBySize.lg} px-4` },
    ],
    defaultVariants: { size: 'md', variant: 'outlined' },
  },
);
