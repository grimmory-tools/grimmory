import {BookType} from './book.model';

export interface AppPageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

export interface AppBookSummary {
  id: number;
  title: string;
  authors: string[];
  thumbnailUrl: string | null;
  readStatus: string | null;
  personalRating: number | null;
  seriesName: string | null;
  seriesNumber: number | null;
  libraryId: number;
  libraryName: string;
  addedOn: string | null;
  lastReadTime: string | null;
  readProgress: number | null;
  primaryFileId: number | null;
  primaryFileType: BookType | null;
  primaryFileName: string | null;
  coverUpdatedOn: string | null;
  audiobookCoverUpdatedOn: string | null;
  isPhysical: boolean | null;
  publisher: string | null;
  categories: string[] | null;
  tags: string[] | null;
  moods: string[] | null;
  language: string | null;
  narrator: string | null;
  isbn13: string | null;
  isbn10: string | null;
  publishedDate: string | null;
  pageCount: number | null;
  ageRating: number | null;
  contentRating: string | null;
  metadataMatchScore: number | null;
  fileSizeKb: number | null;
  amazonRating: number | null;
  amazonReviewCount: number | null;
  goodreadsRating: number | null;
  goodreadsReviewCount: number | null;
  hardcoverRating: number | null;
  hardcoverReviewCount: number | null;
  ranobedbRating: number | null;
  lubimyczytacRating: number | null;
  audibleRating: number | null;
  audibleReviewCount: number | null;
  allMetadataLocked: boolean | null;
}

export interface AppFilterOptions {
  authors: CountedOption[];
  languages: LanguageOption[];
  readStatuses: CountedOption[];
  fileTypes: CountedOption[];
  categories: CountedOption[];
  publishers: CountedOption[];
  series: CountedOption[];
  tags: CountedOption[];
  moods: CountedOption[];
  narrators: CountedOption[];
  ageRatings: CountedOption[];
  contentRatings: CountedOption[];
  matchScores: CountedOption[];
  publishedYears: CountedOption[];
  fileSizes: CountedOption[];
  personalRatings: CountedOption[];
  amazonRatings: CountedOption[];
  goodreadsRatings: CountedOption[];
  hardcoverRatings: CountedOption[];
  lubimyczytacRatings: CountedOption[];
  ranobedbRatings: CountedOption[];
  audibleRatings: CountedOption[];
  pageCounts: CountedOption[];
  shelfStatuses: CountedOption[];
  comicCharacters: CountedOption[];
  comicTeams: CountedOption[];
  comicLocations: CountedOption[];
  comicCreators: CountedOption[];
  shelves: CountedOption[];
  libraries: CountedOption[];
}

export interface CountedOption {
  name: string;
  count: number;
}

export interface LanguageOption {
  code: string;
  label: string;
  count: number;
}

export interface AppBookFilters {
  libraryId?: number;
  shelfId?: number;
  magicShelfId?: number;
  unshelved?: boolean;
  status?: string[];
  search?: string;
  fileType?: string[];
  minRating?: number;
  maxRating?: number;
  authors?: string[];
  language?: string[];
  series?: string[];
  category?: string[];
  publisher?: string[];
  tag?: string[];
  mood?: string[];
  narrator?: string[];
  ageRating?: string[];
  contentRating?: string[];
  matchScore?: string[];
  publishedDate?: string[];
  fileSize?: string[];
  personalRating?: string[];
  amazonRating?: string[];
  goodreadsRating?: string[];
  hardcoverRating?: string[];
  lubimyczytacRating?: string[];
  ranobedbRating?: string[];
  audibleRating?: string[];
  pageCount?: string[];
  shelfStatus?: string[];
  comicCharacter?: string[];
  comicTeam?: string[];
  comicLocation?: string[];
  comicCreator?: string[];
  shelves?: string[];
  libraries?: string[];
  filterMode?: 'and' | 'or' | 'not';
}

export interface AppBookSort {
  field: string;
  dir: 'asc' | 'desc';
}
