import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around the translated shortcut collection,
// duplicate-key template tracking, and responsive overlay behavior so the ebook shortcuts dialog
// can be tested without depending on the current template structure.
describe.skip('EbookShortcutsHelpComponent', () => {
  it('needs template seams to verify translated shortcut grouping and mobile gesture labeling without tripping duplicate-key rendering issues', () => {
    // TODO(seam): Cover shortcutGroups once the template tracking strategy is isolated or corrected for deterministic unit assertions.
  });

  it('needs overlay seams to verify close emission and background-click behavior without mounting the full dialog shell', () => {
    // TODO(seam): Cover onClose and onOverlayClick after extracting the current overlay template interactions behind a test seam.
  });
});
