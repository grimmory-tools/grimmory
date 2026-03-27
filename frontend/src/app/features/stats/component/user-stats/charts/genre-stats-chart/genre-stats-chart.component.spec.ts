import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs a chart-runtime seam around Chart.js canvas
// rendering, tooltip callback execution, and transloco-backed axis formatting so data mapping can
// be asserted without turning the spec into a browser/chart integration harness.
describe.skip('GenreStatsChartComponent', () => {
  it('needs a chart-adapter seam to verify sorted genre aggregation and tooltip formatting', () => {
    // TODO(seam): Cover loadGenreStats and chartData mapping once chart rendering is isolated from the component runtime.
  });

  it('needs a rendering seam to verify translated axis labels and truncation callbacks without a live chart canvas', () => {
    // TODO(seam): Cover chartOptions translation and label truncation after extracting the Chart.js-specific runtime behavior.
  });
});
