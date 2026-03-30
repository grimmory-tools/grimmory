import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around confirm dialogs, dialog-launcher
// close streams, and editable table row state so recipient CRUD behavior can be asserted without
// the full PrimeNG table runtime.
describe.skip('EmailV2RecipientComponent', () => {
  it('needs service seams to verify recipient loading, default selection, and save/delete outcomes', () => {
    // TODO(seam): Cover loadRecipientEmails, saveRecipient, deleteRecipient, and setDefaultRecipient once service streams and confirm behavior are isolated behind deterministic doubles.
  });

  it('needs table-edit seams to verify row edit toggling and create-dialog refresh behavior', () => {
    // TODO(seam): Cover toggleEditRecipient and openAddRecipientDialog after dialog onClose streams and row-edit state are extracted from the live table runtime.
  });
});
