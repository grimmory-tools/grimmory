import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around dialog config/bootstrap state,
// paginator state, confirmation flows, and multi-step duplicate merge orchestration so the
// component can be tested without building a full dialog-plus-confirmation harness.
describe.skip('DuplicateMergerComponent', () => {
  it('needs scanning seams to verify preset toggles, duplicate detection request shaping, and display-group initialization', () => {
    // TODO(seam): Cover applyPreset and scan once the dialog config and duplicate scan subscription flow are isolated behind test doubles.
  });

  it('needs merge seams to verify target selection, dismissal, pagination, and attach-file success and failure handling', () => {
    // TODO(seam): Cover the merge workflow after extracting confirmation, loader, and dialog-close side effects from the component runtime.
  });
});
