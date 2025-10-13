import {Shelf} from './shelf.model';
import {CbxBackgroundColor, CbxFitMode, CbxPageSpread, CbxPageViewMode, CbxScrollMode, NewPdfReaderSetting} from '../../settings/user-management/user.service';
import {BookReview} from '../components/book-reviews/book-review-service';

export type BookType = "PDF" | "EPUB" | "CBX";

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

export interface AdditionalFile extends FileInfo {
  id: number;
  bookId: number;
  additionalFileType: AdditionalFileType;
  description?: string;
  addedOn?: string;
}

export interface Book extends FileInfo {
  id: number;
  bookType: BookType;
  libraryId: number;
  libraryName: string;
  metadata?: BookMetadata;
  shelves?: Shelf[];
  lastReadTime?: string;
  addedOn?: string;
  epubProgress?: EpubProgress;
  pdfProgress?: PdfProgress;
  cbxProgress?: CbxProgress;
  koreaderProgress?: KoReaderProgress;
  seriesCount?: number | null;
  metadataMatchScore?: number | null;
  readStatus?: ReadStatus;
  dateFinished?: string;
  libraryPath?: { id: number };
  alternativeFormats?: AdditionalFile[];
  supplementaryFiles?: AdditionalFile[];
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
  googleId?: string;
  pageCount?: number | null;
  language?: string;
  rating?: number | null;
  reviewCount?: number | null;
  amazonRating?: number | null;
  amazonReviewCount?: number | null;
  goodreadsRating?: number | null;
  goodreadsReviewCount?: number | null;
  hardcoverRating?: number | null;
  hardcoverReviewCount?: number | null;
  personalRating?: number | null;
  coverUpdatedOn?: string;
  authors?: string[];
  categories?: string[];
  moods?: string[];
  tags?: string[];
  provider?: string;
  providerBookId?: string;
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
  googleIdLocked?: boolean;
  pageCountLocked?: boolean;
  languageLocked?: boolean;
  personalRatingLocked?: boolean;
  amazonRatingLocked?: boolean;
  amazonReviewCountLocked?: boolean;
  goodreadsRatingLocked?: boolean;
  goodreadsReviewCountLocked?: boolean;
  hardcoverRatingLocked?: boolean;
  hardcoverReviewCountLocked?: boolean;
  coverUpdatedOnLocked?: boolean;
  authorsLocked?: boolean;
  categoriesLocked?: boolean;
  moodsLocked?: boolean;
  tagsLocked?: boolean;
  coverLocked?: boolean;
  reviewsLocked?: boolean;
  [key: string]: any;
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
  googleId?: boolean;
  pageCount?: boolean;
  language?: boolean;
  amazonRating?: boolean;
  amazonReviewCount?: boolean;
  goodreadsRating?: boolean;
  goodreadsReviewCount?: boolean;
  hardcoverRating?: boolean;
  hardcoverReviewCount?: boolean;
  personalRating?: boolean;
  authors?: boolean;
  categories?: boolean;
  moods?: boolean;
  tags?: boolean;
  cover?: boolean;
}

export interface MetadataUpdateWrapper {
  metadata: BookMetadata;
  clearFlags: MetadataClearFlags;
}

export interface PdfViewerSetting {
  zoom: string | number;
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
  cbxSettings?: CbxViewerSetting;
  newPdfSettings?: NewPdfReaderSetting;
  [key: string]: any;
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
}

export interface BookDeletionResponse {
  deleted: number[];
  failedFileDeletions: number[];
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
