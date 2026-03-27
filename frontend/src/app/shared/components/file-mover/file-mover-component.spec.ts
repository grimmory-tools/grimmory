import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around dialog config input,
// library-query reactivity, placeholder resolution, and file-operation side effects so
// preview generation and move submission can be asserted without reproducing the full runtime graph.
describe.skip('FileMoverComponent', () => {
  it('needs a query-and-dialog seam to verify preview generation and target-library path updates', () => {
    // TODO(seam): Cover applyPattern and library/path change flows once dialog config and reactive library data are injectable test seams.
  });

  it('needs a file-operations seam to verify move submission and toast behavior without coupling to runtime app settings effects', () => {
    // TODO(seam): Cover move execution, success/failure messaging, and pattern source selection after extracting the side-effectful workflow.
  });
});
