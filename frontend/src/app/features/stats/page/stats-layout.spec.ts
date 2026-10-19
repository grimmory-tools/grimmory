import {describe, expect, it} from 'vitest';

import {packStatsChartRows, type StatsPageChartConfig} from './stats-layout';
import {type StatsChartSize} from '../card/stats-chart-definition';

function chart(id: string, size: StatsChartSize): StatsPageChartConfig {
  return {id, size};
}

describe('stats chart layout', () => {
  it('packs charts in order without letting one row consume a later gap', () => {
    const charts = [
      chart('wide', 'wide'),
      chart('first-small', 'small'),
      chart('medium', 'medium'),
      chart('second-small', 'small'),
      chart('full', 'full'),
      chart('third-small', 'small'),
    ];

    expect(packStatsChartRows(charts).map((row) => row.map(({id}) => id))).toEqual([
      ['wide', 'first-small'],
      ['medium', 'second-small'],
      ['full'],
      ['third-small'],
    ]);
  });
});
