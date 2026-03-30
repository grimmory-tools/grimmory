import {describe, expect, it} from 'vitest';

// TODO(seam): Selection popups depend on live DOM ranges, reader selection state, and
// annotation actions that should be tested against a mounted reader document.
describe.skip('SelectionPopupComponent', () => {
  it('needs a mounted reader document to cover highlight, note, and dismiss branches', () => {
    expect.hasAssertions();
  });
});
