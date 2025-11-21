export const ResetProgressTypes = {
  KOREADER: 'KOREADER',
  BOOKLORE: 'BOOKLORE',
  KOBO: 'KOBO'
} as const;

export type ResetProgressType = keyof typeof ResetProgressTypes;
