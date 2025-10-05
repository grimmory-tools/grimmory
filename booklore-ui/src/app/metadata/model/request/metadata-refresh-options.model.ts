export interface MetadataRefreshOptions {
  libraryId: number | null;
  refreshCovers: boolean;
  mergeCategories: boolean;
  reviewBeforeApply: boolean;
  fieldOptions?: FieldOptions;
  skipFields?: Record<keyof FieldOptions, boolean>;
}

export interface FieldProvider {
  p4: string | null;
  p3: string | null;
  p2: string | null;
  p1: string | null;
}

export interface FieldOptions {
  title: FieldProvider;
  description: FieldProvider;
  authors: FieldProvider;
  categories: FieldProvider;
  cover: FieldProvider;
  subtitle: FieldProvider;
  publisher: FieldProvider;
  publishedDate: FieldProvider;
  seriesName: FieldProvider;
  seriesNumber: FieldProvider;
  seriesTotal: FieldProvider;
  isbn13: FieldProvider;
  isbn10: FieldProvider;
  language: FieldProvider;
  pageCount: FieldProvider;
  asin: FieldProvider;
  goodreadsId: FieldProvider;
  comicvineId: FieldProvider;
  hardcoverId: FieldProvider;
  googleId: FieldProvider;
  amazonRating: FieldProvider;
  amazonReviewCount: FieldProvider;
  goodreadsRating: FieldProvider;
  goodreadsReviewCount: FieldProvider;
  hardcoverRating: FieldProvider;
  hardcoverReviewCount: FieldProvider;
  moods: FieldProvider;
  tags: FieldProvider;
}
