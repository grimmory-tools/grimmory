import { cva } from 'class-variance-authority';

export type TabsVariant = 'underline' | 'segmented';
export type TabsPlacement = 'inline' | 'below';
export type TabsSize = 'sm' | 'md' | 'lg';
export type TabsCollapse = 'auto' | 'always' | 'never';

export const appTabsRootVariants = cva('relative', {
  variants: {
    placement: {
      inline: '',
      below: 'flex min-h-[3rem] items-end',
    },
  },
  defaultVariants: { placement: 'inline' },
});

export const appTabsListVariants = cva('relative flex min-w-0', {
  variants: {
    variant: {
      underline: 'items-stretch gap-5 border-b border-border/70',
      segmented: 'items-center gap-1 rounded-[10px] bg-text/[0.04] p-1 dark:bg-text/[0.04]',
    },
  },
  defaultVariants: { variant: 'underline' },
});

export const appTabVariants = cva(
  'relative z-10 inline-flex cursor-pointer items-center justify-center gap-1.5 whitespace-nowrap font-medium ' +
    'leading-none transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary ' +
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
      { variant: 'underline', size: 'sm', class: 'px-3 py-2 text-xs' },
      { variant: 'underline', size: 'md', class: 'px-3.5 py-2.5 text-sm' },
      { variant: 'underline', size: 'lg', class: 'px-4 py-3 text-sm' },
      { variant: 'segmented', size: 'sm', class: 'h-8 px-3 text-xs' },
      { variant: 'segmented', size: 'md', class: 'h-9 px-3 text-sm' },
      { variant: 'segmented', size: 'lg', class: 'h-10 px-4 text-sm' },
    ],
    defaultVariants: { variant: 'underline', size: 'md' },
  },
);
