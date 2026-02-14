import {Book, ReadStatus} from '../../../model/book.model';

// ============================================================================
// TYPES
// ============================================================================

export interface FilterValue {
  id?: string | number;
  name?: string;
  sortIndex?: number;
}

export interface Filter<T extends FilterValue = FilterValue> {
  value: T;
  bookCount: number;
}

export type FilterType =
  | 'author' | 'category' | 'series' | 'bookType' | 'readStatus'
  | 'personalRating' | 'publisher' | 'matchScore' | 'library' | 'shelf'
  | 'shelfStatus' | 'tag' | 'publishedDate' | 'fileSize' | 'amazonRating'
  | 'goodreadsRating' | 'hardcoverRating' | 'language' | 'pageCount' | 'mood'
  | 'ageRating' | 'contentRating'
  | 'narrator'
  | 'comicCharacter' | 'comicTeam' | 'comicLocation' | 'comicCreator';

export type SortMode = 'count' | 'sortIndex';

export interface RangeConfig {
  id: number;
  label: string;
  min: number;
  max: number;
  sortIndex: number;
}

export interface FilterConfig {
  label: string;
  extractor: (book: Book) => FilterValue[];
  sortMode: SortMode;
  isNumericId?: boolean;
}

// ============================================================================
// CONSTANTS
// ============================================================================

export const READ_STATUS_LABELS: Readonly<Record<ReadStatus, string>> = {
  [ReadStatus.UNREAD]: 'Unread',
  [ReadStatus.READING]: 'Reading',
  [ReadStatus.RE_READING]: 'Re-reading',
  [ReadStatus.PARTIALLY_READ]: 'Partially Read',
  [ReadStatus.PAUSED]: 'Paused',
  [ReadStatus.READ]: 'Read',
  [ReadStatus.WONT_READ]: 'Won\'t Read',
  [ReadStatus.ABANDONED]: 'Abandoned',
  [ReadStatus.UNSET]: 'Unset'
};

export const RATING_RANGES_5: readonly RangeConfig[] = [
  {id: 0, label: '0 to 1', min: 0, max: 1, sortIndex: 0},
  {id: 1, label: '1 to 2', min: 1, max: 2, sortIndex: 1},
  {id: 2, label: '2 to 3', min: 2, max: 3, sortIndex: 2},
  {id: 3, label: '3 to 4', min: 3, max: 4, sortIndex: 3},
  {id: 4, label: '4 to 4.5', min: 4, max: 4.5, sortIndex: 4},
  {id: 5, label: '4.5+', min: 4.5, max: Infinity, sortIndex: 5}
];

export const RATING_OPTIONS_10: readonly RangeConfig[] = Array.from({length: 10}, (_, i) => ({
  id: i + 1,
  label: `${i + 1}`,
  min: i + 1,
  max: i + 2,
  sortIndex: i
}));

export const FILE_SIZE_RANGES: readonly RangeConfig[] = [
  {id: 0, label: '< 1 MB', min: 0, max: 1024, sortIndex: 0},
  {id: 1, label: '1–10 MB', min: 1024, max: 10240, sortIndex: 1},
  {id: 2, label: '10–50 MB', min: 10240, max: 51200, sortIndex: 2},
  {id: 3, label: '50–100 MB', min: 51200, max: 102400, sortIndex: 3},
  {id: 4, label: '250–500 MB', min: 256000, max: 512000, sortIndex: 4},
  {id: 5, label: '0.5–1 GB', min: 512000, max: 1048576, sortIndex: 5},
  {id: 6, label: '1–2 GB', min: 1048576, max: 2097152, sortIndex: 6},
  {id: 7, label: '5+ GB', min: 5242880, max: Infinity, sortIndex: 7}
];

export const PAGE_COUNT_RANGES: readonly RangeConfig[] = [
  {id: 0, label: '< 50 pages', min: 0, max: 50, sortIndex: 0},
  {id: 1, label: '50–100 pages', min: 50, max: 100, sortIndex: 1},
  {id: 2, label: '100–200 pages', min: 100, max: 200, sortIndex: 2},
  {id: 3, label: '200–400 pages', min: 200, max: 400, sortIndex: 3},
  {id: 4, label: '400–600 pages', min: 400, max: 600, sortIndex: 4},
  {id: 5, label: '600–1000 pages', min: 600, max: 1000, sortIndex: 5},
  {id: 6, label: '1000+ pages', min: 1000, max: Infinity, sortIndex: 6}
];

export const MATCH_SCORE_RANGES: readonly RangeConfig[] = [
  {id: 0, min: 0.95, max: 1.01, label: 'Outstanding (95–100%)', sortIndex: 0},
  {id: 1, min: 0.90, max: 0.95, label: 'Excellent (90–94%)', sortIndex: 1},
  {id: 2, min: 0.80, max: 0.90, label: 'Great (80–89%)', sortIndex: 2},
  {id: 3, min: 0.70, max: 0.80, label: 'Good (70–79%)', sortIndex: 3},
  {id: 4, min: 0.50, max: 0.70, label: 'Fair (50–69%)', sortIndex: 4},
  {id: 5, min: 0.30, max: 0.50, label: 'Weak (30–49%)', sortIndex: 5},
  {id: 6, min: 0.00, max: 0.30, label: 'Poor (0–29%)', sortIndex: 6}
];

export const AGE_RATING_OPTIONS: readonly RangeConfig[] = [
  {id: 0, min: 0, max: 1, label: 'All Ages', sortIndex: 0},
  {id: 6, min: 6, max: 7, label: '6+', sortIndex: 1},
  {id: 10, min: 10, max: 11, label: '10+', sortIndex: 2},
  {id: 13, min: 13, max: 14, label: '13+', sortIndex: 3},
  {id: 16, min: 16, max: 17, label: '16+', sortIndex: 4},
  {id: 18, min: 18, max: 19, label: '18+', sortIndex: 5},
  {id: 21, min: 21, max: 22, label: '21+', sortIndex: 6}
];

export const CONTENT_RATING_LABELS: Readonly<Record<string, string>> = {
  'EVERYONE': 'Everyone',
  'TEEN': 'Teen',
  'MATURE': 'Mature',
  'ADULT': 'Adult',
  'EXPLICIT': 'Explicit'
};

export const readStatusLabels = READ_STATUS_LABELS;
export const ratingRanges = RATING_RANGES_5;
export const ratingOptions10 = RATING_OPTIONS_10;
export const fileSizeRanges = FILE_SIZE_RANGES;
export const pageCountRanges = PAGE_COUNT_RANGES;
export const matchScoreRanges = MATCH_SCORE_RANGES;

export const NUMERIC_ID_FILTER_TYPES = new Set<FilterType>([
  'personalRating', 'matchScore', 'fileSize', 'amazonRating',
  'goodreadsRating', 'hardcoverRating', 'pageCount', 'library', 'shelf',
  'ageRating'
]);

export const FILTER_LABELS: Readonly<Record<FilterType, string>> = {
  author: 'Author',
  category: 'Genre',
  series: 'Series',
  bookType: 'Book Type',
  readStatus: 'Read Status',
  personalRating: 'Personal Rating',
  publisher: 'Publisher',
  matchScore: 'Metadata Match Score',
  library: 'Library',
  shelf: 'Shelf',
  shelfStatus: 'Shelf Status',
  tag: 'Tag',
  publishedDate: 'Published Year',
  fileSize: 'File Size',
  amazonRating: 'Amazon Rating',
  goodreadsRating: 'Goodreads Rating',
  hardcoverRating: 'Hardcover Rating',
  language: 'Language',
  pageCount: 'Page Count',
  mood: 'Mood',
  ageRating: 'Age Rating',
  contentRating: 'Content Rating',
  narrator: 'Narrator',
  comicCharacter: 'Comic Character',
  comicTeam: 'Comic Team',
  comicLocation: 'Comic Location',
  comicCreator: 'Comic Creator'
};

// ============================================================================
// FILTER EXTRACTORS
// ============================================================================

const findInRange = (value: number | null | undefined, ranges: readonly RangeConfig[]): FilterValue[] => {
  if (value == null) return [];
  const match = ranges.find(r => value >= r.min && value < r.max);
  return match ? [{id: match.id, name: match.label, sortIndex: match.sortIndex}] : [];
};

const findExactRating10 = (rating: number | undefined): FilterValue[] => {
  if (!rating || rating < 1 || rating > 10) return [];
  const match = RATING_OPTIONS_10.find(r => r.id === rating);
  return match ? [{id: match.id, name: match.label, sortIndex: match.sortIndex}] : [];
};

const normalizeMatchScore = (score: number | null | undefined): number | null => {
  if (score == null) return null;
  return score > 1 ? score / 100 : score;
};

const extractStringsAsFilters = (values: string[] | undefined): FilterValue[] =>
  values?.map(name => ({id: name, name})) ?? [];

const extractSingleString = (value: string | undefined | null): FilterValue[] =>
  value ? [{id: value, name: value}] : [];

const findExactAgeRating = (ageRating: number | null | undefined): FilterValue[] => {
  if (ageRating == null) return [];
  const match = AGE_RATING_OPTIONS.find(r => r.id === ageRating);
  return match ? [{id: match.id, name: match.label, sortIndex: match.sortIndex}] : [];
};

export const FILTER_EXTRACTORS: Readonly<Record<Exclude<FilterType, 'library'>, (book: Book) => FilterValue[]>> = {
  author: (book) => extractStringsAsFilters(book.metadata?.authors),
  category: (book) => extractStringsAsFilters(book.metadata?.categories),
  series: (book) => extractSingleString(book.metadata?.seriesName),
  bookType: (book) => extractSingleString(book.primaryFile?.bookType),
  readStatus: (book) => {
    const status = book.readStatus ?? ReadStatus.UNSET;
    const validStatus = status in READ_STATUS_LABELS ? status : ReadStatus.UNSET;
    return [{id: validStatus, name: READ_STATUS_LABELS[validStatus]}];
  },
  personalRating: (book) => findExactRating10(book.personalRating ?? undefined),
  publisher: (book) => extractSingleString(book.metadata?.publisher),
  matchScore: (book) => findInRange(normalizeMatchScore(book.metadataMatchScore), MATCH_SCORE_RANGES),
  shelf: (book) => book.shelves?.map(s => ({id: s.id, name: s.name})) ?? [],
  shelfStatus: (book) => {
    const isShelved = (book.shelves?.length ?? 0) > 0;
    return [{id: isShelved ? 'shelved' : 'unshelved', name: isShelved ? 'Shelved' : 'Unshelved'}];
  },
  tag: (book) => extractStringsAsFilters(book.metadata?.tags),
  publishedDate: (book) => {
    const date = book.metadata?.publishedDate;
    if (!date) return [];
    const year = new Date(date).getFullYear().toString();
    return [{id: year, name: year}];
  },
  fileSize: (book) => findInRange(book.fileSizeKb, FILE_SIZE_RANGES),
  amazonRating: (book) => findInRange(book.metadata?.amazonRating, RATING_RANGES_5),
  goodreadsRating: (book) => findInRange(book.metadata?.goodreadsRating, RATING_RANGES_5),
  hardcoverRating: (book) => findInRange(book.metadata?.hardcoverRating, RATING_RANGES_5),
  language: (book) => extractSingleString(book.metadata?.language),
  pageCount: (book) => findInRange(book.metadata?.pageCount, PAGE_COUNT_RANGES),
  mood: (book) => extractStringsAsFilters(book.metadata?.moods),
  ageRating: (book) => findExactAgeRating(book.metadata?.ageRating),
  contentRating: (book) => {
    const rating = book.metadata?.contentRating;
    if (!rating) return [];
    const label = CONTENT_RATING_LABELS[rating] ?? rating;
    return [{id: rating, name: label}];
  },
  narrator: (book) => extractSingleString(book.metadata?.narrator),
  comicCharacter: (book) => extractStringsAsFilters(book.metadata?.comicMetadata?.characters),
  comicTeam: (book) => extractStringsAsFilters(book.metadata?.comicMetadata?.teams),
  comicLocation: (book) => extractStringsAsFilters(book.metadata?.comicMetadata?.locations),
  comicCreator: (book) => {
    const comic = book.metadata?.comicMetadata;
    if (!comic) return [];
    const creators: FilterValue[] = [];
    const roleLabels: Record<string, string> = {
      penciller: 'Penciller', inker: 'Inker', colorist: 'Colorist',
      letterer: 'Letterer', coverArtist: 'Cover Artist', editor: 'Editor'
    };
    const roles: [string[] | undefined, string][] = [
      [comic.pencillers, 'penciller'],
      [comic.inkers, 'inker'],
      [comic.colorists, 'colorist'],
      [comic.letterers, 'letterer'],
      [comic.coverArtists, 'coverArtist'],
      [comic.editors, 'editor']
    ];
    for (const [names, role] of roles) {
      if (names) {
        for (const name of names) {
          creators.push({id: `${name}:${role}`, name: `${name} (${roleLabels[role]})`});
        }
      }
    }
    return creators;
  }
};

// Translation key for each FilterType — used by UI components to translate filter labels
export const FILTER_LABEL_KEYS: Readonly<Record<FilterType, string>> = {
  author: 'book.filter.labels.author',
  category: 'book.filter.labels.category',
  series: 'book.filter.labels.series',
  bookType: 'book.filter.labels.bookType',
  readStatus: 'book.filter.labels.readStatus',
  personalRating: 'book.filter.labels.personalRating',
  publisher: 'book.filter.labels.publisher',
  matchScore: 'book.filter.labels.matchScore',
  library: 'book.filter.labels.library',
  shelf: 'book.filter.labels.shelf',
  shelfStatus: 'book.filter.labels.shelfStatus',
  tag: 'book.filter.labels.tag',
  publishedDate: 'book.filter.labels.publishedDate',
  fileSize: 'book.filter.labels.fileSize',
  amazonRating: 'book.filter.labels.amazonRating',
  goodreadsRating: 'book.filter.labels.goodreadsRating',
  hardcoverRating: 'book.filter.labels.hardcoverRating',
  language: 'book.filter.labels.language',
  pageCount: 'book.filter.labels.pageCount',
  mood: 'book.filter.labels.mood',
  ageRating: 'book.filter.labels.ageRating',
  contentRating: 'book.filter.labels.contentRating',
  narrator: 'book.filter.labels.narrator',
  comicCharacter: 'book.filter.labels.comicCharacter',
  comicTeam: 'book.filter.labels.comicTeam',
  comicLocation: 'book.filter.labels.comicLocation',
  comicCreator: 'book.filter.labels.comicCreator'
};

export const READ_STATUS_LABEL_KEYS: Readonly<Record<ReadStatus, string>> = {
  [ReadStatus.UNREAD]: 'book.filter.readStatus.unread',
  [ReadStatus.READING]: 'book.filter.readStatus.reading',
  [ReadStatus.RE_READING]: 'book.filter.readStatus.reReading',
  [ReadStatus.PARTIALLY_READ]: 'book.filter.readStatus.partiallyRead',
  [ReadStatus.PAUSED]: 'book.filter.readStatus.paused',
  [ReadStatus.READ]: 'book.filter.readStatus.read',
  [ReadStatus.WONT_READ]: 'book.filter.readStatus.wontRead',
  [ReadStatus.ABANDONED]: 'book.filter.readStatus.abandoned',
  [ReadStatus.UNSET]: 'book.filter.readStatus.unset'
};

export const CONTENT_RATING_LABEL_KEYS: Readonly<Record<string, string>> = {
  'EVERYONE': 'book.filter.contentRating.everyone',
  'TEEN': 'book.filter.contentRating.teen',
  'MATURE': 'book.filter.contentRating.mature',
  'ADULT': 'book.filter.contentRating.adult',
  'EXPLICIT': 'book.filter.contentRating.explicit'
};

export const SHELF_STATUS_LABEL_KEYS: Readonly<Record<string, string>> = {
  'shelved': 'book.filter.shelfStatus.shelved',
  'unshelved': 'book.filter.shelfStatus.unshelved'
};

export const COMIC_ROLE_LABEL_KEYS: Readonly<Record<string, string>> = {
  penciller: 'book.filter.comicRoles.penciller',
  inker: 'book.filter.comicRoles.inker',
  colorist: 'book.filter.comicRoles.colorist',
  letterer: 'book.filter.comicRoles.letterer',
  coverArtist: 'book.filter.comicRoles.coverArtist',
  editor: 'book.filter.comicRoles.editor'
};

export const FILTER_CONFIGS: Readonly<Record<Exclude<FilterType, 'library'>, Omit<FilterConfig, 'extractor'>>> = {
  author: {label: 'Author', sortMode: 'count'},
  category: {label: 'Genre', sortMode: 'count'},
  series: {label: 'Series', sortMode: 'count'},
  bookType: {label: 'Book Type', sortMode: 'count'},
  readStatus: {label: 'Read Status', sortMode: 'count'},
  personalRating: {label: 'Personal Rating', sortMode: 'sortIndex', isNumericId: true},
  publisher: {label: 'Publisher', sortMode: 'count'},
  matchScore: {label: 'Metadata Match Score', sortMode: 'sortIndex', isNumericId: true},
  shelf: {label: 'Shelf', sortMode: 'count', isNumericId: true},
  shelfStatus: {label: 'Shelf Status', sortMode: 'count'},
  tag: {label: 'Tag', sortMode: 'count'},
  publishedDate: {label: 'Published Year', sortMode: 'count'},
  fileSize: {label: 'File Size', sortMode: 'sortIndex', isNumericId: true},
  amazonRating: {label: 'Amazon Rating', sortMode: 'sortIndex', isNumericId: true},
  goodreadsRating: {label: 'Goodreads Rating', sortMode: 'sortIndex', isNumericId: true},
  hardcoverRating: {label: 'Hardcover Rating', sortMode: 'sortIndex', isNumericId: true},
  language: {label: 'Language', sortMode: 'count'},
  pageCount: {label: 'Page Count', sortMode: 'sortIndex', isNumericId: true},
  mood: {label: 'Mood', sortMode: 'count'},
  ageRating: {label: 'Age Rating', sortMode: 'sortIndex', isNumericId: true},
  contentRating: {label: 'Content Rating', sortMode: 'count'},
  narrator: {label: 'Narrator', sortMode: 'count'},
  comicCharacter: {label: 'Comic Character', sortMode: 'count'},
  comicTeam: {label: 'Comic Team', sortMode: 'count'},
  comicLocation: {label: 'Comic Location', sortMode: 'count'},
  comicCreator: {label: 'Comic Creator', sortMode: 'count'}
};
