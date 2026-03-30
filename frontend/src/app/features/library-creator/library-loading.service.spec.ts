import {describe, it} from 'vitest';

// NOTE(frontend-seam): Replace this skipped spec once the component-factory lifecycle
// is wrapped behind a testable seam. The current service hard-codes Angular's
// `createComponent()` ESM export plus direct `document.body` mutation, and Vitest in
// this browser-mode setup cannot reliably intercept that export to prove attach/detach
// behavior without changing runtime code.
describe.skip('LibraryLoadingService', () => {
  it('needs a component-factory seam to verify dynamic creation and teardown', () => {
    // TODO(seam): Cover component creation, progress updates, and body overflow cleanup
    // after introducing a testable wrapper around createComponent/document.body behavior.
  });
});
