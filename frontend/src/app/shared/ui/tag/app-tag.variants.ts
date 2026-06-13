import { cva } from 'class-variance-authority';

export type TagColor =
  | 'neutral'
  | 'primary'
  | 'red'
  | 'amber'
  | 'green'
  | 'teal'
  | 'sky'
  | 'blue'
  | 'indigo'
  | 'purple'
  | 'pink';
export type TagSize = 'sm' | 'md';

export const tagVariants = cva(
  'inline-flex items-center gap-1 rounded-full border font-medium leading-none whitespace-nowrap',
  {
    variants: {
      color: {
        neutral: 'bg-text/5 text-text-secondary border-text/10 dark:bg-text/10',
        primary: 'bg-primary/10 text-primary-text border-primary/15 dark:border-primary/30',
        red: 'bg-red-500/10 text-red-700 border-red-500/15 dark:text-red-300 dark:border-red-400/20',
        amber: 'bg-amber-500/10 text-amber-700 border-amber-500/15 dark:text-amber-300 dark:border-amber-400/20',
        green: 'bg-green-500/10 text-green-700 border-green-500/15 dark:text-green-300 dark:border-green-400/20',
        teal: 'bg-teal-500/10 text-teal-700 border-teal-500/15 dark:text-teal-300 dark:border-teal-400/20',
        sky: 'bg-sky-500/10 text-sky-700 border-sky-500/15 dark:text-sky-300 dark:border-sky-400/20',
        blue: 'bg-blue-500/10 text-blue-700 border-blue-500/15 dark:text-blue-300 dark:border-blue-400/20',
        indigo: 'bg-indigo-500/10 text-indigo-700 border-indigo-500/15 dark:text-indigo-300 dark:border-indigo-400/20',
        purple: 'bg-purple-500/10 text-purple-700 border-purple-500/15 dark:text-purple-300 dark:border-purple-400/20',
        pink: 'bg-pink-500/10 text-pink-700 border-pink-500/15 dark:text-pink-300 dark:border-pink-400/20',
        custom: '',
      },
      size: {
        sm: 'px-1.5 py-0.5 text-xs',
        md: 'px-2 py-0.5 text-sm',
      },
      clickable: {
        true:
          'cursor-pointer transition-[background-color,border-color] duration-150 ' +
          'hover:border-current/40 active:translate-y-px ' +
          'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary',
        false: '',
      },
    },
    defaultVariants: { color: 'neutral', size: 'md', clickable: false },
  },
);

export const tagRemoveVariants = cva(
  '-my-0.5 inline-flex shrink-0 cursor-pointer items-center justify-center self-stretch rounded-r-full ' +
    'transition-colors hover:bg-current/15 ' +
    'focus-visible:outline-1 focus-visible:-outline-offset-1 focus-visible:outline-current',
  {
    variants: {
      size: {
        sm: '-mr-1.5 pl-1 pr-1.5',
        md: '-mr-2 pl-1.5 pr-2',
      },
    },
    defaultVariants: { size: 'md' },
  },
);
