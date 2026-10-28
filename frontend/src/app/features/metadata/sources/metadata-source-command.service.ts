import {HttpClient} from '@angular/common/http';
import {inject, Service} from '@angular/core';
import {mutationOptions, QueryClient} from '@tanstack/angular-query-experimental';
import {lastValueFrom} from 'rxjs';

import {API_CONFIG} from '../../../core/config/api-config';
import {
  MetadataBatchProgressNotification,
  MetadataBatchStatus,
} from '../../../shared/model/metadata-batch-progress.model';
import {invalidateAuthorsQuery} from '../../author-browser/service/author-query-cache';
import {bookCommandScopes} from '../../book/data/book-command-keys';
import {requireBookIds} from '../../book/data/book-command.models';
import {applyBookQueryChangeSet, invalidateAllBookQueries} from '../../book/data/book-query-cache';
import {metadataSourceCommandKeys} from './metadata-source-command-keys';
import {RefreshMetadataVariables, SetMetadataProposalStatusVariables} from './metadata-source-command.models';
import {metadataSourceQueryKeys} from './metadata-source-query-keys';

const ERROR_QUIET_PERIOD_MS = 5000;

@Service()
export class MetadataSourceCommandService {
  private readonly http = inject(HttpClient);
  private readonly queryClient = inject(QueryClient);
  private readonly tasksUrl = `${API_CONFIG.BASE_URL}/api/v1/tasks`;
  private readonly booksUrl = `${API_CONFIG.BASE_URL}/api/v1/books`;
  private readonly librariesUrl = `${API_CONFIG.BASE_URL}/api/v1/libraries`;
  private readonly reviewTasksUrl = `${API_CONFIG.BASE_URL}/api/metadata/tasks`;
  private errorReconcileTimer: ReturnType<typeof setTimeout> | null = null;

  refreshMetadata() {
    return mutationOptions({
      mutationKey: metadataSourceCommandKeys.refreshMetadata(),
      mutationFn: (variables: RefreshMetadataVariables) => lastValueFrom(this.http.post<void>(`${this.tasksUrl}/start`, {
        taskType: 'REFRESH_METADATA_MANUAL',
        options: variables.refreshType === 'BOOKS'
          ? {...variables, bookIds: requireBookIds(variables.bookIds)}
          : variables,
      })),
    });
  }

  importSidecar() {
    return mutationOptions({
      mutationKey: metadataSourceCommandKeys.importSidecar(),
      scope: bookCommandScopes.metadata,
      mutationFn: ({bookId}: {readonly bookId: number}) =>
        lastValueFrom(this.http.post<void>(`${this.booksUrl}/${bookId}/sidecar/import`, {})),
      onSettled: async (_data, _error, {bookId}, _onMutateResult, {client}) => {
        invalidateAuthorsQuery(client);
        await applyBookQueryChangeSet(client, {changedBookIds: [bookId]});
      },
    });
  }

  exportSidecar() {
    return mutationOptions({
      mutationKey: metadataSourceCommandKeys.exportSidecar(),
      scope: bookCommandScopes.metadata,
      mutationFn: ({bookId}: {readonly bookId: number}) =>
        lastValueFrom(this.http.post<void>(`${this.booksUrl}/${bookId}/sidecar/export`, {})),
      onSettled: (_data, _error, {bookId}, _onMutateResult, {client}) =>
        client.invalidateQueries({queryKey: metadataSourceQueryKeys.sidecarQueries(bookId)}),
    });
  }

  importLibrarySidecars() {
    return mutationOptions({
      mutationKey: metadataSourceCommandKeys.importLibrarySidecars(),
      scope: bookCommandScopes.metadata,
      mutationFn: ({libraryId}: {readonly libraryId: number}) =>
        lastValueFrom(this.http.post<{readonly message: string; readonly imported: number}>(`${this.librariesUrl}/${libraryId}/sidecar/import-all`, {})),
      onSettled: async (_data, _error, _variables, _onMutateResult, {client}) => {
        invalidateAuthorsQuery(client);
        await invalidateAllBookQueries(client);
      },
    });
  }

  exportLibrarySidecars() {
    return mutationOptions({
      mutationKey: metadataSourceCommandKeys.exportLibrarySidecars(),
      scope: bookCommandScopes.metadata,
      mutationFn: ({libraryId}: {readonly libraryId: number}) =>
        lastValueFrom(this.http.post<{readonly message: string; readonly exported: number}>(`${this.librariesUrl}/${libraryId}/sidecar/export-all`, {})),
      onSettled: (_data, _error, _variables, _onMutateResult, {client}) =>
        client.invalidateQueries({queryKey: metadataSourceQueryKeys.sidecars()}),
    });
  }

  setProposalStatus() {
    return mutationOptions({
      mutationKey: metadataSourceCommandKeys.setProposalStatus(),
      mutationFn: ({taskId, proposalId, status}: SetMetadataProposalStatusVariables) =>
        lastValueFrom(this.http.post<void>(
          `${this.reviewTasksUrl}/${taskId}/proposals/${proposalId}/status`,
          null,
          {params: {status}},
        )),
      onSettled: (_data, _error, _variables, _onMutateResult, {client}) =>
        client.invalidateQueries({queryKey: metadataSourceQueryKeys.reviewTaskQueries()}),
    });
  }

  deleteReviewTask() {
    return mutationOptions({
      mutationKey: metadataSourceCommandKeys.deleteReviewTask(),
      mutationFn: ({taskId}: {readonly taskId: string}) =>
        lastValueFrom(this.http.delete<void>(`${this.reviewTasksUrl}/${taskId}`)),
      onSettled: (_data, error, {taskId}, _onMutateResult, {client}) => {
        if (!error) {
          client.removeQueries({queryKey: metadataSourceQueryKeys.reviewTask(taskId)});
        }
        return client.invalidateQueries({queryKey: metadataSourceQueryKeys.reviewTaskQueries()});
      },
    });
  }

  handleBatchProgress(progress: MetadataBatchProgressNotification): void {
    this.clearErrorReconcileTimer();
    if (progress.status === MetadataBatchStatus.IN_PROGRESS) {
      return;
    }
    if (progress.status === MetadataBatchStatus.ERROR) {
      this.errorReconcileTimer = setTimeout(() => {
        this.errorReconcileTimer = null;
        reconcileMetadataRefresh(this.queryClient);
      }, ERROR_QUIET_PERIOD_MS);
      return;
    }
    reconcileMetadataRefresh(this.queryClient);
  }

  private clearErrorReconcileTimer(): void {
    if (this.errorReconcileTimer !== null) {
      clearTimeout(this.errorReconcileTimer);
      this.errorReconcileTimer = null;
    }
  }
}

function reconcileMetadataRefresh(client: QueryClient): void {
  void invalidateAllBookQueries(client);
  void client.invalidateQueries({queryKey: metadataSourceQueryKeys.reviewTaskQueries()});
  invalidateAuthorsQuery(client);
}
