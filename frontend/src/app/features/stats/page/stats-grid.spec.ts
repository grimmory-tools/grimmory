import {TestBed} from '@angular/core/testing';
import {beforeEach, describe, expect, it} from 'vitest';

import {type StatsChartSize} from '../card/stats-chart-definition';
import {createStatsChartGrid} from './stats-grid';
import {type StatsPageChartConfig} from './stats-layout';

const PREFERENCES_KEY = 'stats-chart-grid-test';
function chart(id: string, size: StatsChartSize): StatsPageChartConfig {
  return {id, size};
}

const CHARTS: readonly StatsPageChartConfig[] = [
  chart('first', 'small'),
  chart('second', 'small'),
  chart('third', 'small'),
  chart('full', 'full'),
];

describe('createStatsChartGrid', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('moves a chart past a row it cannot join without splitting that row or rebuilding the others', () => {
    savePreferences([['full'], ['first', 'second'], ['third']]);
    const grid = createGrid();
    const [, pairRow, lastRow] = grid.rows();

    grid.move('full', 1);

    expect(grid.rows().map(({charts}) => charts.map(({id}) => id))).toEqual([
      ['first', 'second'],
      ['full'],
      ['third'],
    ]);
    expect(grid.rows()[0].id).toBe(pairRow.id);
    expect(grid.rows()[2].id).toBe(lastRow.id);
  });

  it('restores saved preferences, leaving out charts that no longer exist and adding new ones at the end', () => {
    savePreferences([['full'], ['gone', 'second']], ['third']);
    const grid = createGrid();

    expect(grid.rows().map(({charts}) => charts.map(({id}) => id))).toEqual([['full'], ['second'], ['first']]);
    expect(grid.availableCharts().map(({id}) => id)).toEqual(['third']);
  });

  function savePreferences(rows: readonly (readonly string[])[], hidden: readonly string[] = []): void {
    localStorage.setItem(PREFERENCES_KEY, JSON.stringify({rows, hidden}));
  }

  function createGrid() {
    return TestBed.runInInjectionContext(() =>
      createStatsChartGrid({
        preferencesKey: PREFERENCES_KEY,
        defaults: CHARTS,
      }),
    );
  }
});
