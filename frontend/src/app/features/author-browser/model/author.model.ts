export interface AuthorSummary {
  id: number;
  name: string;
  asin?: string;
  bookCount: number;
  hasPhoto: boolean;
}

export interface EnrichedAuthor extends AuthorSummary {
  libraryIds: Set<number>;
  libraryNames: string[];
  categories: string[];
  readStatus: 'all-read' | 'some-read' | 'in-progress' | 'unread';
  hasSeries: boolean;
  seriesCount: number;
  latestAddedOn: string | null;
  lastReadTime: string | null;
  readingProgress: number;
  avgPersonalRating: number | null;
}

export interface AuthorFilters {
  matchStatus: 'all' | 'matched' | 'unmatched';
  photoStatus: 'all' | 'has-photo' | 'no-photo';
  readStatus: 'all' | 'all-read' | 'some-read' | 'in-progress' | 'unread';
  bookCount: 'all' | '0' | '1' | '2' | '3' | '4' | '5' | '6-10' | '11-20' | '21-35' | '36+';
  library: string;
  genre: string;
}

export const DEFAULT_AUTHOR_FILTERS: AuthorFilters = {
  matchStatus: 'all',
  photoStatus: 'all',
  readStatus: 'all',
  bookCount: 'all',
  library: 'all',
  genre: 'all'
};

export interface AuthorDetails {
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

export interface AuthorMatchRequest {
  source: string;
  asin: string;
  region: string;
}

export interface AuthorPhotoResult {
  url: string;
  width: number;
  height: number;
  index: number;
}

export interface AuthorUpdateRequest {
  name?: string;
  description?: string;
  asin?: string;
  nameLocked?: boolean;
  descriptionLocked?: boolean;
  asinLocked?: boolean;
  photoLocked?: boolean;
}
