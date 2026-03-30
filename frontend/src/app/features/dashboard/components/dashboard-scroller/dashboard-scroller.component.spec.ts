import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around ngx-infinite-scroll, nested book-card
// interactions, and overlay/menu state so scroller paging and open-menu coordination can be tested
// without booting a full dashboard rendering stack.
describe.skip('DashboardScrollerComponent', () => {
  it('needs scroller and card seams to verify force-ebook mode, infinite scrolling, and menu-toggle behavior', () => {
    // TODO(seam): Cover scroller rendering and book-card coordination once the scroll and card interactions are isolated.
  });
});
