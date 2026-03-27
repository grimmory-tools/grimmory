import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around PrimeNG file-upload callbacks,
// dialog bootstrapping, and upload stream orchestration so single-file validation and upload state
// transitions can be asserted without browser File objects and the live upload widget runtime.
describe.skip('AdditionalFileUploaderComponent', () => {
  it('needs upload-widget seams to verify file-type resets, size validation, and file list management', () => {
    // TODO(seam): Cover onFileTypeChange, onFilesSelect, onClear, and badge/status helpers after FileUpload callbacks and browser File objects are isolated behind deterministic doubles.
  });

  it('needs upload-service seams to verify upload success, upload failure, and dialog close behavior', () => {
    // TODO(seam): Cover uploadFiles and closeDialog after BookFileService streams and dialog refs are exposed through a stable test harness.
  });
});
