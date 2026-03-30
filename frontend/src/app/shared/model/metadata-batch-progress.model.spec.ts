import {describe, expect, it} from 'vitest';

import {MetadataBatchStatus, MetadataBatchStatusLabels} from './metadata-batch-progress.model';

describe('metadata-batch-progress model', () => {
  it('keeps the status enum and labels in sync', () => {
    expect(MetadataBatchStatus.IN_PROGRESS).toBe('IN_PROGRESS');
    expect(MetadataBatchStatus.COMPLETED).toBe('COMPLETED');
    expect(MetadataBatchStatus.ERROR).toBe('ERROR');
    expect(MetadataBatchStatus.CANCELLED).toBe('CANCELLED');

    expect(MetadataBatchStatusLabels).toEqual({
      [MetadataBatchStatus.IN_PROGRESS]: 'In Progress',
      [MetadataBatchStatus.COMPLETED]: 'Completed',
      [MetadataBatchStatus.ERROR]: 'Error',
      [MetadataBatchStatus.CANCELLED]: 'Cancelled',
    });
  });
});
