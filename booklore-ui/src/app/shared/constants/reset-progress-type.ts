export const ResetProgressTypes = {
  KOREADER: 'KOREADER',
  // TODO(grimmory-cleanup): Switch backend/API payloads to emit a real GRIMMORY reset type and drop the BOOKLORE alias.
  GRIMMORY: 'BOOKLORE',
  KOBO: 'KOBO'
} as const;

export type ResetProgressType = keyof typeof ResetProgressTypes;
