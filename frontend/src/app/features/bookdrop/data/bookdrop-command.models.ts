import type {BookdropMetadata} from './bookdrop-query.models';

export type BookdropSelection =
  | {readonly selectAll: true; readonly excludedIds: readonly number[]}
  | {readonly selectAll: false; readonly selectedIds: readonly number[]};

export interface BookdropFinalizeFile {
  readonly fileId: number;
  readonly libraryId: number;
  readonly pathId: number;
  readonly metadata: BookdropMetadata;
}

export type FinalizeBookdropImportVariables =
  | {
    readonly selectAll: true;
    readonly excludedIds: readonly number[];
    readonly defaultLibraryId: number;
    readonly defaultPathId: number;
    readonly files?: readonly BookdropFinalizeFile[];
  }
  | {readonly selectAll: false; readonly files: readonly BookdropFinalizeFile[]};

export interface BookdropFinalizeResult {
  readonly totalFiles: number;
  readonly successfullyImported: number;
  readonly failed: number;
  readonly processedAt: string;
  readonly results: readonly {
    readonly fileName: string;
    readonly success: boolean;
    readonly message: string;
  }[];
}

export type ExtractBookdropPatternVariables = {readonly pattern: string} & BookdropSelection;

export interface BookdropPatternExtractResult {
  readonly totalFiles: number;
  readonly successfullyExtracted: number;
  readonly failed: number;
  readonly results: readonly {
    readonly fileId: number;
    readonly fileName: string;
    readonly success: boolean;
    readonly extractedMetadata: BookdropMetadata | null;
    readonly errorMessage: string | null;
  }[];
}

export type BookdropBulkEditField =
  'seriesName' | 'seriesTotal' | 'publisher' | 'language' | 'authors' | 'categories' | 'moods' | 'tags';

export type BulkEditBookdropMetadataVariables = {
  readonly fields: Pick<BookdropMetadata, BookdropBulkEditField>;
  readonly enabledFields: readonly BookdropBulkEditField[];
  readonly mergeArrays: boolean;
} & BookdropSelection;

export interface BookdropBulkEditResult {
  readonly totalFiles: number;
  readonly successfullyUpdated: number;
  readonly failed: number;
}
