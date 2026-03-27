import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around canvas drawing, requestAnimationFrame,
// computed book-service state, and Sankey layout math so flow processing can be asserted without
// snapshotting imperative canvas output.
describe.skip('BookFlowChartComponent', () => {
  it('needs canvas and layout seams to verify status bucketing, quarter aggregation, and render scheduling deterministically', () => {
    // TODO(seam): Cover Sankey node/link generation once draw operations and animation scheduling are injectable.
  });
});
