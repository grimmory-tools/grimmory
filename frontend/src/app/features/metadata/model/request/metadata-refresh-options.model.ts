import type {MetadataProviderFieldRecord} from '../../../../shared/metadata/metadata-provider-fields';

export type MetadataReplaceMode = 'REPLACE_ALL' | 'REPLACE_MISSING' | 'REPLACE_WHEN_PROVIDED';

export interface MetadataRefreshOptions {
  libraryId: number | null;
  refreshCovers: boolean;
  mergeCategories: boolean;
  reviewBeforeApply: boolean;
  /**
   * Controls how fetched metadata replaces existing metadata.
   * REPLACE_ALL: Replace all fields with fetched values (even if existing values are present)
   * REPLACE_MISSING: Only fill in fields that are currently empty/null
   */
  replaceMode?: MetadataReplaceMode;
  fieldOptions?: FieldOptions;
  enabledFields?: Record<keyof FieldOptions, boolean>;
}

export interface FieldProvider {
  p4: string | null;
  p3: string | null;
  p2: string | null;
  p1: string | null;
}

interface GenericFieldOptions {
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
  moods: FieldProvider;
  tags: FieldProvider;
}

export type FieldOptions = GenericFieldOptions & MetadataProviderFieldRecord<FieldProvider>;
