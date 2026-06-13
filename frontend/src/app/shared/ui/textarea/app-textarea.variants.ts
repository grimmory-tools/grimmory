import { cva } from 'class-variance-authority';
import { neutralControlBorderClass } from '../control.styles';

export const appTextareaVariants = cva(
  `block w-full rounded-md border ${neutralControlBorderClass} bg-card text-text-strong shadow-control outline-hidden ` +
    'px-3 py-[0.4375rem] text-sm leading-5 ' +
    'placeholder:text-text-muted transition-[background-color,border-color,color] duration-150 ' +
    'focus:border-primary/60 focus:outline-1 focus:outline-offset-0 focus:outline-primary/60 ' +
    'disabled:pointer-events-none disabled:opacity-50 aria-invalid:border-danger aria-invalid:focus:border-danger aria-invalid:focus:outline-danger',
  {
    variants: {
      autoResize: {
        true: 'resize-none overflow-hidden',
        false: 'resize-y',
      },
    },
    defaultVariants: { autoResize: false },
  },
);
