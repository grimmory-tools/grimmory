import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around DOM sizing, table selection events,
// router-link rendering, and metadata-lock helpers so table interactions can be asserted without
// mounting the full PrimeNG virtual-table runtime.
describe.skip('BookTableComponent', () => {
  it('needs browser seams to verify responsive scroll-height updates and virtual scroller resizing', () => {
    // TODO(seam): Cover setScrollHeight and ngOnChanges after window/document globals are isolated behind deterministic adapters.
  });

  it('needs table-event seams to verify row selection, bulk selection, and clickable metadata cell generation', () => {
    // TODO(seam): Cover selection methods and getCellClickableValue after the PrimeNG table event surface is extracted into a stable test harness.
  });
});
