import {HttpClient} from '@angular/common/http';
import {inject, Service} from '@angular/core';
import {mutationOptions} from '@tanstack/angular-query-experimental';
import {lastValueFrom} from 'rxjs';

import {API_CONFIG} from '../../../core/config/api-config';
import {bookCommandKeys, bookCommandScopes} from './book-command-keys';
import {
  requireBookIds,
  DeleteBooksPartialError,
  DeleteBooksResult,
  BookProgressSource,
  SetBookReadStatusResult,
  ResetBookProgressResult,
  DeleteBooksVariables,
  DeleteAdditionalFileVariables,
  AttachBookFilesVariables,
  AttachBookFilesResult,
  ResetBookProgressVariables,
  SetAllBookMetadataLocksVariables,
  SetBookReadStatusVariables,
} from './book-command.models';
import {applyBookQueryChangeSet} from './book-query-cache';
import {BookDetailMetadata} from './book-response.models';

const DELETE_BOOKS_CHUNK_SIZE = 200;
const RESET_PROGRESS_CHUNK_SIZE = 500;

const RESET_PROGRESS_BACKEND_TYPES = {
  GRIMMORY: 'BOOKLORE',
  KOREADER: 'KOREADER',
  KOBO: 'KOBO',
} as const satisfies Record<BookProgressSource, string>;

@Service()
export class BookCommandService {
  private readonly http = inject(HttpClient);
  private readonly booksUrl = `${API_CONFIG.BASE_URL}/api/v1/books`;
  private readonly emailUrl = `${API_CONFIG.BASE_URL}/api/v1/email`;

  setReadStatus() {
    return mutationOptions({
      mutationKey: bookCommandKeys.setReadStatus(),
      scope: bookCommandScopes.readingState,
      mutationFn: ({bookIds, status}: SetBookReadStatusVariables) =>
        lastValueFrom(this.http.post<readonly SetBookReadStatusResult[]>(`${this.booksUrl}/status`, {
          bookIds: requireBookIds(bookIds),
          status,
        })),
      onSettled: (_data, _error, {bookIds}, _onMutateResult, {client}) =>
        applyBookQueryChangeSet(client, {changedBookIds: bookIds}),
    });
  }

  deleteBooks() {
    return mutationOptions({
      mutationKey: bookCommandKeys.deleteBooks(),
      scope: bookCommandScopes.deletion,
      mutationFn: (variables: DeleteBooksVariables) =>
        this.deleteBookRecordsInChunks(requireBookIds(variables.bookIds)),
      onSettled: (data, error, {bookIds}, _onMutateResult, {client}) => applyBookQueryChangeSet(client, {
        changedBookIds: bookIds,
        deletedBookIds: data?.removedBookIds
          ?? (error instanceof DeleteBooksPartialError ? error.completed.removedBookIds : []),
      }),
    });
  }

  deleteAdditionalFile() {
    return mutationOptions({
      mutationKey: bookCommandKeys.deleteAdditionalFile(),
      scope: bookCommandScopes.deletion,
      mutationFn: ({bookId, fileId}: DeleteAdditionalFileVariables) =>
        lastValueFrom(this.http.delete<void>(`${this.booksUrl}/${bookId}/files/${fileId}`)),
      onSettled: (_data, _error, {bookId}, _onMutateResult, {client}) =>
        applyBookQueryChangeSet(client, {changedBookIds: [bookId]}),
    });
  }

  attachBookFiles() {
    return mutationOptions({
      mutationKey: bookCommandKeys.attachBookFiles(),
      scope: bookCommandScopes.deletion,
      mutationFn: ({targetBookId, sourceBookIds, moveFiles}: AttachBookFilesVariables) =>
        lastValueFrom(this.http.post<AttachBookFilesResult>(`${this.booksUrl}/${targetBookId}/attach-file`, {
          sourceBookIds: requireBookIds(sourceBookIds), moveFiles,
        })),
      onSettled: (data, _error, {targetBookId, sourceBookIds}, _onMutateResult, {client}) => applyBookQueryChangeSet(client, {
        changedBookIds: [targetBookId, ...sourceBookIds],
        deletedBookIds: data?.deletedSourceBookIds ?? [],
      }),
    });
  }

  resetProgress() {
    return mutationOptions({
      mutationKey: bookCommandKeys.resetProgress(),
      scope: bookCommandScopes.readingState,
      mutationFn: (variables: ResetBookProgressVariables) =>
        this.postResetProgressInChunks(requireBookIds(variables.bookIds), variables.source),
      onSettled: (_data, _error, {bookIds}, _onMutateResult, {client}) =>
        applyBookQueryChangeSet(client, {changedBookIds: bookIds}),
    });
  }

  setAllMetadataLocks() {
    return mutationOptions({
      mutationKey: bookCommandKeys.setAllMetadataLocks(),
      scope: bookCommandScopes.metadata,
      mutationFn: ({bookIds, locked}: SetAllBookMetadataLocksVariables) =>
        lastValueFrom(this.http.put<readonly BookDetailMetadata[]>(`${this.booksUrl}/metadata/toggle-all-lock`, {
          bookIds: requireBookIds(bookIds),
          lock: locked ? 'LOCK' : 'UNLOCK',
        })),
      onSettled: (_data, _error, {bookIds}, _onMutateResult, {client}) =>
        applyBookQueryChangeSet(client, {changedBookIds: bookIds}),
    });
  }

  bulkRegenerateCovers() {
    return mutationOptions({
      mutationKey: bookCommandKeys.bulkRegenerateCovers(),
      mutationFn: ({bookIds}: {readonly bookIds: readonly number[]}) =>
        lastValueFrom(this.http.post<void>(`${this.booksUrl}/bulk-regenerate-covers`, {bookIds: requireBookIds(bookIds)})),
    });
  }

  bulkGenerateCovers() {
    return mutationOptions({
      mutationKey: bookCommandKeys.bulkGenerateCovers(),
      mutationFn: ({bookIds}: {readonly bookIds: readonly number[]}) =>
        lastValueFrom(this.http.post<void>(`${this.booksUrl}/bulk-generate-custom-covers`, {bookIds: requireBookIds(bookIds)})),
    });
  }

  quickSend() {
    return mutationOptions({
      mutationKey: bookCommandKeys.quickSend(),
      mutationFn: ({bookId}: {readonly bookId: number}) =>
        lastValueFrom(this.http.post<void>(`${this.emailUrl}/book/${bookId}`, {})),
    });
  }

  private async deleteBookRecords(
    bookIds: readonly number[],
  ): Promise<DeleteBooksResult> {
    const response = await lastValueFrom(this.http.delete<DeleteBooksResponse>(
      this.booksUrl,
      {params: {ids: bookIds.join(',')}},
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
        throw new DeleteBooksPartialError({removedBookIds, fileCleanupFailedBookIds}, cause);
      }
    }

    return {removedBookIds, fileCleanupFailedBookIds};
  }

  private postResetProgress(
    bookIds: readonly number[],
    source: BookProgressSource,
  ): Promise<readonly ResetBookProgressResult[]> {
    return lastValueFrom(this.http.post<readonly ResetBookProgressResult[]>(
      `${this.booksUrl}/reset-progress`,
      bookIds,
      {params: {type: RESET_PROGRESS_BACKEND_TYPES[source]}},
    ));
  }

  private async postResetProgressInChunks(
    bookIds: readonly number[],
    source: BookProgressSource,
  ): Promise<readonly ResetBookProgressResult[]> {
    const completed: ResetBookProgressResult[] = [];

    for (let offset = 0; offset < bookIds.length; offset += RESET_PROGRESS_CHUNK_SIZE) {
      const chunk = bookIds.slice(offset, offset + RESET_PROGRESS_CHUNK_SIZE);
      completed.push(...await this.postResetProgress(chunk, source));
    }

    return completed;
  }
}

interface DeleteBooksResponse {
  readonly deleted: readonly number[];
  readonly failedFileDeletions: readonly number[];
}
