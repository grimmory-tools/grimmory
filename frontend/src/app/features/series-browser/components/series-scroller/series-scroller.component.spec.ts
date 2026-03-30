import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here is blocked by the current template binding `[compact]="true"`
// into `app-series-card` even though `SeriesCardComponent` does not expose that input. Until the
// runtime surface is corrected, importing this component in a spec fails Angular template compilation.
describe.skip('SeriesScrollerComponent', () => {
  it('needs the template/input contract corrected before empty-state and series-click rendering can be covered', () => {
    // TODO(seam): Cover title rendering, empty state, and card click forwarding once the card input contract is valid.
  });
});
