import {Shelf} from './shelf.model';

export type BookType = "PDF" | "EPUB";

export interface Book {
  id: number;
  bookType: BookType;
  libraryId: number;
  metadata?: BookMetadata;
  shelves?: Shelf[];
  lastReadTime?: string;
  addedOn?: string;
  epubProgress?: EpubProgress;
  pdfProgress?: PdfProgress;
  filePath?: string;
  fileSizeKb?: number;
}

export interface EpubProgress {
  cfi: string;
  percentage: number;
}

export interface PdfProgress {
  page: number;
  percentage: number;
}

export interface BookMetadata {
  bookId: number;
  title: string;
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
  pageCount?: number | null;
  language?: string;
  rating?: number | null;
  reviewCount?: number | null;
  amazonRating?: number | null;
  amazonReviewCount?: number | null;
  goodreadsRating?: number | null;
  goodreadsReviewCount?: number | null;
  coverUpdatedOn?: string;
  authors: string[];
  categories: string[];
  awards?: Award[];
  provider?: string;
  providerBookId?: string;
  thumbnailUrl?: string | null;

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
  pageCountLocked?: boolean;
  languageLocked?: boolean;
  ratingLocked?: boolean;
  reviewCountLocked?: boolean;
  amazonRatingLocked?: boolean;
  amazonReviewCountLocked?: boolean;
  goodreadsRatingLocked?: boolean;
  goodreadsReviewCountLocked?: boolean;
  coverUpdatedOnLocked?: boolean;
  authorsLocked?: boolean;
  categoriesLocked?: boolean;
  awardsLocked?: boolean;
  coverLocked?: boolean;

  [key: string]: any;
}

export interface Award {
  name: string;
  category: string;
  designation: string;
  awardedAt: string;
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
}

export interface BookSetting {
  pdfSettings?: PdfViewerSetting;
  epubSettings?: EpubViewerSetting;

  [key: string]: any;
}

export interface BookRecommendation {
  book: Book;
  similarityScore: number;
}
