import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around note-dialog launches,
// confirmation callbacks, and sorted note-list signals so CRUD behavior can be asserted without
// reproducing the full overlay runtime.
describe.skip('BookNotesComponent', () => {
  it('needs dialog seams to verify add and edit note flows with refresh behavior', () => {
    // TODO(seam): Cover note creation and editing once dialog-launcher interactions are exposed through deterministic doubles.
  });

  it('needs confirmation and sorting seams to verify delete behavior and note ordering', () => {
    // TODO(seam): Cover destructive-note actions and sorted-note projection after confirm dialog payloads and signal updates are isolated for testing.
  });
});
