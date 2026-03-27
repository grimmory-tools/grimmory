import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around dialog payload bootstrapping,
// computed book filtering, autocomplete selection, and attach-file request streams so file
// reassignment can be asserted without the full dialog and query runtime.
describe.skip('BookFileAttacherComponent', () => {
  it('needs dialog and search seams to verify source-book initialization, candidate filtering, and target selection', () => {
    // TODO(seam): Cover ngOnInit, filterBooks, onBookSelect, and getBookDisplayName once dialog data and book-service signals are isolated behind deterministic doubles.
  });

  it('needs attach seams to verify move-files defaults, attach success, attach failure, and close behavior', () => {
    // TODO(seam): Cover attach and closeDialog after app-settings bootstrap, takeUntil teardown, and dialog closing are exposed through a stable test harness.
  });
});
