import {describe, it} from 'vitest';

// NOTE(frontend-seam): Real coverage here needs seams around the live book, library, and
// magic-shelf signal graph so dynamic filter aggregation can be asserted without recreating the
// full sidebar filter runtime and evaluator dependencies.
describe.skip('BookFilterService', () => {
  it('needs signal seams to verify library, shelf, and magic-shelf entity filtering across active sidebar filters', () => {
    // TODO(seam): Cover createFilterSignals and filterBooksByEntity once the dependent signals and evaluator service are isolated behind deterministic doubles.
  });

  it('needs aggregation seams to verify filter count sorting, numeric-id coercion, and invalid magic-shelf fallback handling', () => {
    // TODO(seam): Cover buildAndSortFilters, processFilterValue, and filterByMagicShelf after extractor inputs and service state are exposed through a stable harness.
  });
});
