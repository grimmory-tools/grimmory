import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around date-fns week math, timeline layout
// calculations, stats-service loading, and translated select labels so week navigation and session
// stacking can be asserted without reproducing the full interactive timeline UI.
describe.skip('ReadingSessionTimelineComponent', () => {
  it('needs timeline-layout and stats seams to verify week navigation, session grouping, and tooltip content deterministically', () => {
    // TODO(seam): Cover timeline conversion and overlap layout once the date math and stats loading are extracted behind a smaller adapter.
  });
});
