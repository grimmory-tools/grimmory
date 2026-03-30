import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs a seam around nested Prime confirmation payloads,
// router/navigation side effects, clipboard writes, and multiple dialog/task services so menu-item
// command wiring can be asserted without turning the spec into a brittle integration harness.
describe.skip('LibraryShelfMenuService', () => {
  it('needs a menu-command seam to verify library, shelf, and magic-shelf action wiring deterministically', () => {
    // TODO(seam): Cover confirmation payloads, delete/refresh commands, and dialog launches after introducing a thin menu-action adapter.
  });

  it('needs a browser-side seam to verify clipboard and navigation behavior without relying on global runtime objects', () => {
    // TODO(seam): Cover magic-shelf export and post-delete navigation once clipboard/router effects are injectable.
  });
});
