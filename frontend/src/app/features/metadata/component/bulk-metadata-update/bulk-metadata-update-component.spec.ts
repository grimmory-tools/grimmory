import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around dialog payload setup, reactive-form
// state mutation, autocomplete widgets, and chained metadata-plus-cover uploads so bulk update
// branching can be asserted without the full dialog and PrimeNG runtime.
describe.skip('BulkMetadataUpdateComponent', () => {
  it('needs form seams to verify clear-field toggles, manual autocomplete entry, and payload construction', () => {
    // TODO(seam): Cover onFieldClearToggle, autocomplete handlers, and onSubmit payload mapping once the reactive-form shell is isolated behind deterministic helpers.
  });

  it('needs upload seams to verify metadata success, partial cover-upload failure, and loading-state transitions', () => {
    // TODO(seam): Cover chained updateBooksMetadata and bulkUploadCover branches after dialog, message, and file-upload concerns are exposed through stable doubles.
  });
});
