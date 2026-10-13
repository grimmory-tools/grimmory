import {type StatsChartSize} from '../card/stats-chart-definition';

export interface StatsPageChartConfig {
  readonly id: string;
  readonly size: StatsChartSize;
}

export type StatsChartRowLayout = 'single-small' | 'full' | 'halves' | 'thirds' | 'small-wide';

const VALID_ROWS = new Set(['small,small', 'medium,small', 'medium,medium', 'small,wide', 'small,small,small']);

export function isValidStatsChartRow(charts: readonly StatsPageChartConfig[]): boolean {
  return charts.length <= 1 || VALID_ROWS.has(charts.map(({size}) => size).sort().join(','));
}

export function statsChartColumnClass(row: readonly StatsPageChartConfig[], chart: StatsPageChartConfig): string {
  switch (statsChartRowLayout(row)) {
    case 'full':
      return '@2xl:col-span-12';
    case 'thirds':
      return '@2xl:col-span-4';
    case 'small-wide':
      return chart.size === 'wide' ? '@2xl:col-span-8' : '@2xl:col-span-4';
    case 'single-small':
    case 'halves':
      return '@2xl:col-span-6';
  }
}

export function statsChartRowLayout(row: readonly StatsPageChartConfig[]): StatsChartRowLayout {
  if (row.length === 1) return row[0].size === 'small' ? 'single-small' : 'full';
  if (row.length === 3) return 'thirds';
  return row.some(({size}) => size === 'wide') ? 'small-wide' : 'halves';
}

export function packStatsChartRows<T extends StatsPageChartConfig>(charts: readonly T[]): readonly (readonly T[])[] {
  const rows: T[][] = [];

  for (const chart of charts) {
    const currentRow = rows.at(-1);
    if (currentRow && isValidStatsChartRow([...currentRow, chart])) {
      currentRow.push(chart);
    } else {
      rows.push([chart]);
    }
  }

  return rows;
}
