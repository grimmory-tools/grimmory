import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around the embedpdf viewer,
// reader-session lifecycle, route-driven book loading, and annotation persistence so the
// component can be tested without booting the full PDF runtime and browser document hooks.
describe.skip('PdfReaderComponent', () => {
  it('needs a viewer-runtime seam to verify page, zoom, spread, and annotation flows deterministically', () => {
    // TODO(seam): Cover load, navigation, and annotation persistence after wrapping the PDF viewer and reader-session integrations.
  });

  it('needs a route-and-session seam to verify startup and teardown side effects without real browser navigation', () => {
    // TODO(seam): Cover session start/end and progress persistence once route/book-detail dependencies are injectable test adapters.
  });
});
