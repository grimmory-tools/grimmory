import {describe, it} from 'vitest';

// NOTE(frontend-seam): This component is effectively a thin runtime bridge to layout state plus
// a nested menu tree. A worthwhile spec should be written together with a host/layout seam rather
// than as an empty shell assertion.
describe.skip('AppSidebarComponent', () => {
  it('needs a layout host seam to verify sidebar/menu integration without shallow shell noise', () => {
    // TODO(seam): Cover layout state and nested menu rendering once the sidebar is exercised
    // through the same host harness that will be needed for layout/menu behavior.
  });
});
