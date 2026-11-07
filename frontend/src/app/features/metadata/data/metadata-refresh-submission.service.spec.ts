import {TestBed} from '@angular/core/testing';
import {QueryClient} from '@tanstack/angular-query-experimental';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {
  createQueryClientHarness,
  flushSignalAndQueryEffects,
} from '../../../core/testing/query-testing';
import {
  MetadataBatchProgressNotification,
  MetadataBatchStatus,
} from '../../../shared/model/metadata-batch-progress.model';
import {AUTHORS_QUERY_KEY} from '../../author-browser/service/author-query-keys';
import {bookQueryKeys} from '../../book/data/book-query-keys';
import {MetadataRefreshSubmissionService} from './metadata-refresh-submission.service';

function batchProgress(status: MetadataBatchStatus): MetadataBatchProgressNotification {
  return {
    taskId: 'metadata-task-1',
    completed: 1,
    total: 1,
    message: 'Refreshing metadata',
    status,
    review: false,
  };
}

describe('MetadataRefreshSubmissionService batch reconciliation', () => {
  let service: MetadataRefreshSubmissionService;
  let queryClient: QueryClient;

  beforeEach(() => {
    const harness = createQueryClientHarness();
    queryClient = harness.queryClient;
    TestBed.configureTestingModule({
      providers: [...harness.providers, MetadataRefreshSubmissionService],
    });
    service = TestBed.inject(MetadataRefreshSubmissionService);
    flushSignalAndQueryEffects();
  });

  afterEach(() => {
    vi.useRealTimers();
    queryClient.clear();
  });

  it('reconciles book and author queries when a batch completes', () => {
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries');
    service.handleBatchProgress(batchProgress(MetadataBatchStatus.COMPLETED));

    expect(invalidate).toHaveBeenCalledWith({queryKey: bookQueryKeys.all()});
    expect(invalidate).toHaveBeenCalledWith({queryKey: AUTHORS_QUERY_KEY, exact: true});
  });

  it('treats an error followed by silence as the end of the batch', () => {
    vi.useFakeTimers();
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries');
    service.handleBatchProgress(batchProgress(MetadataBatchStatus.ERROR));
    expect(invalidate).not.toHaveBeenCalled();

    vi.advanceTimersByTime(5000);
    expect(invalidate).toHaveBeenCalledWith({queryKey: bookQueryKeys.all()});
  });

  it('folds per-book errors into one end-of-batch reconciliation', () => {
    vi.useFakeTimers();
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries');
    service.handleBatchProgress(batchProgress(MetadataBatchStatus.ERROR));
    service.handleBatchProgress(batchProgress(MetadataBatchStatus.IN_PROGRESS));
    vi.advanceTimersByTime(10_000);
    expect(invalidate).not.toHaveBeenCalled();

    service.handleBatchProgress(batchProgress(MetadataBatchStatus.COMPLETED));
    expect(invalidate).toHaveBeenCalledTimes(2);
  });

});
