import { cva } from 'class-variance-authority';
import { buttonVariants } from '../button/app-button.variants';
import { cn } from '../cn';
import { connectedItemClass } from '../connected-group';
import { neutralControlBorderClass } from '../control.styles';

export type AppRadioGroupVariant = 'list' | 'card' | 'segmented';
export type AppRadioGroupSize = 'sm' | 'md' | 'lg';

export const appRadioGroupRootVariants = cva('', {
  variants: {
    variant: {
      list: 'flex flex-col gap-2',
      card: 'flex flex-col gap-2',
      segmented: 'isolate inline-flex items-stretch',
    },
  },
  defaultVariants: { variant: 'list' },
});

const cardOption =
  'flex-row-reverse items-center justify-between gap-3 rounded-lg border ' +
  `${neutralControlBorderClass} ` +
  'bg-card px-4 py-3 shadow-control outline-hidden transition-[background-color,border-color] duration-150 ' +
  'has-[input:checked]:border-primary/15 has-[input:checked]:bg-primary/10 dark:has-[input:checked]:border-primary/30 ' +
  'has-[input:focus-visible]:outline-2 has-[input:focus-visible]:outline-offset-2 has-[input:focus-visible]:outline-primary';

interface AppRadioGroupSegmentState {
  size: AppRadioGroupSize;
  selected: boolean;
  first: boolean;
  last: boolean;
}

export function appRadioGroupSegmentClass(state: AppRadioGroupSegmentState): string {
  return cn(
    buttonVariants({ tone: state.selected ? 'primary' : 'neutral', variant: 'soft', size: state.size }),
    connectedItemClass({ first: state.first, last: state.last }),
    state.selected && 'app-cg-active',
    'outline-hidden',
    'has-[input:disabled]:cursor-not-allowed has-[input:disabled]:opacity-50',
    'has-[input:focus-visible]:z-10 has-[input:focus-visible]:outline-2',
    'has-[input:focus-visible]:outline-offset-2 has-[input:focus-visible]:outline-primary',
  );
}

export const appRadioGroupOptionVariants = cva(
  'relative flex cursor-pointer has-[input:disabled]:cursor-not-allowed has-[input:disabled]:opacity-50',
  {
    variants: {
      variant: {
        list: 'items-start gap-2.5',
        card: cardOption,
        segmented: '',
      },
      size: {
        sm: 'text-xs',
        md: 'text-sm',
        lg: 'text-sm',
      },
    },
    defaultVariants: { variant: 'list', size: 'md' },
  },
);

const dotBase =
  'relative flex shrink-0 items-center justify-center rounded-full border bg-card shadow-control ' +
  `${neutralControlBorderClass} ` +
  'transition-[background-color,border-color] duration-150 ' +
  'peer-checked:border-transparent peer-checked:bg-primary ' +
  "before:content-[''] before:rounded-full before:bg-primary-contrast before:opacity-0 before:transition-opacity before:duration-150 " +
  'peer-checked:before:opacity-100';

export const appRadioGroupDotVariants = cva(dotBase, {
  variants: {
    size: {
      sm: 'size-3.5 before:size-1',
      md: 'size-4 before:size-1.5',
      lg: 'size-5 before:size-2',
    },
    variant: {
      list:
        'peer-focus-visible:border-primary/60 peer-focus-visible:outline-1 ' +
        'peer-focus-visible:outline-offset-0 peer-focus-visible:outline-primary/60',
      card: '',
      segmented: '',
    },
  },
  compoundVariants: [
    { variant: 'list', size: 'sm', class: 'mt-px' },
    { variant: 'list', size: ['md', 'lg'], class: 'mt-0.5' },
  ],
  defaultVariants: { size: 'md', variant: 'list' },
});
