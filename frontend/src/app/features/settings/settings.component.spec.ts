import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around route/query-param synchronization,
// lazy tab composition, and nested settings child activation so tab selection can be asserted
// without mounting the full router-backed settings shell.
describe.skip('SettingsComponent', () => {
  it('needs router seams to verify initial tab resolution and query-param updates', () => {
    // TODO(seam): Cover activeTab bootstrapping and route synchronization once ActivatedRoute and Router state are isolated behind deterministic doubles.
  });

  it('needs shell seams to verify tab-switch navigation across the composed settings surface', () => {
    // TODO(seam): Cover tab click behavior after the routed settings shell is extracted from PrimeNG tab and router outlet runtime concerns.
  });
});
