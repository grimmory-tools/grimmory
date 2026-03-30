import {describe, expect, it} from 'vitest';

import {AUTHORS_QUERY_KEY} from './author-query-keys';

describe('AUTHORS_QUERY_KEY', () => {
  it('uses a stable cache key for the author browser', () => {
    expect(AUTHORS_QUERY_KEY).toEqual(['authors']);
  });
});
