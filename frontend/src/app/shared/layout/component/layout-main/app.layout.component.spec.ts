import {describe, it} from 'vitest';

// NOTE(frontend-seam): Honest layout coverage needs a host harness for router events, renderer
// listeners, overlay subscriptions, body-scroll mutation, and view-child/sidebar interactions.
// In its current shape the component is mostly runtime orchestration around global DOM listeners.
describe.skip('AppLayoutComponent', () => {
  it('needs a router-overlay seam to verify menu hiding on navigation and overlay clicks', () => {
    // TODO(seam): Cover outside-click detection and overlay subscription behavior after router
    // events and renderer listeners are exposed behind a testable boundary.
  });

  it('needs a document-style seam to verify sidebar width initialization and body scroll toggling', () => {
    // TODO(seam): Cover local-storage width hydration and blocked-scroll lifecycle once global
    // document mutations can be asserted without brittle DOM listener wiring.
  });
});
