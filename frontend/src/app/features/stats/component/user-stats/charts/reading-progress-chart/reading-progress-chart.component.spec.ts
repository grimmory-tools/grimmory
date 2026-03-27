import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around computed Chart.js doughnut state,
// legend-generation callbacks, and tooltip formatting so progress bucketing can be asserted
// without depending on chart metadata internals.
describe.skip('ReadingProgressChartComponent', () => {
  it('needs chart-data seams to verify progress-range bucketing across the different reader progress sources', () => {
    // TODO(seam): Cover calculateReadingProgressStats and getBookProgress once the doughnut chart runtime is extracted from the component.
  });

  it('needs callback seams to verify legend labels and translated tooltip descriptions deterministically', () => {
    // TODO(seam): Cover chartOptions callback behavior after wrapping Chart.js legend and tooltip metadata behind a test seam.
  });
});
