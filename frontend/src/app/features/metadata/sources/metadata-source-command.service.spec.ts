import {TestBed} from '@angular/core/testing';
import {QueryClient} from '@tanstack/angular-query-experimental';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {createQueryClientHarness, flushSignalAndQueryEffects} from '../../../core/testing/query-testing';
import {
  MetadataBatchProgressNotification,
  MetadataBatchStatus,
} from '../../../shared/model/metadata-batch-progress.model';
import {AUTHORS_QUERY_KEY} from '../../author-browser/service/author-query-keys';
import {bookQueryKeys} from '../../book/data/book-query-keys';
import {metadataSourceQueryKeys} from './metadata-source-query-keys';
import {MetadataSourceCommandService} from './metadata-source-command.service';

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

describe('MetadataSourceCommandService batch reconciliation', () => {
  let service: MetadataSourceCommandService;
  let queryClient: QueryClient;

  beforeEach(() => {
    const harness = createQueryClientHarness();
    queryClient = harness.queryClient;
    TestBed.configureTestingModule({
      providers: [...harness.providers, MetadataSourceCommandService],
    });
    service = TestBed.inject(MetadataSourceCommandService);
    flushSignalAndQueryEffects();
  });

  afterEach(() => {
    vi.useRealTimers();
    queryClient.clear();
  });

  it('reconciles book, sidecar, review task and author queries when a batch completes', () => {
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries');
    service.handleBatchProgress(batchProgress(MetadataBatchStatus.COMPLETED));

    expect(invalidate).toHaveBeenCalledWith({queryKey: bookQueryKeys.all()});
    expect(invalidate).toHaveBeenCalledWith({queryKey: metadataSourceQueryKeys.sidecars()});
    expect(invalidate).toHaveBeenCalledWith({queryKey: metadataSourceQueryKeys.reviewTaskQueries()});
    expect(invalidate).toHaveBeenCalledWith({queryKey: AUTHORS_QUERY_KEY, exact: true});
  });

  it('treats an error followed by silence as the end of the batch', () => {
    vi.useFakeTimers();
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries');
    service.handleBatchProgress(batchProgress(MetadataBatchStatus.ERROR));
    vi.advanceTimersByTime(4999);
    expect(invalidate).not.toHaveBeenCalled();

    vi.advanceTimersByTime(1);
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
    expect(invalidate).toHaveBeenCalledTimes(5);
  });
});
