import {describe, it} from 'vitest';

// NOTE(frontend-seam): This component currently couples DynamicDialog config/ref data,
// router navigation, live library/icon services, format-count HTTP reads, drag-drop, and
// transloco-backed option initialization in one large standalone surface. Honest coverage
// needs a narrower presenter seam or a purpose-built harness, not a brittle all-up mock stack.
describe('LibraryCreatorComponent', () => {
  it('needs a dialog-and-library seam to verify create vs edit initialization and duplicate-name handling', () => {
    // TODO(seam): Cover dialog mode hydration, icon normalization, and duplicate-name validation
    // once the component state can be supplied without mocking the full runtime graph.
  });

  it('needs a stable interaction seam to verify format selection, folder updates, and submit payload assembly', () => {
    // TODO(seam): Cover allowed-format toggles, folder selection, and create/update submission
    // after these flows are exposed behind testable methods or a smaller facade.
  });
});
