import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around virtual-scroller rendering, page
// title initialization, and signal-driven search and filter state so the series browser can be
// tested without mounting the full browser shell.
describe.skip('SeriesBrowserComponent', () => {
  it('needs filter seams to verify search, status filtering, and sort ordering across the derived series list', () => {
    // TODO(seam): Cover applyStatusFilter and applySort once the series-data and book-service signals are isolated behind test doubles.
  });

  it('needs browser-shell seams to verify responsive card sizing, route navigation, and page-title behavior', () => {
    // TODO(seam): Cover ngOnInit and navigateToSeries after extracting router and virtual-scroller concerns behind adapters.
  });
});
