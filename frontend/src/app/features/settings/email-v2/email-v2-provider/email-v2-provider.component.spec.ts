import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around current-user bootstrapping, shared
// provider permission logic, dialog close streams, and confirm/toast side effects so provider
// management can be asserted without the full PrimeNG table runtime.
describe.skip('EmailV2ProviderComponent', () => {
  it('needs service seams to verify provider loading, default assignment, sharing toggles, and delete/save outcomes', () => {
    // TODO(seam): Cover loadCurrentUser, loadEmailProviders, saveProvider, deleteProvider, setDefaultProvider, and toggleShared once service streams are isolated behind deterministic doubles.
  });

  it('needs table-edit seams to verify row editing and create-provider dialog refresh behavior', () => {
    // TODO(seam): Cover toggleEdit and openCreateProviderDialog after dialog onClose streams and provider-row state are extracted from the live table runtime.
  });
});
