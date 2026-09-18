import { cva } from 'class-variance-authority';
import { controlHeightBySize } from '../control.styles';

export type TabsVariant = 'underline' | 'segmented';
export type TabsPlacement = 'inline' | 'below';
export type TabsSize = 'sm' | 'md' | 'lg';
export type TabsCollapse = 'auto' | 'always' | 'never' | 'scroll';

export const appTabsRootVariants = cva('relative', {
  variants: {
    placement: {
      inline: '',
      below: 'flex items-end',
    },
  },
  defaultVariants: { placement: 'inline' },
});

export const appTabsListVariants = cva('relative flex min-w-0', {
  variants: {
    variant: {
      underline: 'items-stretch gap-3 border-b border-border/70 md:gap-5',
      segmented: 'items-center gap-1 rounded-lg bg-text/5 p-1 dark:bg-text/5',
    },
    placement: {
      inline: '',
      below: '',
    },
  },
  compoundVariants: [
    { variant: 'underline', placement: 'below', class: 'w-full' },
    { variant: 'segmented', placement: 'below', class: 'mx-auto' },
  ],
  defaultVariants: { variant: 'underline', placement: 'inline' },
});

export const appTabVariants = cva(
  'relative z-10 inline-flex cursor-pointer items-center justify-center gap-1.5 whitespace-nowrap font-medium ' +
    'leading-5 transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary ' +
    'aria-selected:focus-visible:outline-none',
  {
    variants: {
      variant: {
        underline: 'text-text-secondary hover:text-text aria-selected:text-text-strong',
        segmented: 'text-text-secondary hover:text-text-strong aria-selected:text-primary-text',
      },
      size: { sm: '', md: '', lg: '' },
    },
    compoundVariants: [
      { variant: 'underline', size: 'sm', class: 'h-9 px-2.5 text-xs md:px-3 pointer-coarse:min-h-10' },
      { variant: 'underline', size: 'md', class: 'h-10 px-2.5 text-sm md:px-3.5 pointer-coarse:min-h-11' },
      { variant: 'underline', size: 'lg', class: 'h-11 px-4 text-sm pointer-coarse:min-h-12' },
      { variant: 'segmented', size: 'sm', class: `${controlHeightBySize.sm} px-3 text-xs` },
      { variant: 'segmented', size: 'md', class: `${controlHeightBySize.md} px-3 text-sm` },
      { variant: 'segmented', size: 'lg', class: `${controlHeightBySize.lg} px-4 text-sm` },
    ],
    defaultVariants: { variant: 'underline', size: 'md' },
  },
);
