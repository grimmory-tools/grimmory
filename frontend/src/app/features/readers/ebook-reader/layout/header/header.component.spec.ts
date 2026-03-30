import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around header-service signals, router
// fallback behavior, and the reader shell template so ebook-header interactions can be tested
// without mounting the full reader layout runtime.
describe.skip('ReaderHeaderComponent', () => {
  it('needs service seams to verify sidebar, notes, search, bookmark, controls, fullscreen, and help actions', () => {
    // TODO(seam): Cover the delegation methods once the header-service signal graph is isolated behind direct test doubles.
  });

  it('needs browser-history seams to verify dashboard fallback versus close delegation', () => {
    // TODO(seam): Cover onClose after extracting window.history and router interactions behind a deterministic test seam.
  });
});
