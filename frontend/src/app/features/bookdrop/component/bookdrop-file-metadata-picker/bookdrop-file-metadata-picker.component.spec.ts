import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around dialog payload bootstrapping,
// autocomplete/search streams, and metadata copy/reset helpers so picker decisions can be
// asserted without the full bookdrop dialog and PrimeNG form runtime.
describe.skip('BookdropFileMetadataPickerComponent', () => {
  it('needs dialog and form seams to verify metadata bootstrap, reset, and apply behavior', () => {
    // TODO(seam): Cover initialization and confirmation branches once DynamicDialog config and reactive-form setup are isolated behind test doubles.
  });

  it('needs search seams to verify candidate loading, selection changes, and duplicate-handling paths', () => {
    // TODO(seam): Cover autocomplete and metadata transfer flows after search streams and select widgets are extracted from the live component runtime.
  });
});
