import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around computed dashboard config signals,
// magic-shelf rule evaluation, sorting, dialog launches, and page-title side effects so scroller
// selection logic can be tested without instantiating the full dashboard ecosystem.
describe.skip('MainDashboardComponent', () => {
  it('needs dashboard-config and magic-shelf seams to verify scroller book selection and page setup deterministically', () => {
    // TODO(seam): Cover per-scroller book selection, randomization, and page-title behavior after the dependent services are adapterized.
  });
});
