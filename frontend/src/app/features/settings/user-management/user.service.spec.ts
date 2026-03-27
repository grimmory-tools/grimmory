import {describe, it} from 'vitest';

// NOTE(frontend-seam): Replace this skipped spec once the current-user Angular Query
// lifecycle is wrapped behind a stable seam. The service couples `injectQuery()`,
// auth-token signals, effect-driven cache invalidation, and imperative HTTP/cache sync
// in one root service, which makes high-value assertions brittle here without runtime changes.
describe.skip('UserService', () => {
  it('needs a stable query-client and auth-token seam to verify token-gated current-user loading', () => {
    // TODO(seam): Cover query enablement, currentUser/isUserLoading/userError derivations,
    // and token-cleared cache invalidation after exposing a harnessable query/effect seam.
  });

  it('needs a controlled current-user cache seam to verify updateUserSetting cache writes safely', () => {
    // TODO(seam): Cover cache updates for user settings after the runtime exposes a reliable
    // query cache seam that does not require poking private Angular Query internals.
  });
});
