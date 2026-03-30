import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around Chart.js line datasets, translated
// tooltip/axis callbacks, and month/year filter state so peak-hour aggregation can be checked
// without entangling the spec with chart rendering details.
describe.skip('PeakHoursChartComponent', () => {
  it('needs chart and filter seams to verify hourly session aggregation and dual-axis dataset shaping', () => {
    // TODO(seam): Cover filtered stats loading and dataset production once the chart runtime is wrapped behind an adapter.
  });
});
