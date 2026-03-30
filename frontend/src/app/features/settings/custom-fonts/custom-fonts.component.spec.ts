import {describe, it} from 'vitest';

// NOTE(frontend-seam): This component orchestrates async font discovery/loading, dialog-driven
// uploads, confirmation flows, browser font registration, and toast reporting. Real coverage
// needs a dedicated font-runtime seam around `CustomFontService` and the dialog/confirm stack.
describe.skip('CustomFontsComponent', () => {
  it('needs a custom-font runtime seam to verify load success vs failure and browser font readiness', () => {
    // TODO(seam): Cover ensureFonts/loadAllFonts success and failure paths once browser-font
    // registration is exposed behind a stable service seam.
  });

  it('needs a dialog-confirm seam to verify quota checks, upload additions, and delete flows', () => {
    // TODO(seam): Cover upload dialog close handling and confirmed delete behavior after the
    // dialog/confirmation interactions can be driven without full PrimeNG runtime wiring.
  });
});
