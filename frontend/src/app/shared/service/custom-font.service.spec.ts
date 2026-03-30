import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs a stable Angular Query harness plus a controllable
// FontFace/document.fonts environment so query cache mutation and browser font registration can be
// asserted without changing runtime code.
describe.skip('CustomFontService', () => {
  it('needs a query-client and font-face seam to validate cache updates after upload and deletion', () => {
    // Intentionally skipped until the service can be exercised without mutating runtime code.
  });

  it('needs a controllable browser font registry seam to validate loadFontFace and removeFontFace behavior', () => {
    // Intentionally skipped until document.fonts and FontFace can be driven deterministically.
  });
});
