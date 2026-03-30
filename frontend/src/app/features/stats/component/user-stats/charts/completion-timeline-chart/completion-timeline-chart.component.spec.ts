import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around Chart.js bar datasets, translated
// tooltip/axis callbacks, and year-based stats loading so completion timeline aggregation can be
// validated without depending on chart directive rendering internals.
describe.skip('CompletionTimelineChartComponent', () => {
  it('needs chart-data seams to verify monthly completion aggregation and year navigation deterministically', () => {
    // TODO(seam): Cover status-bucket folding and dataset updates once chart callbacks are decoupled from the rendering layer.
  });
});
