import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around quick-settings service signals,
// viewport-dependent getters, and the large option matrix for fit, scroll, slideshow, and
// magnifier controls so behavior can be tested without mounting the full reader shell.
describe.skip('CbxQuickSettingsComponent', () => {
  it('needs state seams to verify option labeling, page-view toggles, and current-label helpers', () => {
    // TODO(seam): Cover the derived option getters and toggle helpers once the quick-settings service signal graph is isolated behind test doubles.
  });

  it('needs viewport seams to verify phone-layout gating and close-overlay behavior', () => {
    // TODO(seam): Cover isPhonePortrait and onOverlayClick after extracting window-size and service-close interactions behind a deterministic seam.
  });
});
