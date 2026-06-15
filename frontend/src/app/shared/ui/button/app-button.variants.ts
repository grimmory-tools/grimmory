import { cva } from 'class-variance-authority';
import { neutralControlBorderClass, neutralSurfaceHoverClass } from '../control.styles';

export type ButtonTone = 'neutral' | 'primary' | 'danger';
export type ButtonVariant = 'soft' | 'solid' | 'ghost';
export type ButtonSize = 'sm' | 'md' | 'lg';

export const buttonVariants = cva(
  'inline-flex shrink-0 cursor-pointer select-none items-center justify-center ' +
    'rounded-md border leading-none whitespace-nowrap font-medium ' +
    'transition-[background-color,border-color,color] duration-150 active:translate-y-px ' +
    'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary ' +
    'disabled:pointer-events-none disabled:opacity-50',
  {
    variants: {
      tone: { neutral: '', primary: '', danger: '' },
      variant: { soft: '', solid: '', ghost: '' },
      size: {
        sm: 'h-8 px-3 gap-1.5 text-xs',
        md: 'h-9 px-3 gap-2 text-sm',
        lg: 'h-10 px-4 gap-2 text-sm',
      },
      iconOnly: { true: '', false: '' },
      rounded: { true: 'rounded-full', false: '' },
      fluid: { true: 'w-full', false: '' },
    },
    compoundVariants: [
      {
        tone: 'neutral',
        variant: ['soft', 'solid'],
        class: `bg-card text-text-strong ${neutralControlBorderClass} shadow-control ${neutralSurfaceHoverClass} hover:border-text/15`,
      },
      {
        tone: 'primary',
        variant: 'soft',
        class:
          'bg-primary/10 text-primary-text border-primary/15 shadow-control hover:bg-primary/20 dark:hover:bg-primary/15 hover:border-primary/30 dark:border-primary/30 dark:hover:border-primary/45',
      },
      {
        tone: 'primary',
        variant: 'solid',
        class: 'bg-primary text-primary-contrast border-transparent font-semibold shadow-control hover:bg-primary-hover',
      },
      {
        tone: 'danger',
        variant: 'soft',
        class:
          'bg-danger/10 text-danger border-danger/15 shadow-control hover:bg-danger/20 dark:hover:bg-danger/15 hover:border-danger/30 dark:border-danger/30 dark:hover:border-danger/45',
      },
      {
        tone: 'danger',
        variant: 'solid',
        class: 'bg-danger text-white border-transparent font-semibold shadow-control hover:bg-danger-hover',
      },
      {
        tone: 'neutral',
        variant: 'ghost',
        class: `bg-transparent border-transparent text-text-secondary ${neutralSurfaceHoverClass} hover:text-text-strong`,
      },
      { tone: 'primary', variant: 'ghost', class: 'bg-transparent border-transparent text-primary hover:bg-primary/10' },
      { tone: 'danger', variant: 'ghost', class: 'bg-transparent border-transparent text-danger hover:bg-danger/10' },
      { iconOnly: true, size: 'sm', class: 'w-8 px-0 gap-0' },
      { iconOnly: true, size: 'md', class: 'w-9 px-0 gap-0' },
      { iconOnly: true, size: 'lg', class: 'w-10 px-0 gap-0' },
    ],
    defaultVariants: { tone: 'neutral', variant: 'soft', size: 'md', iconOnly: false, rounded: false, fluid: false },
  },
);
