import {HttpClient} from '@angular/common/http';
import {inject, Injectable} from '@angular/core';
import {mutationOptions, QueryClient} from '@tanstack/angular-query-experimental';
import {lastValueFrom} from 'rxjs';

import {API_CONFIG} from '../../../core/config/api-config';
import {
  MetadataBatchProgressNotification,
  MetadataBatchStatus,
} from '../../../shared/model/metadata-batch-progress.model';
import {invalidateAuthorsQuery} from '../../author-browser/service/author-query-cache';
import {invalidateAllBookQueries} from '../../book/data/book-query-cache';
import {metadataRefreshSubmissionKeys} from './metadata-refresh-submission-keys';
import {RefreshMetadataVariables} from './metadata-refresh-submission.models';

const ERROR_QUIET_PERIOD_MS = 5000;

@Injectable({providedIn: 'root'})
export class MetadataRefreshSubmissionService {
  private readonly http = inject(HttpClient);
  private readonly queryClient = inject(QueryClient);
  private readonly taskUrl = `${API_CONFIG.BASE_URL}/api/v1/tasks/start`;
  private errorReconcileTimer: ReturnType<typeof setTimeout> | null = null;

  refreshMetadata() {
    return mutationOptions({
      mutationKey: metadataRefreshSubmissionKeys.refresh(),
      mutationFn: ({bookIds}: RefreshMetadataVariables) => this.refresh(bookIds),
    });
  }

  private refresh(bookIds: readonly number[]): Promise<void> {
    return lastValueFrom(this.http.post<void>(this.taskUrl, {
      taskType: 'REFRESH_METADATA_MANUAL',
      options: {refreshType: 'BOOKS', bookIds},
    }));
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
  // invalidateQueries never rejects (refetch errors surface through query
  // state, not this promise), so there is nothing to await or catch.
  void invalidateAllBookQueries(client);
  invalidateAuthorsQuery(client);
}
