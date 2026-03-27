import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around HttpClient request composition,
// query-cache patching, and file-download side effects so attach/detach/delete flows can be
// asserted without coupling to live query-client state.
describe.skip('BookFileService', () => {
  it('needs request and cache seams to verify upload, attach, detach, and delete mutation behavior', () => {
    // TODO(seam): Cover mutation success and cache-update branches once the query client and HTTP layer are isolated behind stable doubles.
  });

  it('needs download seams to verify stream export behavior and toast/reporting paths', () => {
    // TODO(seam): Cover download helpers after blob creation, browser download APIs, and message dispatch are extracted into deterministic adapters.
  });
});
