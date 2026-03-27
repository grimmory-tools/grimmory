import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around dialog bootstrap data, autocomplete
// event payloads, and metadata lookup/create flows so the physical-book wizard can be tested
// without mounting the full Prime autocomplete runtime.
describe.skip('AddPhysicalBookDialogComponent', () => {
  it('needs form seams to verify initial library selection, author and category autocomplete, and create gating', () => {
    // TODO(seam): Cover initializeSelectedLibraryEffect and the autocomplete handlers once dialog config and Prime event payloads are isolated.
  });

  it('needs metadata seams to verify ISBN lookup hydration and create-book request shaping', () => {
    // TODO(seam): Cover fetchMetadataByIsbn and createBook after extracting the async service flows and dialog-close side effects.
  });
});
