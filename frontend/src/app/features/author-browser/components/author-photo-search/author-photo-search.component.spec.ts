import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around DynamicDialog bootstrap state,
// search/upload service flows, and Prime image/dialog runtime interactions so the query and photo
// upload orchestration can be asserted without mounting the full dialog stack.
describe.skip('AuthorPhotoSearchComponent', () => {
  it('needs dialog and service seams to verify initial author-name bootstrapping and sorted photo search results', () => {
    // TODO(seam): Cover ngOnInit and onSearch once the DynamicDialog wiring and async search flow are isolated behind test doubles.
  });

  it('needs upload seams to verify success and failure toast behavior when selecting a photo', () => {
    // TODO(seam): Cover selectAndUploadPhoto after extracting the dialog-close and message side effects from the component runtime.
  });
});
