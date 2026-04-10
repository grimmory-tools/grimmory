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
  addedOn: string | null;
  lastReadTime: string | null;
  readProgress: number | null;
  primaryFileType: string | null;
  coverUpdatedOn: string | null;
  audiobookCoverUpdatedOn: string | null;
  isPhysical: boolean | null;
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
  filterMode?: 'and' | 'or' | 'not';
}

export interface AppBookSort {
  field: string;
  dir: 'asc' | 'desc';
}
