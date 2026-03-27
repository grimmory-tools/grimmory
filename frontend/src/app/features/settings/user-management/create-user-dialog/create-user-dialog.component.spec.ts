import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around the large reactive form, admin
// permission fan-out side effects, and dialog-close behavior so the create-user workflow can be
// tested without mounting the full Prime form stack.
describe.skip('CreateUserDialogComponent', () => {
  it('needs form seams to verify validator setup, admin permission propagation, and library selection shaping', () => {
    // TODO(seam): Cover ngOnInit once the reactive-form and valueChanges runtime is isolated for deterministic assertions.
  });

  it('needs mutation seams to verify invalid-form warnings and create-user success and failure flows', () => {
    // TODO(seam): Cover createUser after extracting message and dialog-close side effects behind test doubles.
  });
});
