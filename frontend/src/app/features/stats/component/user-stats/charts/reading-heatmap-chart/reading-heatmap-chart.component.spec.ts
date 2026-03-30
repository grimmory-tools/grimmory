import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around signal-driven effect execution,
// matrix-chart cell sizing, and translated tooltip formatting so completion heatmap aggregation
// can be asserted without a live chart layout engine.
describe.skip('ReadingHeatmapChartComponent', () => {
  it('needs effect and transformation seams to verify year-month counting across the rolling ten-year window', () => {
    // TODO(seam): Cover calculateHeatmapData and processHeatmapData once the effect-driven chart sync is isolated from Angular and Chart.js runtime state.
  });

  it('needs chart-layout seams to verify year labels, alpha scaling, and matrix cell sizing callbacks deterministically', () => {
    // TODO(seam): Cover updateChartData after extracting Chart.js matrix sizing and tooltip metadata behind an adapter.
  });
});
