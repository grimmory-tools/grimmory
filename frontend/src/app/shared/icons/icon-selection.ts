export type IconType = 'LUCIDE' | 'CUSTOM_SVG';

export interface IconSelection {
  type: IconType;
  value: string;
}

export const toIconSelection = (value: string, type: IconType | null | undefined): IconSelection =>
  type === 'CUSTOM_SVG' ? {type: 'CUSTOM_SVG', value} : {type: 'LUCIDE', value};
