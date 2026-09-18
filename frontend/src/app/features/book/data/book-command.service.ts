import {HttpClient, HttpParams} from '@angular/common/http';
import {inject, Injectable} from '@angular/core';
import {lastValueFrom} from 'rxjs';

import {API_CONFIG} from '../../../core/config/api-config';
import {reconcilingMutationOptions} from '../../../core/data/command-options';
import {bookCommandKeys, bookCommandScopes} from './book-command-keys';
import {
  bookCommandChangeSet,
  requireBookIds,
  DeleteBooksPartialError,
  DeleteBooksResult,
  SetAllBookMetadataLocksResult,
  BookProgressSource,
  SetBookReadStatusResult,
  ResetBookProgressPartialError,
  ResetBookProgressResult,
  DeleteBooksVariables,
  ResetBookProgressVariables,
  SetAllBookMetadataLocksVariables,
  SetBookReadStatusVariables,
} from './book-command.models';
import {applyBookQueryChangeSet} from './book-query-cache';
import {KnownBookReadStatus} from './book-response.models';

const DELETE_BOOKS_CHUNK_SIZE = 200;
const RESET_PROGRESS_CHUNK_SIZE = 500;

const RESET_PROGRESS_BACKEND_TYPES = {
  GRIMMORY: 'BOOKLORE',
  KOREADER: 'KOREADER',
  KOBO: 'KOBO',
} as const satisfies Record<BookProgressSource, string>;

@Injectable({providedIn: 'root'})
export class BookCommandService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API_CONFIG.BASE_URL}/api/v1/books`;

  setReadStatus() {
    return reconcilingMutationOptions({
      mutationKey: bookCommandKeys.readStatus(),
      scope: bookCommandScopes.readingState,
      mutationFn: (variables: SetBookReadStatusVariables) =>
        this.postReadStatus(requireBookIds(variables.bookIds), variables.status),
      reconcile: (outcome, variables, client) => applyBookQueryChangeSet(
        client,
        bookCommandChangeSet(outcome, variables.bookIds, results => ({
          changedBookIds: results.map(result => result.bookId),
        })),
      ),
    });
  }

  deleteBooks() {
    return reconcilingMutationOptions({
      mutationKey: bookCommandKeys.deleteBooks(),
      scope: bookCommandScopes.deletion,
      mutationFn: (variables: DeleteBooksVariables) =>
        this.deleteBookRecordsInChunks(requireBookIds(variables.bookIds)),
      reconcile: (outcome, variables, client) => applyBookQueryChangeSet(
        client,
        bookCommandChangeSet(outcome, variables.bookIds, ({removedBookIds}) => {
          const removed = new Set(removedBookIds);
          return {
            deletedBookIds: removedBookIds,
            changedBookIds: variables.bookIds.filter(bookId => !removed.has(bookId)),
          };
        }),
      ),
    });
  }

  resetProgress() {
    return reconcilingMutationOptions({
      mutationKey: bookCommandKeys.resetProgress(),
      scope: bookCommandScopes.readingState,
      mutationFn: (variables: ResetBookProgressVariables) =>
        this.postResetProgressInChunks(requireBookIds(variables.bookIds), variables.source),
      reconcile: (outcome, variables, client) => applyBookQueryChangeSet(
        client,
        bookCommandChangeSet(outcome, variables.bookIds, results => ({
          changedBookIds: results.map(result => result.bookId),
        })),
      ),
    });
  }

  setAllMetadataLocks() {
    return reconcilingMutationOptions({
      mutationKey: bookCommandKeys.metadataAllLocks(),
      scope: bookCommandScopes.metadata,
      mutationFn: (variables: SetAllBookMetadataLocksVariables) =>
        this.putAllMetadataLocks(requireBookIds(variables.bookIds), variables.locked),
      reconcile: (outcome, variables, client) => applyBookQueryChangeSet(
        client,
        bookCommandChangeSet(outcome, variables.bookIds, results => ({
          changedBookIds: results.map(result => result.bookId),
        })),
      ),
    });
  }

  private postReadStatus(
    bookIds: readonly number[],
    status: KnownBookReadStatus,
  ): Promise<readonly SetBookReadStatusResult[]> {
    return lastValueFrom(this.http.post<readonly SetBookReadStatusResult[]>(
      `${this.baseUrl}/status`,
      {bookIds, status},
    ));
  }

  private async deleteBookRecords(
    bookIds: readonly number[],
  ): Promise<DeleteBooksResult> {
    const response = await lastValueFrom(this.http.delete<DeleteBooksResponse>(
      this.baseUrl,
      {params: new HttpParams().set('ids', bookIds.join(','))},
    ));
    return {
      removedBookIds: response.deleted,
      fileCleanupFailedBookIds: [...new Set(response.failedFileDeletions)],
    };
  }

  private async deleteBookRecordsInChunks(
    bookIds: readonly number[],
  ): Promise<DeleteBooksResult> {
    const removedBookIds: number[] = [];
    const fileCleanupFailedBookIds: number[] = [];

    for (let offset = 0; offset < bookIds.length; offset += DELETE_BOOKS_CHUNK_SIZE) {
      const chunk = bookIds.slice(offset, offset + DELETE_BOOKS_CHUNK_SIZE);
      try {
        const result = await this.deleteBookRecords(chunk);
        removedBookIds.push(...result.removedBookIds);
        fileCleanupFailedBookIds.push(...result.fileCleanupFailedBookIds);
      } catch (cause) {
        throw new DeleteBooksPartialError(
          {removedBookIds, fileCleanupFailedBookIds},
          chunk,
          cause,
        );
      }
    }

    return {removedBookIds, fileCleanupFailedBookIds};
  }

  private async postResetProgress(
    bookIds: readonly number[],
    source: BookProgressSource,
  ): Promise<readonly ResetBookProgressResult[]> {
    const response = await lastValueFrom(this.http.post<readonly ResetProgressResponseItem[]>(
      `${this.baseUrl}/reset-progress`,
      bookIds,
      {params: {type: RESET_PROGRESS_BACKEND_TYPES[source]}},
    ));
    return response.map(result => ({
      bookId: result.bookId,
      source,
      readStatusModifiedTime: result.readStatusModifiedTime,
    }));
  }

  private async postResetProgressInChunks(
    bookIds: readonly number[],
    source: BookProgressSource,
  ): Promise<readonly ResetBookProgressResult[]> {
    const completed: ResetBookProgressResult[] = [];

    for (let offset = 0; offset < bookIds.length; offset += RESET_PROGRESS_CHUNK_SIZE) {
      const chunk = bookIds.slice(offset, offset + RESET_PROGRESS_CHUNK_SIZE);
      try {
        completed.push(...await this.postResetProgress(chunk, source));
      } catch (cause) {
        throw new ResetBookProgressPartialError(completed, chunk, cause);
      }
    }

    return completed;
  }

  private async putAllMetadataLocks(
    bookIds: readonly number[],
    locked: boolean,
  ): Promise<readonly SetAllBookMetadataLocksResult[]> {
    const response = await lastValueFrom(this.http.put<readonly AllMetadataLockResponse[]>(
      `${this.baseUrl}/metadata/toggle-all-lock`,
      {bookIds, lock: locked ? 'LOCK' : 'UNLOCK'},
    ));
    return decodeAllMetadataLockResults(response);
  }
}

interface DeleteBooksResponse {
  readonly deleted: readonly number[];
  readonly failedFileDeletions: readonly number[];
}

interface AllMetadataLockResponse {
  readonly bookId: number;
  readonly [field: string]: unknown;
}

function decodeAllMetadataLockResults(
  response: readonly AllMetadataLockResponse[],
): readonly SetAllBookMetadataLocksResult[] {
  return response.map(({bookId, ...fields}) => {
    const metadataLocks: Record<string, boolean> = {};
    for (const [field, value] of Object.entries(fields)) {
      if (field.endsWith('Locked') && typeof value === 'boolean') {
        metadataLocks[field] = value;
      }
    }
    return {bookId, metadataLocks};
  });
}

interface ResetProgressResponseItem {
  readonly bookId: number;
  readonly readStatusModifiedTime: string | null;
}
