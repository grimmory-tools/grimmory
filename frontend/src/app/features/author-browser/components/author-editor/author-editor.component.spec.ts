import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around Prime file-upload events, dynamic
// dialog launchers, and author-update side effects so the editor workflow can be exercised without
// the full upload and dialog runtime.
describe.skip('AuthorEditorComponent', () => {
  it('needs form and save seams to verify lock toggling, metadata payload shaping, and success and failure toasts', () => {
    // TODO(seam): Cover saveMetadata and applyLockStates once the author service and message side effects are isolated behind test doubles.
  });

  it('needs dialog and upload seams to verify photo search reopening, upload callbacks, and timestamp refresh behavior', () => {
    // TODO(seam): Cover openPhotoSearch and upload handlers after extracting Prime dialog and file-upload runtime details.
  });
});
