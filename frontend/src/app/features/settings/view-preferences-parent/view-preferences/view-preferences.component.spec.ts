import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around effect-driven user hydration,
// multiple live collection services, drag-drop ordering, and popover-driven multi-sort editing so
// preference persistence can be asserted without booting the full settings surface.
describe.skip('ViewPreferencesComponent', () => {
  it('needs collection and drag-drop seams to verify global and override preference persistence deterministically', () => {
    // TODO(seam): Cover visible-sort-field ordering, override creation, and auto-save interactions after the collection/popover flows are isolated.
  });
});
