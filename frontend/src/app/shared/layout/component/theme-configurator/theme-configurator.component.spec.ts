import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs a stable app-config signal seam plus favicon
// side-effect interception. The component is mostly computed/effect wiring over `AppConfigService`
// state, palette tokens, and `FaviconService`, so a useful spec should validate those reactions
// without reaching through private signal internals.
describe.skip('ThemeConfiguratorComponent', () => {
  it('needs an app-config seam to verify selected palette derivation and color updates', () => {
    // TODO(seam): Cover computed primary/surface selections and updateColors state writes once
    // the config signal can be driven through a stable public harness.
  });

  it('needs a favicon side-effect seam to verify palette-to-favicon synchronization', () => {
    // TODO(seam): Cover favicon updates after exposing a testable effect boundary around the
    // config-driven favicon sync.
  });
});
