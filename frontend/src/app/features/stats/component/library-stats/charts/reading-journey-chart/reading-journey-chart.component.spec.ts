import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around monthly acquisition/completion
// aggregation, cumulative line-dataset generation, and translated tooltip callbacks so reading
// journey analysis can be asserted without depending on chart metadata internals.
describe.skip('ReadingJourneyChartComponent', () => {
  it('needs aggregation seams to verify monthly added and finished counts, date ranges, and journey insights', () => {
    // TODO(seam): Cover calculateMonthlyData and calculateInsights once the computed chart output is isolated behind a deterministic adapter.
  });

  it('needs callback seams to verify cumulative dataset shaping and backlog tooltip formatting', () => {
    // TODO(seam): Cover chartData and chartOptions after extracting Chart.js callback metadata from the component runtime.
  });
});
