import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around settings hydration and the composed
// metadata preference subpanels so the container shell can be asserted without mounting the full
// nested settings runtime.
describe.skip('MetadataSettingsComponent', () => {
  it('needs settings seams to verify metadata option bootstrap and bookdrop-download toggling', () => {
    // TODO(seam): Cover loadSettings, initializeSettings, and onMetadataDownloadOnBookdropToggle once app settings and save helpers are exposed through deterministic doubles.
  });

  it('needs child-panel seams to verify metadata option submissions across nested preferences', () => {
    // TODO(seam): Cover onMetadataSubmit after the container shell is isolated from the provider, match-weight, persistence, and review child runtimes.
  });
});
