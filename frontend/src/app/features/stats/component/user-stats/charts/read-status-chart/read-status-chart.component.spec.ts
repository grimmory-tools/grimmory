import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around computed Chart.js doughnut data,
// legend-generation callbacks, and tooltip formatting so read-status aggregation can be asserted
// without depending on chart metadata internals.
describe.skip('ReadStatusChartComponent', () => {
  it('needs chart-data seams to verify status bucketing, legend generation, and tooltip formatting deterministically', () => {
    // TODO(seam): Cover read-status aggregation once the Chart.js legend and tooltip callbacks are extracted behind a testable adapter.
  });
});
