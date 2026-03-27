import {describe, expect, it} from 'vitest';

import {MetadataRefreshType} from './metadata-refresh-type.enum';

describe('MetadataRefreshType', () => {
  it('exposes both supported refresh scopes', () => {
    expect(MetadataRefreshType).toEqual({
      BOOKS: 'BOOKS',
      LIBRARY: 'LIBRARY'
    });
  });
});
