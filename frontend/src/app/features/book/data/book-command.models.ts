import {BookDetail, BookDetailMetadata, BookFileType, BookReadStatus} from './book-response.models';

export interface SetBookReadStatusVariables {
  readonly bookIds: readonly number[];
  readonly status: BookReadStatus;
}

export interface SetBookReadStatusResult {
  readonly bookId: number;
  readonly readStatus: BookReadStatus;
  readonly readStatusModifiedTime?: string | null;
  readonly dateFinished?: string | null;
}

export interface DeleteBooksVariables {
  readonly bookIds: readonly number[];
}

export interface DeleteBooksResult {
  readonly removedBookIds: readonly number[];
  readonly fileCleanupFailedBookIds: readonly number[];
}

export interface DeleteAdditionalFileVariables {
  readonly bookId: number;
  readonly fileId: number;
}

export interface AttachBookFilesVariables {
  readonly targetBookId: number;
  readonly sourceBookIds: readonly number[];
  readonly moveFiles: boolean;
}

export interface AttachBookFilesResult {
  readonly updatedBook: BookDetail;
  readonly deletedSourceBookIds: readonly number[];
}

export type BookProgressSource = 'GRIMMORY' | 'KOREADER' | 'KOBO';

export interface ResetBookProgressVariables {
  readonly bookIds: readonly number[];
  readonly source: BookProgressSource;
}

export interface ResetBookProgressResult {
  readonly bookId: number;
  readonly readStatus: BookReadStatus | null;
  readonly readStatusModifiedTime: string | null;
  readonly dateFinished: string | null;
}

export class DeleteBooksPartialError extends Error {
  constructor(
    readonly completed: DeleteBooksResult,
    override readonly cause: unknown,
  ) {
    super('Deleting books stopped before completing.', {cause});
    this.name = 'DeleteBooksPartialError';
  }
}

export function requireBookIds(bookIds: readonly number[]): readonly number[] {
  if (bookIds.length === 0) {
    throw new Error('At least one book ID is required.');
  }
  return bookIds;
}

export interface SetAllBookMetadataLocksVariables {
  readonly bookIds: readonly number[];
  readonly locked: boolean;
}

export interface SetBookPersonalRatingVariables {
  readonly bookId: number;
  readonly rating: number;
}

export interface BookPersonalRatingResult {
  readonly bookId: number;
  readonly personalRating: number | null;
}

export interface SaveBookProgressVariables {
  readonly bookId: number;
  readonly fileProgress: {
    readonly bookFileId: number;
    readonly progressPercent: number;
    readonly positionData?: string;
    readonly positionHref?: string;
    readonly ttsPositionCfi?: string;
    readonly contentSourceProgressPercent?: number;
  };
}

export interface SetBookDateFinishedVariables {
  readonly bookId: number;
  readonly dateFinished: string;
}

export interface SetBookPhysicalVariables {
  readonly bookId: number;
  readonly physical: boolean;
}

export interface DeleteBookReviewVariables {
  readonly bookId: number;
  readonly reviewId: number;
}

export interface DetachBookFileVariables {
  readonly bookId: number;
  readonly fileId: number;
  readonly copyMetadata: boolean;
}

export interface DetachBookFileResult {
  readonly sourceBook: BookDetail;
  readonly newBook: BookDetail;
}

export interface UploadBookFileVariables {
  readonly bookId: number;
  readonly file: File;
  readonly isBook: boolean;
}

export interface ReplaceBookContentVariables {
  readonly bookId: number;
  readonly bookType: BookFileType | null;
  readonly content: Blob;
}

export interface UploadBookVariables {
  readonly libraryId: number;
  readonly pathId: number;
  readonly file: File;
}

export interface CreatePhysicalBookVariables {
  readonly libraryId: number;
  readonly isbn?: string;
  readonly title?: string;
  readonly authors?: readonly string[];
  readonly description?: string;
  readonly publisher?: string;
  readonly publishedDate?: string;
  readonly language?: string;
  readonly pageCount?: number;
  readonly categories?: readonly string[];
  readonly thumbnailUrl?: string;
}

export type BookMetadataLockField = Extract<keyof BookDetailMetadata, `${string}Locked`>;

export type BookMetadataClearField = BookMetadataLockField extends `${infer Field}Locked` ? Field : never;

export interface UpdateBookMetadataVariables {
  readonly bookId: number;
  readonly metadata: Readonly<Partial<BookDetailMetadata>>;
  readonly clearFlags: Readonly<Partial<Record<BookMetadataClearField, boolean>>>;
  readonly mergeCategories: boolean;
  readonly replaceMode: 'REPLACE_ALL' | 'REPLACE_WHEN_PROVIDED';
}

export interface BulkUpdateBookMetadataVariables {
  readonly bookIds: readonly number[];
  readonly authors?: readonly string[];
  readonly clearAuthors?: boolean;
  readonly publisher?: string;
  readonly clearPublisher?: boolean;
  readonly language?: string;
  readonly clearLanguage?: boolean;
  readonly seriesName?: string;
  readonly clearSeriesName?: boolean;
  readonly seriesTotal?: number;
  readonly clearSeriesTotal?: boolean;
  readonly publishedDate?: string;
  readonly clearPublishedDate?: boolean;
  readonly genres?: readonly string[];
  readonly clearGenres?: boolean;
  readonly moods?: readonly string[];
  readonly clearMoods?: boolean;
  readonly tags?: readonly string[];
  readonly clearTags?: boolean;
  readonly mergeCategories?: boolean;
  readonly mergeMoods?: boolean;
  readonly mergeTags?: boolean;
  readonly ageRating?: number;
  readonly clearAgeRating?: boolean;
  readonly contentRating?: string;
  readonly clearContentRating?: boolean;
}

export interface SetBookMetadataFieldLocksVariables {
  readonly bookIds: readonly number[];
  readonly fieldActions: Readonly<Partial<Record<BookMetadataLockField, 'LOCK' | 'UNLOCK'>>>;
}

export type BookMetadataValueType = 'authors' | 'categories' | 'moods' | 'tags' | 'series' | 'publishers' | 'languages';

export interface MergeBookMetadataValuesVariables {
  readonly metadataType: BookMetadataValueType;
  readonly targetValues: readonly string[];
  readonly valuesToMerge: readonly string[];
}

export interface DeleteBookMetadataValuesVariables {
  readonly metadataType: BookMetadataValueType;
  readonly valuesToDelete: readonly string[];
}

export type BookCoverTarget = 'ebook' | 'audiobook';

export interface BookCoverVariables {
  readonly bookId: number;
  readonly target: BookCoverTarget;
}

export interface SetBookCoverFromUrlVariables {
  readonly bookId: number;
  readonly target: BookCoverTarget;
  readonly url: string;
}

export interface UploadBookCoverVariables {
  readonly bookId: number;
  readonly target: BookCoverTarget;
  readonly file: File;
}

export interface BulkUploadBookCoverVariables {
  readonly bookIds: readonly number[];
  readonly file: File;
}

export interface SendBookVariables {
  readonly bookId: number;
  readonly providerId: number;
  readonly recipientId: number;
  readonly bookFileId?: number;
}
