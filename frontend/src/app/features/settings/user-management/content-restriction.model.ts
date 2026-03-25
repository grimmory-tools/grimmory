export enum ContentRestrictionType {
  CATEGORY = 'CATEGORY',
  TAG = 'TAG',
  MOOD = 'MOOD',
  AGE_RATING = 'AGE_RATING',
  CONTENT_RATING = 'CONTENT_RATING'
}

export enum ContentRestrictionMode {
  EXCLUDE = 'EXCLUDE',
  ALLOW_ONLY = 'ALLOW_ONLY'
}

export enum ContentRating {
  EVERYONE = 'EVERYONE',
  TEEN = 'TEEN',
  MATURE = 'MATURE',
  ADULT = 'ADULT',
  EXPLICIT = 'EXPLICIT'
}

export interface ContentRestriction {
  id?: number;
  userId: number;
  restrictionType: ContentRestrictionType;
  mode: ContentRestrictionMode;
  value: string;
  createdAt?: string;
}

export const CONTENT_RATINGS = Object.values(ContentRating);

export const AGE_RATING_OPTIONS = [
  { value: '0', label: 'All Ages' },
  { value: '6', label: '6+' },
  { value: '10', label: '10+' },
  { value: '13', label: '13+' },
  { value: '16', label: '16+' },
  { value: '18', label: '18+' },
  { value: '21', label: '21+' }
];
