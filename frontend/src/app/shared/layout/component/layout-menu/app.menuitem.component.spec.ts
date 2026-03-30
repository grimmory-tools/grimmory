import {describe, it} from 'vitest';

// NOTE(frontend-seam): This menu item coordinates router state, menu-service broadcasts, user
// permission effects, dialog launchers, nested expansion state, and link/view-child behavior.
// Useful coverage needs a purpose-built menu host seam, not a brittle isolated mock tangle.
describe.skip('AppMenuitemComponent', () => {
  it('needs a menu-service and router seam to verify active-state propagation and route activation', () => {
    // TODO(seam): Cover route-driven activation, menu broadcasts, and nested expansion after the
    // component can be exercised inside a stable menu host harness.
  });

  it('needs a dialog/link seam to verify openDialog, triggerLink, and icon/count formatting behavior', () => {
    // TODO(seam): Cover command, dialog, and link behavior once those side effects are surfaced
    // behind a narrower, test-friendly contract.
  });
});
