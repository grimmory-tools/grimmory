import {describe, expect, it} from 'vitest';

import {MetadataRefreshRequest} from './metadata-refresh-request.model';
import {MetadataRefreshType} from './metadata-refresh-type.enum';

describe('metadata-refresh-request.model', () => {
  it('supports library-wide refresh requests with nested options', () => {
    const request: MetadataRefreshRequest = {
      refreshType: MetadataRefreshType.LIBRARY,
      libraryId: 8,
      refreshOptions: {
        libraryId: 8,
        refreshCovers: true,
        mergeCategories: true,
        reviewBeforeApply: false
      }
    };

    expect(request.refreshType).toBe(MetadataRefreshType.LIBRARY);
    expect(request.refreshOptions?.refreshCovers).toBe(true);
  });

  it('supports book-scoped refresh requests', () => {
    const request: MetadataRefreshRequest = {
      refreshType: MetadataRefreshType.BOOKS,
      bookIds: [1, 2, 3]
    };

    expect(request.bookIds).toEqual([1, 2, 3]);
  });
});
