import { cva } from 'class-variance-authority';
import { LucideCircleAlert, LucideCircleCheck, LucideInfo, LucideTriangleAlert, type LucideIconData } from '@lucide/angular';
import { neutralControlBorderClass } from '../control.styles';

export type MessageColor = 'neutral' | 'primary' | 'green' | 'amber' | 'red';

export const messageVariants = cva('flex min-h-9 items-center gap-2 rounded-md border px-3 py-1.5 text-sm', {
  variants: {
    color: {
      neutral: `bg-card text-text-strong ${neutralControlBorderClass}`,
      primary: 'bg-primary/10 text-primary-text border-primary/15 dark:border-primary/30',
      green: 'bg-green-500/10 text-green-600 border-green-600/15 dark:text-green-400 dark:border-green-500/30',
      amber: 'bg-amber-500/10 text-amber-600 border-amber-600/15 dark:text-amber-400 dark:border-amber-500/30',
      red: 'bg-red-500/10 text-red-600 border-red-500/15 dark:text-red-400 dark:border-red-500/30',
    },
  },
  defaultVariants: { color: 'neutral' },
});

export const MESSAGE_ICONS: Record<MessageColor, LucideIconData> = {
  neutral: LucideInfo.icon,
  primary: LucideInfo.icon,
  green: LucideCircleCheck.icon,
  amber: LucideTriangleAlert.icon,
  red: LucideCircleAlert.icon,
};
