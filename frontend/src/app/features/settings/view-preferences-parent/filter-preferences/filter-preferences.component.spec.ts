import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around signal-backed user-preference
// hydration, drag/drop reorder behavior, and PrimeNG selection widgets so filter persistence can
// be asserted without the full view-preferences runtime.
describe.skip('FilterPreferencesComponent', () => {
  it('needs preference seams to verify bootstrap, reset, and save behavior for filter visibility state', () => {
    // TODO(seam): Cover the effect-driven settings sync once user preferences can be injected through deterministic doubles.
  });

  it('needs drag/drop seams to verify ordering and selection changes without CDK/PrimeNG widget runtime coupling', () => {
    // TODO(seam): Cover reorder and toggle flows after the drag-drop and pick-list interactions are isolated behind a testable adapter.
  });
});
