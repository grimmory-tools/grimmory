import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around the live summary stream, router
// navigation timing, and widget lifecycle teardown so bookdrop notification state can be tested
// without mounting the live websocket-backed runtime.
describe.skip('BookdropFilesWidgetComponent', () => {
  it('needs subscription seams to verify pending-count updates and timestamp hydration from summary notifications', () => {
    // TODO(seam): Cover ngOnInit once the live summary observable is isolated behind a deterministic subject.
  });

  it('needs routing seams to verify review navigation and destroy-time subscription cleanup', () => {
    // TODO(seam): Cover openReviewDialog and ngOnDestroy after extracting router and subscription lifecycle concerns.
  });
});
