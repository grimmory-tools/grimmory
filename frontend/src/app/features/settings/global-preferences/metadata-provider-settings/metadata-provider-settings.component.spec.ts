import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs a seam around effect-driven settings hydration and
// the large provider payload editor so save behavior can be asserted without coupling the spec to a
// brittle form snapshot across every provider toggle and token field.
describe.skip('MetadataProviderSettingsComponent', () => {
  it('needs a provider-settings form seam to verify payload assembly and toggle/token interactions deterministically', () => {
    // TODO(seam): Cover applySettings, token gating, and save payload composition once the provider editor state is extracted behind a smaller adapter.
  });
});
