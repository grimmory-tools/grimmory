import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around image preloading, fullscreen,
// touch/mouse/keyboard browser events, and the CBX header/sidebar/footer service graph so the
// reader can be tested without reproducing the full interactive runtime.
describe.skip('CbxReaderComponent', () => {
  it('needs an interaction-runtime seam to verify pagination, reader controls, and bookmark/note state', () => {
    // TODO(seam): Cover page transitions and control wiring once browser-event and image-loading dependencies are abstracted.
  });

  it('needs a session-and-route seam to verify startup, teardown, and series-navigation behavior deterministically', () => {
    // TODO(seam): Cover progress persistence and route-driven loading after introducing testable adapters around the reader runtime.
  });
});
