export interface AuthorSummary {
  id: number;
  name: string;
  asin?: string;
  bookCount: number;
  photoUpdatedOn?: string;
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

export interface AuthorSearchResult {
  source: string;
  asin: string;
  name: string;
  description?: string;
  imageUrl?: string;
}

export interface AuthorPhotoResult {
  url: string;
  width: number;
  height: number;
  index: number;
}
