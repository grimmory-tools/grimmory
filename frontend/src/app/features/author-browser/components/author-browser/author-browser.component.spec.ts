import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around signal/effect synchronization,
// virtual-scroller behavior, router/route query-param state, and cross-service selection logic so
// author browsing can be tested without a full browser shell harness.
describe.skip('AuthorBrowserComponent', () => {
  it('needs state-sync seams to verify author enrichment, filtering, sorting, and active-filter counting', () => {
    // TODO(seam): Cover enrichAuthors, applyFilters, and applySort once the reactive service graph is isolated for deterministic assertions.
  });

  it('needs browser-shell seams to verify selection, scroll restoration, route state, and thumbnail cache-busting behavior', () => {
    // TODO(seam): Cover the virtual-scroller and router orchestration after extracting those concerns behind testable adapters.
  });
});
