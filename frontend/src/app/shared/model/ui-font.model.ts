export const UI_FONT_VALUES = ['default', 'atkinson'] as const;

export type UiFontPreference = (typeof UI_FONT_VALUES)[number];

export const DEFAULT_UI_FONT: UiFontPreference = 'default';
export const ACCESSIBLE_UI_FONT: UiFontPreference = 'atkinson';

export function normalizeUiFont(value: string | null | undefined): UiFontPreference {
  return UI_FONT_VALUES.includes(value as UiFontPreference) ? value as UiFontPreference : DEFAULT_UI_FONT;
}
