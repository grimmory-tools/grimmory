import {HttpClient} from '@angular/common/http';
import {inject, Service} from '@angular/core';
import {mutationOptions} from '@tanstack/angular-query-experimental';
import {lastValueFrom} from 'rxjs';

import {API_CONFIG} from '../../../core/config/api-config';
import {invalidateAuthorsQuery} from '../../author-browser/service/author-query-cache';
import {invalidateAllBookQueries} from '../../book/data/book-query-cache';
import {shelfQueryKeys} from '../../shelf/data/shelf-query-keys';
import {libraryCommandKeys} from './library-command-keys';
import type {
  CreateLibraryVariables,
  SetFileNamingPatternVariables,
  UpdateLibraryVariables,
} from './library-command.models';
import {libraryQueryKeys} from './library-query-keys';
import type {Library} from './library-query.models';

@Service()
export class LibraryCommandService {
  private readonly http = inject(HttpClient);
  private readonly librariesUrl = `${API_CONFIG.BASE_URL}/api/v1/libraries`;

  createLibrary() {
    return mutationOptions({
      mutationKey: libraryCommandKeys.createLibrary(),
      mutationFn: (library: CreateLibraryVariables) => lastValueFrom(this.http.post<Library>(this.librariesUrl, library)),
      onSettled: (_data, _error, _variables, _onMutateResult, {client}) =>
        client.invalidateQueries({queryKey: libraryQueryKeys.all()}),
    });
  }

  updateLibrary() {
    return mutationOptions({
      mutationKey: libraryCommandKeys.updateLibrary(),
      mutationFn: ({libraryId, library}: UpdateLibraryVariables) =>
        lastValueFrom(this.http.put<Library>(`${this.librariesUrl}/${libraryId}`, library)),
      onSettled: (_data, _error, _variables, _onMutateResult, {client}) => Promise.all([
        client.invalidateQueries({queryKey: libraryQueryKeys.all()}),
        invalidateAllBookQueries(client),
      ]),
    });
  }

  deleteLibrary() {
    return mutationOptions({
      mutationKey: libraryCommandKeys.deleteLibrary(),
      mutationFn: ({libraryId}: {readonly libraryId: number}) =>
        lastValueFrom(this.http.delete<void>(`${this.librariesUrl}/${libraryId}`)),
      onSettled: (_data, error, {libraryId}, _onMutateResult, {client}) => {
        if (!error) {
          client.removeQueries({queryKey: libraryQueryKeys.formatCounts(libraryId)});
        }
        invalidateAuthorsQuery(client);
        return Promise.all([
          client.invalidateQueries({queryKey: libraryQueryKeys.all()}),
          client.invalidateQueries({queryKey: shelfQueryKeys.all()}),
          invalidateAllBookQueries(client),
        ]);
      },
    });
  }

  rescanLibrary() {
    return mutationOptions({
      mutationKey: libraryCommandKeys.rescanLibrary(),
      mutationFn: ({libraryId}: {readonly libraryId: number}) =>
        lastValueFrom(this.http.put<void>(`${this.librariesUrl}/${libraryId}/refresh`, {})),
    });
  }

  setFileNamingPattern() {
    return mutationOptions({
      mutationKey: libraryCommandKeys.setFileNamingPattern(),
      mutationFn: ({libraryId, fileNamingPattern}: SetFileNamingPatternVariables) =>
        lastValueFrom(this.http.patch<Library>(`${this.librariesUrl}/${libraryId}/file-naming-pattern`, {fileNamingPattern})),
      onSettled: (_data, _error, _variables, _onMutateResult, {client}) =>
        client.invalidateQueries({queryKey: libraryQueryKeys.all()}),
    });
  }

  scanLibraryPaths() {
    return mutationOptions({
      mutationKey: libraryCommandKeys.scanLibraryPaths(),
      mutationFn: (library: CreateLibraryVariables) => lastValueFrom(this.http.post<number>(`${this.librariesUrl}/scan`, library)),
    });
  }
}
