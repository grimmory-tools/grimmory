import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around the user-service signal effect,
// PrimeNG password controls, and toast/dialog side effects so profile edit and password-change
// branches can be asserted without mounting the full dialog runtime.
describe.skip('UserProfileDialogComponent', () => {
  it('needs user-signal seams to verify profile bootstrap, edit toggling, no-change exits, and update failures', () => {
    // TODO(seam): Cover resetEditForm and updateProfile after the current-user signal and updateUser observable can be driven through deterministic doubles.
  });

  it('needs form and dialog seams to verify password validation, password-change outcomes, and close behavior', () => {
    // TODO(seam): Cover passwordMatchValidator, submitPasswordChange, and closeDialog after the password inputs and message service are isolated behind a stable test harness.
  });
});
