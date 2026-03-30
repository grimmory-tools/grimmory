import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around Angular Query detail loading,
// signal-based book navigation, and dialog-hosted tab composition so multi-book metadata editing
// can be asserted without the live query client and dialog runtime.
describe.skip('MultiBookMetadataEditorComponent', () => {
  it('needs query seams to verify filtered-book selection and current-book detail loading', () => {
    // TODO(seam): Cover book filtering and currentBook resolution once injectQuery can be driven with deterministic data without mutating runtime code.
  });

  it('needs navigation seams to verify next/previous boundaries and dialog close behavior', () => {
    // TODO(seam): Cover index navigation and close delegation after dialog state and signal updates are isolated behind a stable test harness.
  });
});
