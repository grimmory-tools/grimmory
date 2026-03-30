import {describe, expect, it} from 'vitest';

import {CURRENT_USER_QUERY_KEY} from './user-query-keys';

describe('user-query-keys', () => {
  it('uses a stable cache key for the current user record', () => {
    expect(CURRENT_USER_QUERY_KEY).toEqual(['currentUser']);
  });
});
