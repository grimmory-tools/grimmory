export interface AuthorSummary {
  id: number;
  name: string;
  asin?: string;
  bookCount: number;
  hasPhoto: boolean;
}

export interface AuthorDetail {
  id: number;
  name: string;
  description?: string;
  asin?: string;
  nameLocked: boolean;
  descriptionLocked: boolean;
  asinLocked: boolean;
  photoLocked: boolean;
}
