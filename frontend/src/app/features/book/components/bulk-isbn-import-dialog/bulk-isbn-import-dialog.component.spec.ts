import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around file-reader input, paced async
// import loops, metadata lookup retries, and library-service state so ISBN parsing and import
// progress can be asserted without browser file APIs and timing-heavy runtime behavior.
describe.skip('BulkIsbnImportDialogComponent', () => {
  it('needs parser seams to verify pasted-text parsing, file parsing, duplicate removal, and already-existing ISBN filtering', () => {
    // TODO(seam): Cover parseContent and related normalization helpers once file-reader inputs and existing-library lookup state are isolated behind deterministic doubles.
  });

  it('needs async-loop seams to verify import progress, cancellation, retry timing, and summary transitions', () => {
    // TODO(seam): Cover startImport and progress bookkeeping after metadata lookup, create-book requests, and delay scheduling are extracted into a stable harness.
  });
});
