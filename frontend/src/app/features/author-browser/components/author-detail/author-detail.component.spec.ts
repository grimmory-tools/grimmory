import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around route bootstrap, virtual scroller,
// projected child components, and DOM overflow measurement so the detail page can be exercised
// without a full routed view harness.
describe.skip('AuthorDetailComponent', () => {
  it('needs route and service seams to verify author loading, page-title updates, and tab initialization', () => {
    // TODO(seam): Cover ngOnInit and loadAuthor once route params and author-service subscriptions are isolated from the routed component runtime.
  });

  it('needs DOM and child-component seams to verify expansion toggles, quick-match updates, and cache patching', () => {
    // TODO(seam): Cover ngAfterViewChecked and quickMatch after extracting DOM measurement and child component interactions behind adapters.
  });
});
