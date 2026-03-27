import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around metadata search requests, regional
// selection state, and match-result toast behavior so author matching can be tested without
// coupling the spec to the Prime form runtime.
describe.skip('AuthorMatchComponent', () => {
  it('needs search seams to verify query bootstrapping, canSearch gating, and result-list replacement', () => {
    // TODO(seam): Cover ngOnInit and search once the author-service request flow is isolated for deterministic assertions.
  });

  it('needs match seams to verify request shaping and success and failure message flows', () => {
    // TODO(seam): Cover matchAuthor after extracting the async mutation and toast side effects behind test seams.
  });
});
