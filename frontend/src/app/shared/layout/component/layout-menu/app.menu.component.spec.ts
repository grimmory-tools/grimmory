import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real menu coverage needs coordinated signals for libraries, shelves,
// magic shelves, authors, books, user permissions, translation changes, and version/dialog
// behavior. The component is a large computed-menu assembler, and a low-value shallow spec would
// not honestly verify the menu trees that matter.
describe.skip('AppMenuComponent', () => {
  it('needs a menu-data seam to verify computed home/library/shelf/magic-shelf menu trees', () => {
    // TODO(seam): Cover computed menu composition once the dependent signals/services can be
    // driven through a stable test harness rather than an all-up runtime mock graph.
  });

  it('needs a version-dialog and local-storage seam to verify side effects around dialogs and sidebar sizing', () => {
    // TODO(seam): Cover version dialog and sidebar width behavior after those side effects are
    // exposed behind a narrower facade.
  });
});
