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

export interface ContentRestriction {
  id?: number;
  userId: number;
  restrictionType: ContentRestrictionType;
  mode: ContentRestrictionMode;
  value: string;
  createdAt?: string;
}
