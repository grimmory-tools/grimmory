import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around Prime split-button/tiered-menu browser
// hooks such as `window.matchMedia`, plus effect-driven settings hydration and metadata-regeneration
// subscriptions, so preference saves can be asserted without depending on Prime runtime internals.
describe.skip('GlobalPreferencesComponent', () => {
  it('needs a menu and settings seam to verify toggle persistence, file-size validation, and cover-regeneration actions', () => {
    // TODO(seam): Cover saveSetting and regenerateCovers once the split-button/menu interactions are isolated behind a smaller adapter.
  });
});
