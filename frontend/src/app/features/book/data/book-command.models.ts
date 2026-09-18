import {CommandOutcome} from '../../../core/data/command-options';
import {KnownBookReadStatus} from './book-response.models';
import {BookQueryChangeSet} from './book-query-cache';

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

export type BookProgressSource = 'GRIMMORY' | 'KOREADER' | 'KOBO';

export interface ResetBookProgressVariables {
  readonly bookIds: readonly number[];
  readonly source: BookProgressSource;
}

export interface ResetBookProgressResult {
  readonly bookId: number;
  readonly source: BookProgressSource;
  readonly readStatusModifiedTime: string | null;
}

export abstract class BulkBookCommandPartialError<TCompleted> extends Error {
  constructor(
    readonly completed: TCompleted,
    readonly attemptedBookIds: readonly number[],
    override readonly cause: unknown,
  ) {
    super('Bulk book command stopped before completing.', {cause});
    this.name = 'BulkBookCommandPartialError';
  }

  abstract get changeSet(): BookQueryChangeSet;
}

export class DeleteBooksPartialError extends BulkBookCommandPartialError<DeleteBooksResult> {
  constructor(
    completed: DeleteBooksResult,
    attemptedBookIds: readonly number[],
    cause: unknown,
  ) {
    super(completed, attemptedBookIds, cause);
    this.name = 'DeleteBooksPartialError';
  }

  override get changeSet(): BookQueryChangeSet {
    return {deletedBookIds: this.completed.removedBookIds};
  }
}

export class ResetBookProgressPartialError
  extends BulkBookCommandPartialError<readonly ResetBookProgressResult[]> {
  constructor(
    completed: readonly ResetBookProgressResult[],
    attemptedBookIds: readonly number[],
    cause: unknown,
  ) {
    super(completed, attemptedBookIds, cause);
    this.name = 'ResetBookProgressPartialError';
  }

  override get changeSet(): BookQueryChangeSet {
    return {changedBookIds: this.completed.map(result => result.bookId)};
  }
}

export function requireBookIds(bookIds: readonly number[]): readonly number[] {
  if (bookIds.length === 0) {
    throw new Error('At least one book ID is required.');
  }
  return bookIds;
}

export function bookCommandChangeSet<TData>(
  outcome: CommandOutcome<TData>,
  requestedBookIds: readonly number[],
  confirmed: (data: TData) => BookQueryChangeSet,
): BookQueryChangeSet {
  return outcome.status === 'success'
    ? confirmed(outcome.data)
    : bookFailureChangeSet(outcome.error, requestedBookIds);
}

function bookFailureChangeSet(
  error: unknown,
  requestedBookIds: readonly number[],
): BookQueryChangeSet {
  if (error instanceof BulkBookCommandPartialError) {
    const changeSet = error.changeSet;
    return {
      ...changeSet,
      changedBookIds: [...(changeSet.changedBookIds ?? []), ...error.attemptedBookIds],
    };
  }
  return {changedBookIds: requestedBookIds};
}

export interface SetAllBookMetadataLocksVariables {
  readonly bookIds: readonly number[];
  readonly locked: boolean;
}

export interface SetAllBookMetadataLocksResult {
  readonly bookId: number;
  readonly metadataLocks: Readonly<Record<string, boolean>>;
}
