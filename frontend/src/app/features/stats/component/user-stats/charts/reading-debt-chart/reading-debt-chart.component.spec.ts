import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around signal-driven monthly aggregation,
// dual-axis Chart.js dataset generation, and translated trend labeling so backlog analysis can be
// tested without coupling the spec to chart rendering internals.
describe.skip('ReadingDebtChartComponent', () => {
  it('needs aggregation seams to verify monthly added and finished counts, running backlog, and trend selection', () => {
    // TODO(seam): Cover processData once the effect-driven chart update path is isolated behind a test seam.
  });

  it('needs chart seams to verify the combined bar-plus-line dataset output and translated labels', () => {
    // TODO(seam): Cover chartData and chartOptions after extracting Chart.js dataset typing and callback behavior from the component runtime.
  });
});
