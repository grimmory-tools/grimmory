import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around Chart.js scatter datasets, translated
// tooltip callbacks, and stats-service loading so outlier filtering and dominant-archetype selection
// can be tested without binding to chart rendering internals.
describe.skip('SessionArchetypesChartComponent', () => {
  it('needs chart-data seams to verify scatter dataset grouping and dominant archetype calculations', () => {
    // TODO(seam): Cover filtering and dataset construction once chart callbacks and service responses are isolated from Chart.js runtime concerns.
  });
});
