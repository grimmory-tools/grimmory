import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around animation timing, signal-driven
// sidebar state, and note/bookmark editing callbacks so open/close and sidebar interaction flows
// can be asserted without the full reader shell runtime.
describe.skip('CbxSidebarComponent', () => {
  it('needs animation seams to verify delayed close behavior and overlay interactions', () => {
    // TODO(seam): Cover closeWithAnimation and onOverlayClick once timeout scheduling and service open-state signals are isolated behind deterministic doubles.
  });

  it('needs sidebar-service seams to verify page, bookmark, note, and search interactions', () => {
    // TODO(seam): Cover navigation, delete, edit, and search delegation after the sidebar service signal graph is exposed through a stable test harness.
  });
});
