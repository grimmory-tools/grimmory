import { cva } from 'class-variance-authority';
import { neutralControlBorderClass } from '../control.styles';

export type AppSwitchSize = 'sm' | 'md' | 'lg';

export const appSwitchRootVariants = cva('relative inline-flex shrink-0 align-middle', {
  variants: {
    size: {
      sm: 'h-4 w-8',
      md: 'h-5 w-10',
      lg: 'h-6 w-12',
    },
  },
  defaultVariants: { size: 'md' },
});

export const appSwitchTrackVariants = cva(
  `absolute inset-0 rounded-full border ${neutralControlBorderClass} bg-text-muted/30 shadow-control ` +
    'transition-[background-color,border-color,opacity] duration-150 ' +
    'peer-checked:border-transparent peer-checked:bg-primary ' +
    'peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-primary',
  {
    variants: {
      disabled: {
        true: 'opacity-50',
        false: '',
      },
      invalid: {
        true: 'border-danger',
        false: '',
      },
    },
    defaultVariants: { disabled: false, invalid: false },
  },
);

export const appSwitchThumbVariants = cva(
  'pointer-events-none absolute left-0.5 top-1/2 -translate-y-1/2 translate-x-0 rounded-full bg-white shadow-sm ' +
    'transition-[opacity,translate,transform] duration-150',
  {
    variants: {
      size: {
        sm: 'size-3 peer-checked:translate-x-4',
        md: 'size-4 peer-checked:translate-x-5',
        lg: 'size-5 peer-checked:translate-x-6',
      },
      disabled: {
        true: 'opacity-50',
        false: '',
      },
    },
    defaultVariants: { size: 'md', disabled: false },
  },
);
