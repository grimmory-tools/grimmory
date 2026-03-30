import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around drag-drop reorder events, computed
// chart enablement state, and child chart composition so library-stats orchestration can be tested
// without mounting the full dashboard shell.
describe.skip('LibraryStatsComponent', () => {
  it('needs state seams to verify selected-library syncing, chart enablement toggles, and category grouping', () => {
    // TODO(seam): Cover selectedLibrary, toggleChart, and getChartsByCategory once the computed service graph is isolated behind test doubles.
  });

  it('needs dashboard seams to verify drag-drop reorder and config reset behavior across the child chart layout', () => {
    // TODO(seam): Cover onChartReorder and resetChartOrder after extracting drag-drop and child component composition concerns.
  });
});
