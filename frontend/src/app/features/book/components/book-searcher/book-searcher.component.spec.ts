import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around debounced signal-to-observable
// search state, global document click listeners, and router navigation so search dropdown behavior
// can be tested without a browser-event harness.
describe.skip('BookSearcherComponent', () => {
  it('needs debounce seams to verify filtered-book output, loading state, and active-index reset behavior', () => {
    // TODO(seam): Cover the debounced search signal and filteredBooks computed state once timing concerns are extracted for direct control.
  });

  it('needs event seams to verify keyboard navigation, outside-click clearing, and routed book selection', () => {
    // TODO(seam): Cover onKeydown and onDocumentClick after isolating host listeners and router side effects behind adapters.
  });
});
