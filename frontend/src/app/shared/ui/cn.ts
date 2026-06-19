import { twMerge } from 'tailwind-merge';

export function cn(...classes: (string | false | null | undefined)[]): string {
  return twMerge(classes.filter(Boolean).join(' '));
}

type PtAttrs = Record<string, unknown> & { class?: string };

export function ptViewMerge(global: unknown, self: unknown, datasets?: unknown): Record<string, unknown> {
  const { class: globalClass, ...globalRest } = (global ?? {}) as PtAttrs;
  const { class: selfClass, ...selfRest } = (self ?? {}) as PtAttrs;
  const { class: datasetClass, ...datasetRest } = (datasets ?? {}) as PtAttrs;
  return { ...globalRest, ...selfRest, ...datasetRest, class: twMerge(globalClass, selfClass, datasetClass) };
}
