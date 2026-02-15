import {Shelf} from './shelf.model';
import {CbxBackgroundColor, CbxFitMode, CbxPageSpread, CbxPageViewMode, CbxScrollMode, NewPdfReaderSetting} from '../../settings/user-management/user.service';
import {BookReview} from '../components/book-reviews/book-review-service';
import {ZoomType} from 'ngx-extended-pdf-viewer';

export type BookType = "PDF" | "EPUB" | "CBX" | "FB2" | "MOBI" | "AZW3" | "AUDIOBOOK";

export enum AdditionalFileType {
  ALTERNATIVE_FORMAT = 'ALTERNATIVE_FORMAT',
  SUPPLEMENTARY = 'SUPPLEMENTARY'
}

export interface FileInfo {
  fileName?: string;
  filePath?: string;
  fileSubPath?: string;
  fileSizeKb?: number;
}

export interface BookFile extends FileInfo {
  id: number;
  bookId: number;
  bookType?: BookType;
  folderBased?: boolean;
  extension?: string;
  addedOn?: string;
}

export interface AdditionalFile extends BookFile {
  additionalFileType?: AdditionalFileType;
  description?: string;
}

export interface Book extends FileInfo {
  id: number;
  primaryFile?: BookFile;
  libraryId: number;
  libraryName: string;
  metadata?: BookMetadata;
  shelves?: Shelf[];
  lastReadTime?: string;
  addedOn?: string;
  epubProgress?: EpubProgress;
  pdfProgress?: PdfProgress;
  cbxProgress?: CbxProgress;
  audiobookProgress?: AudiobookProgress;
  koreaderProgress?: KoReaderProgress;
  koboProgress?: KoboProgress;
  seriesCount?: number | null;
  seriesBooks?: Book[] | null;
  metadataMatchScore?: number | null;
  personalRating?: number | null;
  readStatus?: ReadStatus;
  dateFinished?: string;
  libraryPath?: { id: number };
  alternativeFormats?: AdditionalFile[];
  supplementaryFiles?: AdditionalFile[];
  isPhysical?: boolean;

  [key: string]: unknown;
}

export interface EpubProgress {
  cfi: string;
  percentage: number;
}

export interface PdfProgress {
  page: number;
  percentage: number;
}

export interface CbxProgress {
  page: number;
  percentage: number;
}

export interface KoReaderProgress {
  percentage: number;
}

export interface KoboProgress {
  percentage: number;
}

export interface AudiobookProgress {
  positionMs: number;
  trackIndex?: number;
  trackPositionMs?: number;
  percentage: number;
}

export interface BookFileProgress {
  bookFileId: number;
  positionData?: string;
  positionHref?: string;
  progressPercent: number;
}

export interface AudiobookMetadata {
  narrator?: string;
  abridged?: boolean | null;
  durationSeconds?: number;
  bitrate?: number;
  sampleRate?: number;
  channels?: number;
  codec?: string;
  chapterCount?: number;
  narratorLocked?: boolean;
  abridgedLocked?: boolean;
}

export interface ComicMetadata {
  issueNumber?: string;
  volumeName?: string;
  volumeNumber?: number;
  storyArc?: string;
  storyArcNumber?: number;
  alternateSeries?: string;
  alternateIssue?: string;
  pencillers?: string[];
  inkers?: string[];
  colorists?: string[];
  letterers?: string[];
  coverArtists?: string[];
  editors?: string[];
  imprint?: string;
  format?: string;
  blackAndWhite?: boolean;
  manga?: boolean;
  readingDirection?: string;
  characters?: string[];
  teams?: string[];
  locations?: string[];
  webLink?: string;
  notes?: string;
  issueNumberLocked?: boolean;
  volumeNameLocked?: boolean;
  volumeNumberLocked?: boolean;
  storyArcLocked?: boolean;
  creatorsLocked?: boolean;
  charactersLocked?: boolean;
  teamsLocked?: boolean;
  locationsLocked?: boolean;
}

export interface BookMetadata {
  bookId: number;
  title?: string;
  subtitle?: string;
  publisher?: string;
  publishedDate?: string;
  description?: string;
  seriesName?: string;
  seriesNumber?: number | null;
  seriesTotal?: number | null;
  isbn13?: string;
  isbn10?: string;
  asin?: string;
  goodreadsId?: string;
  comicvineId?: string;
  hardcoverId?: string;
  hardcoverBookId?: number | null;
  googleId?: string;
  pageCount?: number | null;
  language?: string;
  rating?: number | null;
  reviewCount?: number | null;
  amazonRating?: number | null;
  amazonReviewCount?: number | null;
  goodreadsRating?: number | null;
  goodreadsReviewCount?: number | null;
  hardcoverReviewCount?: number | null;
  lubimyczytacId?: string;
  lubimyczytacRating?: number | null;
  ranobedbId?: string;
  ranobedbRating?: number | null;
  hardcoverRating?: number | null;
  audibleId?: string;
  audibleRating?: number | null;
  audibleReviewCount?: number | null;
  narrator?: string;
  abridged?: boolean | null;
  narratorLocked?: boolean;
  abridgedLocked?: boolean;
  audiobookMetadata?: AudiobookMetadata;
  comicMetadata?: ComicMetadata;
  coverUpdatedOn?: string;
  audiobookCoverUpdatedOn?: string;
  authors?: string[];
  categories?: string[];
  moods?: string[];
  tags?: string[];
  provider?: string;
  providerBookId?: string;
  externalUrl?: string;
  thumbnailUrl?: string | null;
  reviews?: BookReview[];
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
  comicvineIdLocked?: boolean;
  goodreadsIdLocked?: boolean;
  hardcoverIdLocked?: boolean;
  hardcoverBookIdLocked?: boolean;
  googleIdLocked?: boolean;
  pageCountLocked?: boolean;
  languageLocked?: boolean;
  amazonRatingLocked?: boolean;
  amazonReviewCountLocked?: boolean;
  goodreadsRatingLocked?: boolean;
  goodreadsReviewCountLocked?: boolean;
  hardcoverRatingLocked?: boolean;
  hardcoverReviewCountLocked?: boolean;
  lubimyczytacIdLocked?: boolean;
  lubimyczytacRatingLocked?: boolean;
  ranobedbIdLocked?: boolean;
  ranobedbRatingLocked?: boolean;
  audibleIdLocked?: boolean;
  audibleRatingLocked?: boolean;
  audibleReviewCountLocked?: boolean;
  coverUpdatedOnLocked?: boolean;
  authorsLocked?: boolean;
  categoriesLocked?: boolean;
  moodsLocked?: boolean;
  tagsLocked?: boolean;
  coverLocked?: boolean;
  audiobookCoverLocked?: boolean;
  reviewsLocked?: boolean;
  ageRating?: number | null;
  contentRating?: string | null;
  ageRatingLocked?: boolean;
  contentRatingLocked?: boolean;
  allMetadataLocked?: boolean;

  [key: string]: unknown;
}

export interface MetadataClearFlags {
  title?: boolean;
  subtitle?: boolean;
  publisher?: boolean;
  publishedDate?: boolean;
  description?: boolean;
  seriesName?: boolean;
  seriesNumber?: boolean;
  seriesTotal?: boolean;
  isbn13?: boolean;
  isbn10?: boolean;
  asin?: boolean;
  goodreadsId?: boolean;
  comicvineId?: boolean;
  hardcoverId?: boolean;
  hardcoverBookId?: boolean;
  googleId?: boolean;
  pageCount?: boolean;
  language?: boolean;
  amazonRating?: boolean;
  amazonReviewCount?: boolean;
  goodreadsRating?: boolean;
  goodreadsReviewCount?: boolean;
  hardcoverRating?: boolean;
  hardcoverReviewCount?: boolean;
  lubimyczytacId?: boolean;
  lubimyczytacRating?: boolean;
  ranobedbId?: boolean;
  ranobedbRating?: boolean;
  audibleId?: boolean;
  audibleRating?: boolean;
  audibleReviewCount?: boolean;
  narrator?: boolean;
  abridged?: boolean;
  authors?: boolean;
  categories?: boolean;
  moods?: boolean;
  tags?: boolean;
  cover?: boolean;
  audiobookCover?: boolean;
  ageRating?: boolean;
  contentRating?: boolean;
}

export interface MetadataUpdateWrapper {
  metadata: BookMetadata;
  clearFlags: MetadataClearFlags;
}

export interface PdfViewerSetting {
  zoom: ZoomType;
  spread: 'off' | 'even' | 'odd';
}

export interface EpubViewerSetting {
  theme: string;
  font: string;
  fontSize: number;
  flow: string;
  lineHeight: number;
  letterSpacing: number;
  spread: string;
  customFontId?: number | null;
}

export interface EbookViewerSetting {
  lineHeight: number;
  justify: boolean;
  hyphenate: boolean;
  maxColumnCount: number;
  gap: number;
  fontSize: number;
  theme: string
  maxInlineSize: number;
  maxBlockSize: number;
  fontFamily: string | null;
  isDark: boolean;
  flow: 'paginated' | 'scrolled';
}

export interface CbxViewerSetting {
  pageSpread: CbxPageSpread;
  pageViewMode: CbxPageViewMode;
  fitMode: CbxFitMode;
  scrollMode?: CbxScrollMode;
  backgroundColor?: CbxBackgroundColor;
}

export interface BookSetting {
  pdfSettings?: PdfViewerSetting;
  epubSettings?: EpubViewerSetting;
  ebookSettings?: EbookViewerSetting;
  cbxSettings?: CbxViewerSetting;
  newPdfSettings?: NewPdfReaderSetting;

  [key: string]: unknown;
}

export interface BookRecommendation {
  book: Book;
  similarityScore: number;
}

export interface BulkMetadataUpdateRequest {
  bookIds: number[];
  authors?: string[];
  clearAuthors?: boolean;
  publisher?: string;
  clearPublisher?: boolean;
  language?: string;
  clearLanguage?: boolean;
  seriesName?: string;
  clearSeriesName?: boolean;
  seriesTotal?: number | null;
  clearSeriesTotal?: boolean;
  publishedDate?: string | null;
  clearPublishedDate?: boolean;
  genres?: string[];
  clearGenres?: boolean;
  moods?: string[];
  clearMoods?: boolean;
  tags?: string[];
  clearTags?: boolean;
  mergeCategories?: boolean;
  mergeMoods?: boolean;
  mergeTags?: boolean;
  ageRating?: number | null;
  clearAgeRating?: boolean;
  contentRating?: string | null;
  clearContentRating?: boolean;
}

export interface BookDeletionResponse {
  deleted: number[];
  failedFileDeletions: number[];
}

export interface BookSyncResponse {
  books: Book[];
  deletedIds: number[];
  syncTimestamp: string;
  totalBookCount: number;
}

export enum ReadStatus {
  UNREAD = 'UNREAD',
  READING = 'READING',
  RE_READING = 'RE_READING',
  READ = 'READ',
  PARTIALLY_READ = 'PARTIALLY_READ',
  PAUSED = 'PAUSED',
  WONT_READ = 'WONT_READ',
  ABANDONED = 'ABANDONED',
  UNSET = 'UNSET'
}

export interface CreatePhysicalBookRequest {
  libraryId: number;
  isbn?: string;
  title?: string;
  authors?: string[];
  description?: string;
  publisher?: string;
  publishedDate?: string;
  language?: string;
  pageCount?: number;
  categories?: string[];
}

export interface BookStatusUpdateResponse {
  bookId: number;
  readStatus: ReadStatus;
  readStatusModifiedTime: string;
  dateFinished?: string;
}

export interface PersonalRatingUpdateResponse {
  bookId: number;
  personalRating?: number;
}
