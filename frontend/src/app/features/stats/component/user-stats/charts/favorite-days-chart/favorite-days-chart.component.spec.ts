import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around Chart.js bar datasets, translated
// axis/tooltip labels, and year/month filter state so favorite-day aggregation can be validated
// without coupling tests to the chart directive runtime.
describe.skip('FavoriteDaysChartComponent', () => {
  it('needs chart and filter seams to verify favorite-day aggregation and dual-axis dataset construction', () => {
    // TODO(seam): Cover filtered stats loading and chart dataset shaping once chart callbacks are wrapped behind a lightweight adapter.
  });
});
