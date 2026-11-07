import {HttpClient} from '@angular/common/http';
import {inject, Service} from '@angular/core';
import {mutationOptions} from '@tanstack/angular-query-experimental';
import {lastValueFrom} from 'rxjs';

import {API_CONFIG} from '../../../core/config/api-config';
import {postFormData} from '../../../core/data/query-transport';
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
  SetBookPersonalRatingVariables,
  BookPersonalRatingResult,
  SaveBookProgressVariables,
  SetBookDateFinishedVariables,
  SetBookPhysicalVariables,
  DetachBookFileVariables,
  DetachBookFileResult,
  CreatePhysicalBookVariables,
  UploadBookFileVariables,
  UploadBookVariables,
  ReplaceBookContentVariables,
  MergeBookMetadataValuesVariables,
  DeleteBookMetadataValuesVariables,
  UpdateBookMetadataVariables,
  BulkUpdateBookMetadataVariables,
  SetBookMetadataFieldLocksVariables,
  BookCoverVariables,
  SetBookCoverFromUrlVariables,
  UploadBookCoverVariables,
  BulkUploadBookCoverVariables,
  DeleteBookReviewVariables,
  SendBookVariables,
} from './book-command.models';
import {invalidateAuthorsQuery} from '../../author-browser/service/author-query-cache';
import {applyBookQueryChangeSet, invalidateAllBookQueries, invalidateBookCollections} from './book-query-cache';
import {bookQueryKeys} from './book-query-keys';
import {BookDetail, BookDetailMetadata, BookReviewResponse} from './book-response.models';

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
  private readonly reviewsUrl = `${API_CONFIG.BASE_URL}/api/v1/reviews`;
  private readonly emailUrl = `${API_CONFIG.BASE_URL}/api/v1/email`;
  private readonly filesUrl = `${API_CONFIG.BASE_URL}/api/v1/files`;

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
      scope: bookCommandScopes.files,
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

  setPersonalRating() {
    return mutationOptions({
      mutationKey: bookCommandKeys.setPersonalRating(),
      scope: bookCommandScopes.readingState,
      mutationFn: ({bookId, rating}: SetBookPersonalRatingVariables) =>
        lastValueFrom(this.http.put<readonly BookPersonalRatingResult[]>(`${this.booksUrl}/personal-rating`, {
          ids: [bookId],
          rating,
        })),
      onSettled: (_data, _error, {bookId}, _onMutateResult, {client}) =>
        applyBookQueryChangeSet(client, {changedBookIds: [bookId]}),
    });
  }

  resetPersonalRating() {
    return mutationOptions({
      mutationKey: bookCommandKeys.resetPersonalRating(),
      scope: bookCommandScopes.readingState,
      mutationFn: ({bookId}: {readonly bookId: number}) =>
        lastValueFrom(this.http.post<readonly BookPersonalRatingResult[]>(
          `${this.booksUrl}/reset-personal-rating`,
          [bookId],
        )),
      onSettled: (_data, _error, {bookId}, _onMutateResult, {client}) =>
        applyBookQueryChangeSet(client, {changedBookIds: [bookId]}),
    });
  }

  saveProgress() {
    return mutationOptions({
      mutationKey: bookCommandKeys.saveProgress(),
      scope: bookCommandScopes.readingState,
      mutationFn: (variables: SaveBookProgressVariables) =>
        lastValueFrom(this.http.post<void>(`${this.booksUrl}/progress`, variables)),
      onSettled: (_data, _error, {bookId}, _onMutateResult, {client}) =>
        applyBookQueryChangeSet(client, {changedBookIds: [bookId]}),
    });
  }

  setDateFinished() {
    return mutationOptions({
      mutationKey: bookCommandKeys.setDateFinished(),
      scope: bookCommandScopes.readingState,
      mutationFn: (variables: SetBookDateFinishedVariables) =>
        lastValueFrom(this.http.post<void>(`${this.booksUrl}/progress`, variables)),
      onSettled: (_data, _error, {bookId}, _onMutateResult, {client}) =>
        applyBookQueryChangeSet(client, {changedBookIds: [bookId]}),
    });
  }

  setPhysical() {
    return mutationOptions({
      mutationKey: bookCommandKeys.setPhysical(),
      scope: bookCommandScopes.metadata,
      mutationFn: ({bookId, physical}: SetBookPhysicalVariables) =>
        lastValueFrom(this.http.patch<BookDetail>(`${this.booksUrl}/${bookId}/physical`, null, {params: {physical}})),
      onSettled: (_data, _error, {bookId}, _onMutateResult, {client}) =>
        applyBookQueryChangeSet(client, {changedBookIds: [bookId]}),
    });
  }

  detachFile() {
    return mutationOptions({
      mutationKey: bookCommandKeys.detachFile(),
      scope: bookCommandScopes.files,
      mutationFn: ({bookId, fileId, copyMetadata}: DetachBookFileVariables) =>
        lastValueFrom(this.http.post<DetachBookFileResult>(
          `${this.booksUrl}/${bookId}/files/${fileId}/detach`,
          {copyMetadata},
        )),
      onSettled: (_data, _error, {bookId}, _onMutateResult, {client}) =>
        applyBookQueryChangeSet(client, {changedBookIds: [bookId]}),
    });
  }

  uploadFile() {
    return mutationOptions({
      mutationKey: bookCommandKeys.uploadFile(),
      scope: bookCommandScopes.files,
      mutationFn: ({bookId, file, isBook}: UploadBookFileVariables) =>
        postFormData(this.http, `${this.booksUrl}/${bookId}/files`, {file, isBook: String(isBook)}),
      onSettled: (_data, _error, {bookId}, _onMutateResult, {client}) =>
        applyBookQueryChangeSet(client, {changedBookIds: [bookId]}),
    });
  }

  uploadBook() {
    return mutationOptions({
      mutationKey: bookCommandKeys.uploadBook(),
      mutationFn: ({libraryId, pathId, file}: UploadBookVariables) =>
        postFormData(this.http, `${this.filesUrl}/upload`, {file, libraryId: String(libraryId), pathId: String(pathId)}),
    });
  }

  replaceBookContent() {
    return mutationOptions({
      mutationKey: bookCommandKeys.replaceBookContent(),
      scope: bookCommandScopes.files,
      mutationFn: ({bookId, bookType, content}: ReplaceBookContentVariables) =>
        lastValueFrom(this.http.put<void>(`${this.booksUrl}/${bookId}/content`, content, {
          params: bookType === null ? undefined : {bookType},
        })),
      onSettled: (_data, _error, {bookId}, _onMutateResult, {client}) =>
        applyBookQueryChangeSet(client, {changedBookIds: [bookId]}),
    });
  }

  createPhysicalBook() {
    return mutationOptions({
      mutationKey: bookCommandKeys.createPhysicalBook(),
      mutationFn: (variables: CreatePhysicalBookVariables) =>
        lastValueFrom(this.http.post<BookDetail>(`${this.booksUrl}/physical`, variables)),
      onSettled: (_data, _error, _variables, _onMutateResult, {client}) => invalidateBookCollections(client),
    });
  }

  updateMetadata() {
    return mutationOptions({
      mutationKey: bookCommandKeys.updateMetadata(),
      scope: bookCommandScopes.metadata,
      mutationFn: ({bookId, metadata, clearFlags, mergeCategories, replaceMode}: UpdateBookMetadataVariables) =>
        lastValueFrom(this.http.put<BookDetailMetadata>(
          `${this.booksUrl}/${bookId}/metadata`,
          {metadata, clearFlags},
          {params: {mergeCategories, replaceMode}},
        )),
      onSettled: (_data, _error, {bookId}, _onMutateResult, {client}) =>
        applyBookQueryChangeSet(client, {changedBookIds: [bookId]}),
    });
  }

  bulkUpdateMetadata() {
    return mutationOptions({
      mutationKey: bookCommandKeys.bulkUpdateMetadata(),
      scope: bookCommandScopes.metadata,
      mutationFn: (variables: BulkUpdateBookMetadataVariables) =>
        lastValueFrom(this.http.put<void>(`${this.booksUrl}/bulk-edit-metadata`, {...variables, bookIds: requireBookIds(variables.bookIds)})),
      onSettled: (_data, _error, {bookIds}, _onMutateResult, {client}) =>
        applyBookQueryChangeSet(client, {changedBookIds: bookIds}),
    });
  }

  setMetadataFieldLocks() {
    return mutationOptions({
      mutationKey: bookCommandKeys.setMetadataFieldLocks(),
      scope: bookCommandScopes.metadata,
      mutationFn: ({bookIds, fieldActions}: SetBookMetadataFieldLocksVariables) =>
        lastValueFrom(this.http.put<void>(`${this.booksUrl}/metadata/toggle-field-locks`, {
          bookIds: requireBookIds(bookIds),
          fieldActions,
        })),
      onSettled: (_data, _error, {bookIds}, _onMutateResult, {client}) =>
        applyBookQueryChangeSet(client, {changedBookIds: bookIds}),
    });
  }

  mergeMetadataValues() {
    return mutationOptions({
      mutationKey: bookCommandKeys.mergeMetadataValues(),
      scope: bookCommandScopes.metadata,
      mutationFn: (variables: MergeBookMetadataValuesVariables) =>
        lastValueFrom(this.http.post<void>(`${this.booksUrl}/metadata/manage/consolidate`, variables)),
      onSettled: (_data, _error, _variables, _onMutateResult, {client}) => {
        invalidateAuthorsQuery(client);
        return invalidateAllBookQueries(client);
      },
    });
  }

  deleteMetadataValues() {
    return mutationOptions({
      mutationKey: bookCommandKeys.deleteMetadataValues(),
      scope: bookCommandScopes.metadata,
      mutationFn: (variables: DeleteBookMetadataValuesVariables) =>
        lastValueFrom(this.http.post<void>(`${this.booksUrl}/metadata/manage/delete`, variables)),
      onSettled: (_data, _error, _variables, _onMutateResult, {client}) => {
        invalidateAuthorsQuery(client);
        return invalidateAllBookQueries(client);
      },
    });
  }

  regenerateCover() {
    return mutationOptions({
      mutationKey: bookCommandKeys.regenerateCover(),
      scope: bookCommandScopes.metadata,
      mutationFn: ({bookId, target}: BookCoverVariables) => lastValueFrom(this.http.post<void>(
        `${this.booksUrl}/${bookId}/${target === 'ebook' ? 'regenerate-cover' : 'regenerate-audiobook-cover'}`,
        {},
      )),
      onSettled: (_data, _error, {bookId}, _onMutateResult, {client}) =>
        applyBookQueryChangeSet(client, {changedBookIds: [bookId]}),
    });
  }

  generateCover() {
    return mutationOptions({
      mutationKey: bookCommandKeys.generateCover(),
      scope: bookCommandScopes.metadata,
      mutationFn: ({bookId, target}: BookCoverVariables) => lastValueFrom(this.http.post<void>(
        `${this.booksUrl}/${bookId}/${target === 'ebook' ? 'generate-custom-cover' : 'generate-custom-audiobook-cover'}`,
        {},
      )),
      onSettled: (_data, _error, {bookId}, _onMutateResult, {client}) =>
        applyBookQueryChangeSet(client, {changedBookIds: [bookId]}),
    });
  }

  setCoverFromUrl() {
    return mutationOptions({
      mutationKey: bookCommandKeys.setCoverFromUrl(),
      scope: bookCommandScopes.metadata,
      mutationFn: ({bookId, target, url}: SetBookCoverFromUrlVariables) => lastValueFrom(this.http.post<void>(
        `${this.booksUrl}/${bookId}/metadata/${target === 'ebook' ? 'cover' : 'audiobook-cover'}/from-url`,
        {url},
      )),
      onSettled: (_data, _error, {bookId}, _onMutateResult, {client}) =>
        applyBookQueryChangeSet(client, {changedBookIds: [bookId]}),
    });
  }

  uploadCover() {
    return mutationOptions({
      mutationKey: bookCommandKeys.uploadCover(),
      scope: bookCommandScopes.metadata,
      mutationFn: ({bookId, target, file}: UploadBookCoverVariables) => postFormData(
        this.http,
        `${this.booksUrl}/${bookId}/metadata/${target === 'ebook' ? 'cover' : 'audiobook-cover'}/upload`,
        {file},
      ),
      onSettled: (_data, _error, {bookId}, _onMutateResult, {client}) =>
        applyBookQueryChangeSet(client, {changedBookIds: [bookId]}),
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

  bulkUploadCover() {
    return mutationOptions({
      mutationKey: bookCommandKeys.bulkUploadCover(),
      mutationFn: ({bookIds, file}: BulkUploadBookCoverVariables) =>
        postFormData(this.http, `${this.booksUrl}/bulk-upload-cover`, {file, bookIds: requireBookIds(bookIds).join(',')}),
    });
  }

  regenerateAllCovers() {
    return mutationOptions({
      mutationKey: bookCommandKeys.regenerateAllCovers(),
      mutationFn: ({missingOnly}: {readonly missingOnly: boolean}) =>
        lastValueFrom(this.http.post<void>(`${this.booksUrl}/regenerate-covers`, {}, {params: {missingOnly}})),
    });
  }

  quickSend() {
    return mutationOptions({
      mutationKey: bookCommandKeys.quickSend(),
      mutationFn: ({bookId}: {readonly bookId: number}) =>
        lastValueFrom(this.http.post<void>(`${this.emailUrl}/book/${bookId}`, {})),
    });
  }

  sendBook() {
    return mutationOptions({
      mutationKey: bookCommandKeys.sendBook(),
      mutationFn: (variables: SendBookVariables) =>
        lastValueFrom(this.http.post<void>(`${this.emailUrl}/book`, variables)),
    });
  }

  refreshReviews() {
    return mutationOptions({
      mutationKey: bookCommandKeys.refreshReviews(),
      mutationFn: ({bookId}: {readonly bookId: number}) =>
        lastValueFrom(this.http.post<readonly BookReviewResponse[]>(`${this.reviewsUrl}/book/${bookId}/refresh`, {})),
      onSettled: (_data, _error, {bookId}, _onMutateResult, {client}) =>
        client.invalidateQueries({queryKey: bookQueryKeys.reviews(bookId)}),
    });
  }

  deleteReview() {
    return mutationOptions({
      mutationKey: bookCommandKeys.deleteReview(),
      mutationFn: ({reviewId}: DeleteBookReviewVariables) =>
        lastValueFrom(this.http.delete<void>(`${this.reviewsUrl}/${reviewId}`)),
      onSettled: (_data, _error, {bookId}, _onMutateResult, {client}) =>
        client.invalidateQueries({queryKey: bookQueryKeys.reviews(bookId)}),
    });
  }

  deleteAllReviews() {
    return mutationOptions({
      mutationKey: bookCommandKeys.deleteAllReviews(),
      mutationFn: ({bookId}: {readonly bookId: number}) =>
        lastValueFrom(this.http.delete<void>(`${this.reviewsUrl}/book/${bookId}`)),
      onSettled: (_data, _error, {bookId}, _onMutateResult, {client}) =>
        client.invalidateQueries({queryKey: bookQueryKeys.reviews(bookId)}),
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
