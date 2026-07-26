export const BOOK_FILE_TYPES = ['PDF', 'EPUB', 'CBX', 'FB2', 'MOBI', 'AZW3', 'AUDIOBOOK'] as const;
export type BookFileType = typeof BOOK_FILE_TYPES[number] | (string & {});

export const BOOK_ARCHIVE_TYPES = ['ZIP', 'RAR', 'SEVEN_ZIP', 'UNKNOWN'] as const;
export type BookArchiveType = typeof BOOK_ARCHIVE_TYPES[number] | (string & {});

export const BOOK_READ_STATUSES = [
  'UNREAD',
  'READING',
  'RE_READING',
  'READ',
  'PARTIALLY_READ',
  'PAUSED',
  'WONT_READ',
  'ABANDONED',
  'UNSET',
] as const;
export type KnownBookReadStatus = typeof BOOK_READ_STATUSES[number];
export type BookReadStatus = KnownBookReadStatus | (string & {});

export const BOOK_METADATA_PROVIDERS = [
  'Amazon',
  'GoodReads',
  'Google',
  'Hardcover',
  'Comicvine',
  'Douban',
  'Lubimyczytac',
  'Ranobedb',
  'Audible',
] as const;
export type BookMetadataProvider = typeof BOOK_METADATA_PROVIDERS[number] | (string & {});

export interface BookFileResponse {
  id: number;
  bookId: number;
  fileName?: string;
  filePath?: string;
  fileSubPath?: string;
  book: boolean;
  folderBased: boolean;
  bookType?: BookFileType;
  archiveType?: BookArchiveType;
  fileSizeKb?: number;
  extension?: string;
  description?: string;
  addedOn?: string;
}

export type BookShelfIconType = 'LUCIDE' | 'CUSTOM_SVG' | (string & {});

export type BookShelfSortDirection = 'ASCENDING' | 'DESCENDING';

export interface BookShelfSort {
  field: string | null;
  direction: BookShelfSortDirection | null;
}

export interface BookShelf {
  id: number;
  name: string;
  icon?: string;
  iconType?: BookShelfIconType;
  sort?: BookShelfSort;
  userId?: number;
  publicShelf: boolean;
  bookCount: number;
}

export interface BookLibraryPath {
  id: number;
}

export interface BookPdfProgress {
  page: number | null;
  percentage: number | null;
}

export interface BookEpubProgress {
  cfi: string | null;
  href: string | null;
  contentSourceProgressPercent: number | null;
  percentage: number | null;
  ttsPositionCfi: string | null;
}

export interface BookCbxProgress {
  page: number | null;
  percentage: number | null;
}

export interface BookAudiobookProgress {
  positionMs: number | null;
  trackIndex: number | null;
  trackPositionMs: number | null;
  percentage: number | null;
}

export interface BookKoReaderProgress {
  percentage: number | null;
}

export interface BookKoboProgress {
  percentage: number | null;
}

export interface BookSummaryAudiobookMetadata {
  durationSeconds?: number;
  bitrate?: number;
  sampleRate?: number;
  channels?: number;
  codec?: string;
  chapterCount?: number;
}

export interface BookSummaryComicMetadata {
  pencillers?: string[];
  inkers?: string[];
  colorists?: string[];
  letterers?: string[];
  coverArtists?: string[];
  editors?: string[];
  characters?: string[];
  teams?: string[];
  locations?: string[];
}

interface BookMetadataFields {
  bookId: number;
  title?: string;
  publisher?: string;
  publishedDate?: string;
  seriesName?: string;
  seriesNumber?: number;
  isbn13?: string;
  isbn10?: string;
  pageCount?: number;
  language?: string;
  narrator?: string;
  amazonRating?: number;
  amazonReviewCount?: number;
  goodreadsRating?: number;
  goodreadsReviewCount?: number;
  hardcoverRating?: number;
  hardcoverReviewCount?: number;
  ranobedbRating?: number;
  coverUpdatedOn?: string;
  audiobookCoverUpdatedOn?: string;
  authors?: string[];
  categories?: string[];
  moods?: string[];
  tags?: string[];
  rating?: number;
  isFixedLayout?: boolean;
  ageRating?: number;
  contentRating?: string;
}

export interface BookSummaryMetadata extends BookMetadataFields {
  audiobookMetadata?: BookSummaryAudiobookMetadata;
  comicMetadata?: BookSummaryComicMetadata;
  allMetadataLocked: boolean;
}

interface BookRecord<TMetadata extends BookMetadataFields> {
  id: number;
  libraryId: number;
  libraryName: string;
  primaryFile?: BookFileResponse;
  lastReadTime?: string;
  addedOn?: string;
  metadata?: TMetadata;
  metadataMatchScore?: number;
  pdfProgress?: BookPdfProgress;
  epubProgress?: BookEpubProgress;
  cbxProgress?: BookCbxProgress;
  audiobookProgress?: BookAudiobookProgress;
  koreaderProgress?: BookKoReaderProgress;
  koboProgress?: BookKoboProgress;
  personalRating?: number;
  shelves?: BookShelf[];
  readStatus?: BookReadStatus;
  dateFinished?: string;
  alternativeFormats?: BookFileResponse[];
  supplementaryFiles?: BookFileResponse[];
  isPhysical?: boolean;
}

export type BookSummary = BookRecord<BookSummaryMetadata>;

export interface BookDetailAudiobookChapter {
  index?: number;
  title?: string;
  startTimeMs?: number;
  endTimeMs?: number;
  durationMs?: number;
}

export interface BookDetailAudiobookMetadata extends BookSummaryAudiobookMetadata {
  chapters?: BookDetailAudiobookChapter[];
}

export interface BookDetailComicMetadata extends BookSummaryComicMetadata {
  issueNumber?: string;
  volumeName?: string;
  volumeNumber?: number;
  storyArc?: string;
  storyArcNumber?: number;
  alternateSeries?: string;
  alternateIssue?: string;
  imprint?: string;
  format?: string;
  blackAndWhite?: boolean;
  manga?: boolean;
  readingDirection?: string;
  webLink?: string;
  notes?: string;
  issueNumberLocked?: boolean;
  volumeNameLocked?: boolean;
  volumeNumberLocked?: boolean;
  storyArcLocked?: boolean;
  storyArcNumberLocked?: boolean;
  alternateSeriesLocked?: boolean;
  alternateIssueLocked?: boolean;
  imprintLocked?: boolean;
  formatLocked?: boolean;
  blackAndWhiteLocked?: boolean;
  mangaLocked?: boolean;
  readingDirectionLocked?: boolean;
  webLinkLocked?: boolean;
  notesLocked?: boolean;
  creatorsLocked?: boolean;
  pencillersLocked?: boolean;
  inkersLocked?: boolean;
  coloristsLocked?: boolean;
  letterersLocked?: boolean;
  coverArtistsLocked?: boolean;
  editorsLocked?: boolean;
  charactersLocked?: boolean;
  teamsLocked?: boolean;
  locationsLocked?: boolean;
}

export interface BookDetailMetadata extends BookMetadataFields {
  subtitle?: string;
  description?: string;
  seriesTotal?: number;
  asin?: string;
  goodreadsId?: string;
  comicvineId?: string;
  hardcoverId?: string;
  hardcoverBookId?: string;
  doubanId?: string;
  googleId?: string;
  lubimyczytacId?: string;
  ranobedbId?: string;
  audibleId?: string;
  doubanRating?: number;
  doubanReviewCount?: number;
  lubimyczytacRating?: number;
  audibleRating?: number;
  audibleReviewCount?: number;
  abridged?: boolean;
  audiobookMetadata?: BookDetailAudiobookMetadata;
  comicMetadata?: BookDetailComicMetadata;
  provider?: BookMetadataProvider;
  externalUrl?: string;
  thumbnailUrl?: string;
  titleLocked?: boolean;
  subtitleLocked?: boolean;
  publisherLocked?: boolean;
  publishedDateLocked?: boolean;
  descriptionLocked?: boolean;
  seriesNameLocked?: boolean;
  seriesNumberLocked?: boolean;
  seriesTotalLocked?: boolean;
  isbn13Locked?: boolean;
  isbn10Locked?: boolean;
  asinLocked?: boolean;
  goodreadsIdLocked?: boolean;
  comicvineIdLocked?: boolean;
  hardcoverIdLocked?: boolean;
  hardcoverBookIdLocked?: boolean;
  doubanIdLocked?: boolean;
  googleIdLocked?: boolean;
  pageCountLocked?: boolean;
  languageLocked?: boolean;
  amazonRatingLocked?: boolean;
  amazonReviewCountLocked?: boolean;
  goodreadsRatingLocked?: boolean;
  goodreadsReviewCountLocked?: boolean;
  hardcoverRatingLocked?: boolean;
  hardcoverReviewCountLocked?: boolean;
  doubanRatingLocked?: boolean;
  doubanReviewCountLocked?: boolean;
  lubimyczytacIdLocked?: boolean;
  lubimyczytacRatingLocked?: boolean;
  ranobedbIdLocked?: boolean;
  ranobedbRatingLocked?: boolean;
  audibleIdLocked?: boolean;
  audibleRatingLocked?: boolean;
  audibleReviewCountLocked?: boolean;
  externalUrlLocked?: boolean;
  coverLocked?: boolean;
  audiobookCoverLocked?: boolean;
  authorsLocked?: boolean;
  categoriesLocked?: boolean;
  moodsLocked?: boolean;
  tagsLocked?: boolean;
  reviewsLocked?: boolean;
  narratorLocked?: boolean;
  abridgedLocked?: boolean;
  ageRatingLocked?: boolean;
  contentRatingLocked?: boolean;
}

export interface BookDetail extends BookRecord<BookDetailMetadata> {
  libraryPath?: BookLibraryPath;
}

export interface BookRecommendation {
  book: BookDetail;
  similarityScore: number;
}
