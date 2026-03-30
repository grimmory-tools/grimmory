import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs a theme-runtime seam around Prime theme tokens,
// localStorage hydration, DOM mutation, and browser-only effects so app state initialization can be
// asserted without changing runtime code.
describe.skip('AppConfigService', () => {
  it('needs a browser storage and theme-token seam to validate state hydration and persistence effects', () => {
    // Intentionally skipped until storage and theme runtime hooks are controllable in tests.
  });

  it('needs a document mutation seam to validate theme updates without coupling the spec to runtime DOM side effects', () => {
    // Intentionally skipped until DOM-side theme application can be driven deterministically.
  });
});
