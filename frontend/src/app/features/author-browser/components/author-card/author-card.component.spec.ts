import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around Prime tiered-menu lifecycle,
// checkbox event payloads, and quick-match service side effects so card interaction logic can be
// asserted without depending on menu overlay internals.
describe.skip('AuthorCardComponent', () => {
  it('needs interaction seams to verify modifier-click selection, card-click emission, and image fallback state', () => {
    // TODO(seam): Cover onCardClick, toggleSelection, and photo state transitions after isolating DOM event details from the component.
  });

  it('needs menu seams to verify lazy menu initialization and quick-match success and failure flows', () => {
    // TODO(seam): Cover initMenu and onQuickMatch once the Prime menu runtime and message side effects are wrapped behind adapters.
  });
});
