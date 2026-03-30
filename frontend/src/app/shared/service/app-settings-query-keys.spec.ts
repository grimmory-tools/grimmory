import {describe, expect, it} from 'vitest';

import {APP_SETTINGS_QUERY_KEY, PUBLIC_SETTINGS_QUERY_KEY} from './app-settings-query-keys';

describe('app-settings-query-keys', () => {
  it('uses stable cache keys for private and public settings', () => {
    expect(APP_SETTINGS_QUERY_KEY).toEqual(['appSettings']);
    expect(PUBLIC_SETTINGS_QUERY_KEY).toEqual(['publicSettings']);
  });
});
