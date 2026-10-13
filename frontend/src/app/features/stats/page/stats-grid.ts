import {computed, inject, signal} from '@angular/core';

import {LocalStorageService} from '../../../shared/service/local-storage.service';
import {
  isValidStatsChartRow,
  packStatsChartRows,
  type StatsChartRowLayout,
  statsChartRowLayout,
  type StatsPageChartConfig,
} from './stats-layout';

export interface StatsChartLayoutRow<Chart extends StatsPageChartConfig> {
  readonly id: string;
  readonly charts: readonly Chart[];
}

export interface StatsChartGridOptions<Chart extends StatsPageChartConfig> {
  readonly preferencesKey: string;
  readonly defaults: readonly Chart[];
}

interface StoredStatsPreferences {
  readonly rows: readonly (readonly string[])[];
  readonly hidden: readonly string[];
}

interface StatsChartReorderPreview<Chart extends StatsPageChartConfig> {
  readonly sourceRowId: string;
  readonly chart: Chart;
}

export type StatsChartPreviewLayout = StatsChartRowLayout | 'own-row-before' | 'own-row-after' | 'emptied';

let nextRowId = 0;

export function createStatsChartGrid<Chart extends StatsPageChartConfig>({preferencesKey, defaults}: StatsChartGridOptions<Chart>) {
  const storage = inject(LocalStorageService);
  const defaultsById: ReadonlyMap<string, Chart> = new Map(defaults.map((chart) => [chart.id, chart]));

  const saved = loadPreferences();
  const rows = signal<readonly StatsChartLayoutRow<Chart>[]>(saved ? savedLayout(saved) : makeRows(packStatsChartRows(defaults)));
  let reorderPreview: StatsChartReorderPreview<Chart> | null = null;
  const previewLayouts = signal<ReadonlyMap<string, StatsChartPreviewLayout>>(new Map());

  const enabledCharts = computed(() => rows().flatMap(({charts}) => charts));
  const availableCharts = computed(() => {
    const enabled = new Set(enabledCharts().map(({id}) => id));
    return defaults.filter(({id}) => !enabled.has(id));
  });

  function dropResult(chart: Chart, targetRowId: string, index: number): readonly StatsChartLayoutRow<Chart>[] | null {
    const current = rows();
    const target = current.find(({id}) => id === targetRowId);
    if (!target) return null;

    const targetCharts = target.charts.filter(({id}) => id !== chart.id);
    const insertAt = Math.max(0, Math.min(index, targetCharts.length));
    targetCharts.splice(insertAt, 0, chart);
    const joins = target.charts.includes(chart) || isValidStatsChartRow(targetCharts);

    return current.flatMap((row): StatsChartLayoutRow<Chart>[] => {
      if (row === target && joins) return [{...row, charts: targetCharts}];

      const charts = row.charts.filter(({id}) => id !== chart.id);
      const rest = charts.length > 0 ? [{...row, charts}] : [];
      if (row !== target) return rest;

      const ownRow = makeRow([chart]);
      return insertAt === 0 ? [ownRow, ...rest] : [...rest, ownRow];
    });
  }

  function setLayout(layout: readonly StatsChartLayoutRow<Chart>[]): void {
    rows.set(layout.filter(({charts}) => charts.length > 0));
    savePreferences();
  }

  function savePreferences(): void {
    const preferences: StoredStatsPreferences = {
      rows: rows().map(({charts}) => charts.map(({id}) => id)),
      hidden: availableCharts().map(({id}) => id),
    };
    storage.set(preferencesKey, preferences);
  }

  function loadPreferences(): StoredStatsPreferences | null {
    const stored = storage.get<unknown>(preferencesKey);
    return isStoredPreferences(stored) ? stored : null;
  }

  function savedLayout(stored: StoredStatsPreferences): readonly StatsChartLayoutRow<Chart>[] {
    const hidden = new Set(stored.hidden);
    const placed = new Set<string>();
    const layout: StatsChartLayoutRow<Chart>[] = [];

    for (const savedRow of stored.rows) {
      const charts = savedRow.flatMap((id) => {
        const chart = defaultsById.get(id);
        if (!chart || hidden.has(id) || placed.has(id)) return [];
        placed.add(id);
        return [chart];
      });
      layout.push(...makeRows(packStatsChartRows(charts)));
    }

    const unplaced = defaults.filter(({id}) => !placed.has(id) && !hidden.has(id));
    layout.push(...makeRows(packStatsChartRows(unplaced)));
    return layout;
  }

  return {
    rows: rows.asReadonly(),
    enabledCharts,
    availableCharts,

    previewLayout(rowId: string): StatsChartPreviewLayout | null {
      return previewLayouts().get(rowId) ?? null;
    },

    startReorderPreview(sourceRowId: string, chart: Chart): void {
      reorderPreview = {sourceRowId, chart};
      previewLayouts.set(new Map());
    },

    previewReorder(targetRowId: string, currentIndex: number): void {
      const preview = reorderPreview;
      const result = preview && preview.sourceRowId !== targetRowId && dropResult(preview.chart, targetRowId, currentIndex);
      if (!preview || !result) {
        previewLayouts.set(new Map());
        return;
      }

      const source = result.find(({id}) => id === preview.sourceRowId);
      const targetIndex = result.findIndex(({id}) => id === targetRowId);
      const target = result[targetIndex];
      previewLayouts.set(new Map<string, StatsChartPreviewLayout>([
        [preview.sourceRowId, source ? statsChartRowLayout(source.charts) : 'emptied'],
        [
          targetRowId,
          target.charts.includes(preview.chart)
            ? statsChartRowLayout(target.charts)
            : result[targetIndex - 1]?.charts.includes(preview.chart) ? 'own-row-before' : 'own-row-after',
        ],
      ]));
    },

    clearReorderPreview(): void {
      reorderPreview = null;
      previewLayouts.set(new Map());
    },

    add(chart: Chart): void {
      setLayout([...rows(), makeRow([chart])]);
    },

    remove(chartId: string): void {
      setLayout(rows().map((row) => ({...row, charts: row.charts.filter(({id}) => id !== chartId)})));
    },

    reorder(chart: Chart, targetRowId: string, index: number): void {
      const layout = dropResult(chart, targetRowId, index);
      if (layout) setLayout(layout);
    },

    move(chartId: string, delta: -1 | 1): void {
      const charts = enabledCharts();
      const index = charts.findIndex(({id}) => id === chartId);
      const neighbour = index < 0 || index + delta < 0 ? undefined : charts.at(index + delta);
      if (!neighbour) return;

      const chart = charts[index];
      const after = delta === 1;
      setLayout(rows().flatMap((row): StatsChartLayoutRow<Chart>[] => {
        const rest = row.charts.filter(({id}) => id !== chartId);
        if (!rest.includes(neighbour)) return [{...row, charts: rest}];

        const joined = [...rest];
        joined.splice(rest.indexOf(neighbour) + (after ? 1 : 0), 0, chart);
        if (isValidStatsChartRow(joined)) return [{...row, charts: joined}];

        const ownRow = makeRow([chart]);
        return after ? [{...row, charts: rest}, ownRow] : [ownRow, {...row, charts: rest}];
      }));
    },

    resetOrder(): void {
      const enabled = new Set(enabledCharts().map(({id}) => id));
      setLayout(makeRows(packStatsChartRows(defaults.filter(({id}) => enabled.has(id)))));
    },
  };
}

function makeRows<Chart extends StatsPageChartConfig>(rows: readonly (readonly Chart[])[]): readonly StatsChartLayoutRow<Chart>[] {
  return rows.map((charts) => makeRow(charts));
}

function makeRow<Chart extends StatsPageChartConfig>(charts: readonly Chart[]): StatsChartLayoutRow<Chart> {
  return {id: `stats-chart-row-${nextRowId++}`, charts: [...charts]};
}

function isStoredPreferences(value: unknown): value is StoredStatsPreferences {
  return (
    typeof value === 'object'
    && value !== null
    && 'rows' in value
    && Array.isArray(value.rows)
    && value.rows.every((row) => Array.isArray(row))
    && 'hidden' in value
    && Array.isArray(value.hidden)
  );
}
