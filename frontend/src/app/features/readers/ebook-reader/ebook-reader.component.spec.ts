import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around the Foliate custom element runtime,
// DOM container bootstrapping, route-derived file loading, and the large injected reader service
// graph so the ebook reader can be tested without a browser-backed integration harness.
describe.skip('EbookReaderComponent', () => {
  it('needs loader seams to verify Foliate initialization, font loading, and view setup failure handling', () => {
    // TODO(seam): Cover ngOnInit and setupView once the DOM container and custom element runtime are wrapped behind adapters.
  });

  it('needs orchestration seams to verify route-based book loading, selection and note flows, and destroy-time cleanup', () => {
    // TODO(seam): Cover loadBookFromAPI and ngOnDestroy after extracting the reader service graph and URL object lifecycle behind test seams.
  });
});
