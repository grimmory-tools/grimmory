import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around route-param signals, Angular Query
// detail loading, selection state, and the large series dashboard template so the series page
// control flow can be asserted without mounting the full browser and query runtime.
describe.skip('SeriesPageComponent', () => {
  it('needs route and query seams to verify filtered series books, cover-book resolution, and description loading', () => {
    // TODO(seam): Cover the computed series view model once route params and injectQuery data can be driven through a deterministic harness.
  });

  it('needs interaction seams to verify selection, menu actions, metadata flows, and overflow handling', () => {
    // TODO(seam): Cover selection state, action menus, and description expansion after overlay, loading-service, and DOM measurement concerns are isolated behind test adapters.
  });
});
