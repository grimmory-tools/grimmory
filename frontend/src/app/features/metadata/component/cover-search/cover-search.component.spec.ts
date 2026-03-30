import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around dialog-driven book bootstrapping,
// remote cover-search streams, file-upload handling, and toast side effects so cover selection
// behavior can be asserted without the full overlay and browser file runtime.
describe.skip('CoverSearchComponent', () => {
  it('needs search seams to verify lookup, result selection, and empty-state messaging', () => {
    // TODO(seam): Cover remote search branches once the metadata lookup stream and dialog payload are isolated behind deterministic doubles.
  });

  it('needs upload seams to verify local file import, cover replacement, and error reporting paths', () => {
    // TODO(seam): Cover upload and confirm flows after browser file APIs and message dispatch are extracted into testable adapters.
  });
});
