import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around chart dataset derivation,
// translated radar-axis labels, and Chart.js option callbacks so the reading-profile output can
// be asserted without depending on Chart.js metadata internals.
describe.skip('ReadingDnaChartComponent', () => {
  it('needs aggregation seams to verify mood, pace, genre, and completion-profile data mapping', () => {
    // TODO(seam): Cover chartData construction once the radar dataset adapter is extracted from the live chart wrapper.
  });

  it('needs chart-option seams to verify tooltip and legend behavior for translated profile output', () => {
    // TODO(seam): Cover chartOptions after the translated callback surface is isolated behind deterministic test seams.
  });
});
