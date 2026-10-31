import {HttpClient} from '@angular/common/http';
import {inject, Service} from '@angular/core';
import {mutationOptions, QueryClient} from '@tanstack/angular-query-experimental';
import {lastValueFrom} from 'rxjs';

import {API_CONFIG} from '../../../core/config/api-config';
import {readingCommandKeys, readingCommandScopes} from './reading-command-keys';
import type {
  CreateAnnotationVariables,
  CreateBookmarkVariables,
  CreateBookNoteVariables,
  CreateNoteVariables,
  DeleteAnnotationVariables,
  DeleteBookmarkVariables,
  DeleteBookNoteVariables,
  DeleteNoteVariables,
  RecordReadingSessionVariables,
  SavePdfAnnotationsVariables,
  SaveViewerSettingsVariables,
  UpdateBookNoteVariables,
  UpdateNoteVariables,
} from './reading-command.models';
import {readingQueryKeys} from './reading-query-keys';
import type {Annotation, Bookmark, BookNote, Note} from './reading-query.models';

@Service()
export class ReadingCommandService {
  private readonly http = inject(HttpClient);
  private readonly annotationsUrl = `${API_CONFIG.BASE_URL}/api/v1/annotations`;
  private readonly bookmarksUrl = `${API_CONFIG.BASE_URL}/api/v1/bookmarks`;
  private readonly notesUrl = `${API_CONFIG.BASE_URL}/api/v2/book-notes`;
  private readonly bookNotesUrl = `${API_CONFIG.BASE_URL}/api/v1/book-notes`;
  private readonly pdfAnnotationsUrl = `${API_CONFIG.BASE_URL}/api/v1/pdf-annotations`;
  private readonly booksUrl = `${API_CONFIG.BASE_URL}/api/v1/books`;
  private readonly readingSessionsUrl = `${API_CONFIG.BASE_URL}/api/v1/reading-sessions`;

  createAnnotation() {
    return mutationOptions({
      mutationKey: readingCommandKeys.createAnnotation(),
      mutationFn: (annotation: CreateAnnotationVariables) =>
        lastValueFrom(this.http.post<Annotation>(this.annotationsUrl, annotation)),
      onSettled: (_data, _error, {bookId}, _onMutateResult, {client}) => refreshReadingBook(client, bookId),
    });
  }

  deleteAnnotation() {
    return mutationOptions({
      mutationKey: readingCommandKeys.deleteAnnotation(),
      mutationFn: ({annotationId}: DeleteAnnotationVariables) =>
        lastValueFrom(this.http.delete<void>(`${this.annotationsUrl}/${annotationId}`)),
      onSettled: (_data, _error, {bookId}, _onMutateResult, {client}) => refreshReadingBook(client, bookId),
    });
  }

  createBookmark() {
    return mutationOptions({
      mutationKey: readingCommandKeys.createBookmark(),
      mutationFn: (bookmark: CreateBookmarkVariables) =>
        lastValueFrom(this.http.post<Bookmark>(this.bookmarksUrl, bookmark)),
      onSettled: (_data, _error, {bookId}, _onMutateResult, {client}) => refreshReadingBook(client, bookId),
    });
  }

  deleteBookmark() {
    return mutationOptions({
      mutationKey: readingCommandKeys.deleteBookmark(),
      mutationFn: ({bookmarkId}: DeleteBookmarkVariables) =>
        lastValueFrom(this.http.delete<void>(`${this.bookmarksUrl}/${bookmarkId}`)),
      onSettled: (_data, _error, {bookId}, _onMutateResult, {client}) => refreshReadingBook(client, bookId),
    });
  }

  createNote() {
    return mutationOptions({
      mutationKey: readingCommandKeys.createNote(),
      mutationFn: (note: CreateNoteVariables) =>
        lastValueFrom(this.http.post<Note>(this.notesUrl, note)),
      onSettled: (_data, _error, {bookId}, _onMutateResult, {client}) => refreshReadingBook(client, bookId),
    });
  }

  updateNote() {
    return mutationOptions({
      mutationKey: readingCommandKeys.updateNote(),
      mutationFn: ({noteId, noteContent, color}: UpdateNoteVariables) =>
        lastValueFrom(this.http.put<Note>(`${this.notesUrl}/${noteId}`, {noteContent, color})),
      onSettled: (_data, _error, {bookId}, _onMutateResult, {client}) => refreshReadingBook(client, bookId),
    });
  }

  deleteNote() {
    return mutationOptions({
      mutationKey: readingCommandKeys.deleteNote(),
      mutationFn: ({noteId}: DeleteNoteVariables) =>
        lastValueFrom(this.http.delete<void>(`${this.notesUrl}/${noteId}`)),
      onSettled: (_data, _error, {bookId}, _onMutateResult, {client}) => refreshReadingBook(client, bookId),
    });
  }

  createBookNote() {
    return mutationOptions({
      mutationKey: readingCommandKeys.createBookNote(),
      mutationFn: (bookNote: CreateBookNoteVariables) =>
        lastValueFrom(this.http.post<BookNote>(this.bookNotesUrl, bookNote)),
      onSettled: (_data, _error, {bookId}, _onMutateResult, {client}) => refreshReadingBook(client, bookId),
    });
  }

  updateBookNote() {
    return mutationOptions({
      mutationKey: readingCommandKeys.updateBookNote(),
      mutationFn: ({bookNoteId, ...bookNote}: UpdateBookNoteVariables) =>
        lastValueFrom(this.http.post<BookNote>(this.bookNotesUrl, {id: bookNoteId, ...bookNote})),
      onSettled: (_data, _error, {bookId}, _onMutateResult, {client}) => refreshReadingBook(client, bookId),
    });
  }

  deleteBookNote() {
    return mutationOptions({
      mutationKey: readingCommandKeys.deleteBookNote(),
      mutationFn: ({bookNoteId}: DeleteBookNoteVariables) =>
        lastValueFrom(this.http.delete<void>(`${this.bookNotesUrl}/${bookNoteId}`)),
      onSettled: (_data, _error, {bookId}, _onMutateResult, {client}) => refreshReadingBook(client, bookId),
    });
  }

  savePdfAnnotations() {
    return mutationOptions({
      mutationKey: readingCommandKeys.savePdfAnnotations(),
      scope: readingCommandScopes.pdfAnnotations,
      mutationFn: ({bookId, data}: SavePdfAnnotationsVariables) =>
        lastValueFrom(this.http.put<void>(`${this.pdfAnnotationsUrl}/book/${bookId}`, {data})),
      onSettled: (_data, _error, {bookId}, _onMutateResult, {client}) => refreshReadingBook(client, bookId),
    });
  }

  saveViewerSettings() {
    return mutationOptions({
      mutationKey: readingCommandKeys.saveViewerSettings(),
      scope: readingCommandScopes.viewerSettings,
      mutationFn: ({bookId, ...settings}: SaveViewerSettingsVariables) =>
        lastValueFrom(this.http.put<void>(`${this.booksUrl}/${bookId}/viewer-setting`, settings)),
      onSettled: (_data, _error, {bookId}, _onMutateResult, {client}) => refreshReadingBook(client, bookId),
    });
  }

  recordReadingSession() {
    return mutationOptions({
      mutationKey: readingCommandKeys.recordReadingSession(),
      mutationFn: (session: RecordReadingSessionVariables) => lastValueFrom(this.http.post<void>(
        this.readingSessionsUrl,
        session,
        {keepalive: true},
      )),
      onSettled: (_data, _error, {bookId}, _onMutateResult, {client}) => refreshReadingBook(client, bookId),
    });
  }
}

async function refreshReadingBook(client: QueryClient, bookId: number): Promise<void> {
  await Promise.all([
    client.invalidateQueries({queryKey: readingQueryKeys.book(bookId)}),
    client.invalidateQueries({queryKey: readingQueryKeys.notebook()}),
  ]);
}
