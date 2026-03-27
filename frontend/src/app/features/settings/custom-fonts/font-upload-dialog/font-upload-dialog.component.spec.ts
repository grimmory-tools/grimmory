import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs browser seams around `document.fonts`, `FontFace`,
// drag-and-drop file objects, and the DynamicDialog upload lifecycle so preview cancellation and
// upload teardown can be asserted without depending on DOM font APIs that Vitest does not provide.
describe.skip('FontUploadDialogComponent', () => {
  it('needs a font-preview seam to verify preview cleanup, drag-drop handling, and upload completion deterministically', () => {
    // TODO(seam): Cover preview token cancellation and dialog close behavior once font loading is wrapped in an injectable adapter.
  });
});
