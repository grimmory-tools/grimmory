import { cva } from 'class-variance-authority';
import { controlMinHeightBySize, neutralControlBorderClass } from '../control.styles';

export const autocompleteBoxVariants = cva(
  `flex w-full flex-wrap items-center gap-1.5 rounded-md border ${neutralControlBorderClass} bg-card text-text-strong shadow-control outline-hidden ` +
    'transition-[background-color,border-color,color] duration-150 ' +
    'focus-within:border-primary/60 focus-within:outline-1 focus-within:outline-offset-0 focus-within:outline-primary/60',
  {
    variants: {
      size: {
        sm: `${controlMinHeightBySize.sm} px-3 py-1 text-xs pointer-coarse:text-base`,
        md: `${controlMinHeightBySize.md} px-3 py-1 text-sm pointer-coarse:text-base`,
        lg: `${controlMinHeightBySize.lg} px-4 py-1.5 text-sm pointer-coarse:text-base`,
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
