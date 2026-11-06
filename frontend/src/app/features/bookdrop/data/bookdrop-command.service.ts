import {HttpClient} from '@angular/common/http';
import {inject, Service} from '@angular/core';
import {mutationOptions} from '@tanstack/angular-query-experimental';
import {lastValueFrom} from 'rxjs';

import {API_CONFIG} from '../../../core/config/api-config';
import {postFormData} from '../../../core/data/query-transport';
import {bookdropCommandKeys, bookdropCommandScopes} from './bookdrop-command-keys';
import type {
  BookdropBulkEditResult,
  BookdropFinalizeResult,
  BookdropPatternExtractResult,
  BookdropSelection,
  BulkEditBookdropMetadataVariables,
  ExtractBookdropPatternVariables,
  FinalizeBookdropImportVariables,
} from './bookdrop-command.models';
import {bookdropQueryKeys} from './bookdrop-query-keys';

@Service()
export class BookdropCommandService {
  private readonly http = inject(HttpClient);
  private readonly bookdropUrl = `${API_CONFIG.BASE_URL}/api/v1/bookdrop`;
  private readonly filesUrl = `${API_CONFIG.BASE_URL}/api/v1/files`;

  uploadFile() {
    return mutationOptions({
      mutationKey: bookdropCommandKeys.uploadFile(),
      mutationFn: ({file}: {readonly file: File}) => postFormData(this.http, `${this.filesUrl}/upload/bookdrop`, {file}),
    });
  }

  finalizeImport() {
    return mutationOptions({
      mutationKey: bookdropCommandKeys.finalizeImport(),
      scope: bookdropCommandScopes.files,
      mutationFn: (variables: FinalizeBookdropImportVariables) =>
        lastValueFrom(this.http.post<BookdropFinalizeResult>(`${this.bookdropUrl}/imports/finalize`, variables)),
      onSettled: (_data, _error, _variables, _onMutateResult, {client}) =>
        client.invalidateQueries({queryKey: bookdropQueryKeys.all()}),
    });
  }

  discardFiles() {
    return mutationOptions({
      mutationKey: bookdropCommandKeys.discardFiles(),
      scope: bookdropCommandScopes.files,
      mutationFn: (selection: BookdropSelection) =>
        lastValueFrom(this.http.post<void>(`${this.bookdropUrl}/files/discard`, selection)),
      onSettled: (_data, _error, _variables, _onMutateResult, {client}) =>
        client.invalidateQueries({queryKey: bookdropQueryKeys.all()}),
    });
  }

  rescan() {
    return mutationOptions({
      mutationKey: bookdropCommandKeys.rescan(),
      mutationFn: () => lastValueFrom(this.http.post<void>(`${this.bookdropUrl}/rescan`, {})),
    });
  }

  previewPattern() {
    return mutationOptions({
      mutationKey: bookdropCommandKeys.previewPattern(),
      mutationFn: (variables: ExtractBookdropPatternVariables) => lastValueFrom(
        this.http.post<BookdropPatternExtractResult>(`${this.bookdropUrl}/files/extract-pattern`, {...variables, preview: true}),
      ),
    });
  }

  extractPattern() {
    return mutationOptions({
      mutationKey: bookdropCommandKeys.extractPattern(),
      scope: bookdropCommandScopes.files,
      mutationFn: (variables: ExtractBookdropPatternVariables) => lastValueFrom(
        this.http.post<BookdropPatternExtractResult>(`${this.bookdropUrl}/files/extract-pattern`, {...variables, preview: false}),
      ),
      onSettled: (_data, _error, _variables, _onMutateResult, {client}) =>
        client.invalidateQueries({queryKey: bookdropQueryKeys.all()}),
    });
  }

  bulkEditMetadata() {
    return mutationOptions({
      mutationKey: bookdropCommandKeys.bulkEditMetadata(),
      scope: bookdropCommandScopes.files,
      mutationFn: (variables: BulkEditBookdropMetadataVariables) =>
        lastValueFrom(this.http.post<BookdropBulkEditResult>(`${this.bookdropUrl}/files/bulk-edit`, variables)),
      onSettled: (_data, _error, _variables, _onMutateResult, {client}) =>
        client.invalidateQueries({queryKey: bookdropQueryKeys.all()}),
    });
  }
}
