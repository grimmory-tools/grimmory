import {describe, it} from 'vitest';

// NOTE(frontend-seam): Honest coverage for this component needs a PrimeNG FileUpload host seam,
// controllable library/settings signals, and a stable upload-progress harness for `HttpRequest`
// event streams and dialog close behavior. The current surface mixes view-child state, effects,
// progress events, and UI messaging too tightly for a reliable low-noise unit spec tonight.
describe.skip('BookUploaderComponent', () => {
  it('needs a file-upload seam to verify destination validation, duplicate filtering, and size failures', () => {
    // TODO(seam): Cover file selection and validation after the PrimeNG file chooser state is
    // wrapped behind a testable adapter instead of relying on the live FileUpload view child.
  });

  it('needs an upload-progress seam to verify request batching, status transitions, and bookdrop close behavior', () => {
    // TODO(seam): Cover progress updates and terminal states once upload event streams can be
    // driven without coupling the spec to the full PrimeNG/http runtime.
  });
});
