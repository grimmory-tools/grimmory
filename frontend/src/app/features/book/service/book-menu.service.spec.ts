import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around confirmation-dialog callbacks,
// loader orchestration, and message-service side effects so menu-action branching can be asserted
// without reproducing the full imperative overlay runtime.
describe.skip('BookMenuService', () => {
  it('needs dialog seams to verify delete, merge, send, rescan, and metadata-refresh action branching', () => {
    // TODO(seam): Cover menu item creation and destructive-action callbacks once confirm dialog payloads can be asserted directly.
  });

  it('needs loader and toast seams to verify success, failure, and bulk-operation messaging paths', () => {
    // TODO(seam): Cover async action handlers after loader wrappers and message dispatch are extracted behind deterministic collaborators.
  });
});
