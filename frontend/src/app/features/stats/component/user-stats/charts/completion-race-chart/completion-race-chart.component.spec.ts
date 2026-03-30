import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around the UserStatsService stream, date
// normalization, and Chart.js line-series assembly so yearly race aggregation can be tested
// without leaning on a full chart runtime.
describe.skip('CompletionRaceChartComponent', () => {
  it('needs service and transformation seams to verify year changes, session grouping, and fastest/slowest book summaries', () => {
    // TODO(seam): Cover loadData and processData once the observable subscription flow is isolated behind a test seam.
  });

  it('needs chart-adapter seams to verify line dataset generation and translated tooltip callbacks', () => {
    // TODO(seam): Cover the chart dataset mapping after extracting Chart.js point metadata and callback wiring from the component.
  });
});
