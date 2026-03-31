import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real topbar coverage needs a dedicated host harness for view queries,
// timer-driven pulse behavior, router navigation, auth/logout, user-permission signals,
// metadata/bookdrop streams, menu overlays, and transloco language switching. In the current
// shape those concerns are fused tightly enough that a shallow spec would be noisy and dishonest.
describe.skip('AppTopBarComponent', () => {
  it('needs a host-view seam to verify menu visibility, pulse state, and notification/task aggregation', () => {
    // TODO(seam): Cover timer-driven pulse resets, task counts, and bookdrop visibility after
    // the component exposes a narrower facade over its view and timer dependencies.
  });

  it('needs a navigation and language seam to verify route actions and active language switching', () => {
    // TODO(seam): Cover router commands, stats menu composition, and localStorage language writes
    // once those side effects can be driven without mocking the entire topbar runtime.
  });
});
