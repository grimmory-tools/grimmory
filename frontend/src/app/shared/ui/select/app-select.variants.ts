import { cva } from 'class-variance-authority';
import { neutralControlBorderClass } from '../control.styles';

export type AppSelectSize = 'sm' | 'md' | 'lg';
export type AppSelectVariant = 'outlined' | 'bare';

export const appSelectTriggerVariants = cva(
  'relative inline-flex w-full cursor-pointer select-none items-center p-0 text-left font-normal ' +
    'outline-hidden transition-[background-color,border-color,color] duration-150',
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
          'focus-within:border-primary/60 focus-within:outline-1 focus-within:outline-offset-0 focus-within:outline-primary/60 ' +
          'data-[expanded=true]:border-primary/60',
        bare: 'rounded-none border-0 bg-transparent shadow-none',
      },
      invalid: {
        true: 'border-danger focus-within:border-danger focus-within:outline-danger data-[expanded=true]:border-danger',
        false: '',
      },
      disabled: {
        true: 'pointer-events-none opacity-50',
        false: '',
      },
    },
    compoundVariants: [
      { variant: 'outlined', size: 'sm', class: 'h-8' },
      { variant: 'outlined', size: 'md', class: 'h-9' },
      { variant: 'outlined', size: 'lg', class: 'h-10' },
      { variant: 'bare', size: 'sm', class: 'h-full min-h-8' },
      { variant: 'bare', size: 'md', class: 'h-full min-h-9' },
      { variant: 'bare', size: 'lg', class: 'h-full min-h-10' },
    ],
    defaultVariants: { size: 'md', variant: 'outlined', invalid: false, disabled: false },
  },
);

export const appSelectLabelVariants = cva(
  'block min-w-0 flex-auto truncate bg-transparent text-text-strong',
  {
    variants: {
      size: { sm: '', md: '', lg: '' },
      variant: { outlined: '', bare: '' },
    },
    compoundVariants: [
      { variant: 'outlined', size: 'sm', class: 'px-3 leading-8' },
      { variant: 'outlined', size: 'md', class: 'px-3 leading-9' },
      { variant: 'outlined', size: 'lg', class: 'px-4 leading-10' },
      { variant: 'bare', size: 'sm', class: 'px-3' },
      { variant: 'bare', size: 'md', class: 'px-4' },
      { variant: 'bare', size: 'lg', class: 'px-4' },
    ],
    defaultVariants: { size: 'md', variant: 'outlined' },
  },
);
