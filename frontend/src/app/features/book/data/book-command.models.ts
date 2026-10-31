import {BookDetail, KnownBookReadStatus} from './book-response.models';

export interface SetBookReadStatusVariables {
  readonly bookIds: readonly number[];
  readonly status: KnownBookReadStatus;
}

export interface SetBookReadStatusResult {
  readonly bookId: number;
  readonly readStatus: KnownBookReadStatus;
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
  readonly readStatus: KnownBookReadStatus | null;
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
