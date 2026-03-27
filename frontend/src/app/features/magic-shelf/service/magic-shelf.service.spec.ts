import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs a stable Angular Query plus auth-token
// harness that can drive `injectQuery()`, cache invalidation, and the computed `shelves`
// signal without reaching into private query internals. The service also mixes HTTP writes,
// effect-driven cache eviction, and rule evaluation against live book signals.
describe.skip('MagicShelfService', () => {
  it('needs a query-client seam to verify token-gated shelf loading and cache invalidation', () => {
    // TODO(seam): Cover token-enabled loading, token-cleared query removal, and save/delete
    // invalidation once the runtime exposes a stable Angular Query seam for this service.
  });

  it('needs a controllable books signal seam to verify getBookCountValue against parsed shelf rules', () => {
    // TODO(seam): Cover invalid JSON handling and book-count evaluation after the book source
    // can be driven without coupling tests to private signal/query internals.
  });
});
