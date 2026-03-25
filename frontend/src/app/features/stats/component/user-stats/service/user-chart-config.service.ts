import {computed, Injectable, signal} from '@angular/core';

export interface UserChartConfig {
  id: string;
  enabled: boolean;
  sizeClass: string;
  order: number;
}

const DEFAULT_CHARTS: UserChartConfig[] = [
  {id: 'heatmap', enabled: true, sizeClass: 'chart-full', order: 0},
  {id: 'favorite-days', enabled: true, sizeClass: 'chart-medium', order: 1},
  {id: 'peak-hours', enabled: true, sizeClass: 'chart-medium', order: 2},
  {id: 'timeline', enabled: true, sizeClass: 'chart-full', order: 3},
  {id: 'reading-heatmap', enabled: true, sizeClass: 'chart-small-square', order: 4},
  {id: 'personal-rating', enabled: true, sizeClass: 'chart-small-square', order: 5},
  {id: 'reading-progress', enabled: true, sizeClass: 'chart-small-square', order: 6},
  {id: 'read-status', enabled: true, sizeClass: 'chart-small-square', order: 7},
  {id: 'genre-stats', enabled: true, sizeClass: 'chart-medium', order: 8},
  {id: 'completion-timeline', enabled: true, sizeClass: 'chart-medium', order: 9},
  {id: 'reading-clock', enabled: true, sizeClass: 'chart-medium', order: 10},
  {id: 'page-turner', enabled: true, sizeClass: 'chart-medium', order: 11},
  {id: 'completion-race', enabled: true, sizeClass: 'chart-full', order: 12},
  {id: 'reading-survival', enabled: true, sizeClass: 'chart-medium', order: 13},
  {id: 'book-length', enabled: true, sizeClass: 'chart-medium', order: 14},
  {id: 'rating-taste', enabled: true, sizeClass: 'chart-medium', order: 15},
  {id: 'series-progress', enabled: true, sizeClass: 'chart-medium', order: 16},
  {id: 'reading-debt', enabled: true, sizeClass: 'chart-medium', order: 17},
  {id: 'publication-era', enabled: true, sizeClass: 'chart-medium', order: 18},
  {id: 'session-archetypes', enabled: true, sizeClass: 'chart-medium', order: 19},
  {id: 'book-flow', enabled: true, sizeClass: 'chart-medium', order: 20},
  {id: 'reading-habits', enabled: true, sizeClass: 'chart-medium', order: 21},
  {id: 'reading-dna', enabled: true, sizeClass: 'chart-medium', order: 22}
];

@Injectable({
  providedIn: 'root'
})
export class UserChartConfigService {
  private static readonly STORAGE_KEY = 'userStatsChartConfig';

  private readonly _charts = signal<UserChartConfig[]>(this.loadChartConfig());
  readonly charts = this._charts.asReadonly();
  readonly visibleCharts = computed(() => this.charts().filter(chart => chart.enabled));

  toggleChart(chartId: string): void {
    this.saveCharts(this._charts().map(chart =>
      chart.id === chartId ? {...chart, enabled: !chart.enabled} : chart
    ));
  }

  reorderCharts(charts: UserChartConfig[], previousIndex: number, currentIndex: number): void {
    const sourceIds = charts.map(chart => chart.id);
    const reorderedIds = [...sourceIds];
    const [movedId] = reorderedIds.splice(previousIndex, 1);
    reorderedIds.splice(currentIndex, 0, movedId);

    const currentCharts = [...this._charts()].sort((a, b) => a.order - b.order);
    const chartsById = new Map(currentCharts.map(chart => [chart.id, chart]));
    let reorderedIndex = 0;

    const nextCharts = currentCharts.map(chart => {
      const nextId = sourceIds.includes(chart.id) ? reorderedIds[reorderedIndex++] : chart.id;
      return {
        ...(chartsById.get(nextId) ?? this.getDefaultCharts().find(defaultChart => defaultChart.id === nextId)!),
        order: currentCharts.findIndex(currentChart => currentChart.id === chart.id)
      };
    });

    this.saveCharts(nextCharts);
  }

  resetLayout(): void {
    this.saveCharts(this.getDefaultCharts());
  }

  setAllChartsEnabled(enabled: boolean): void {
    this.saveCharts(this._charts().map(chart => ({...chart, enabled})));
  }

  private saveCharts(charts: UserChartConfig[]): void {
    const nextCharts = [...charts]
      .sort((a, b) => a.order - b.order)
      .map((chart, index) => ({...chart, order: index}));

    this._charts.set(nextCharts);
    localStorage.setItem(UserChartConfigService.STORAGE_KEY, JSON.stringify(nextCharts));
  }

  private getDefaultCharts(): UserChartConfig[] {
    return DEFAULT_CHARTS.map(chart => ({...chart}));
  }

  private loadChartConfig(): UserChartConfig[] {
    const savedConfig = localStorage.getItem(UserChartConfigService.STORAGE_KEY);
    if (!savedConfig) {
      return this.getDefaultCharts();
    }

    try {
      const savedCharts = JSON.parse(savedConfig) as Array<Partial<UserChartConfig>>;
      const savedChartsById = new Map(savedCharts.map(chart => [chart.id, chart]));

      return this.getDefaultCharts()
        .map(chart => {
          const saved = savedChartsById.get(chart.id);
          return {
            id: chart.id,
            enabled: saved?.enabled ?? chart.enabled,
            sizeClass: saved?.sizeClass ?? chart.sizeClass,
            order: saved?.order ?? chart.order
          };
        })
        .sort((a, b) => a.order - b.order);
    } catch (error) {
      console.error('Failed to load chart config', error);
      return this.getDefaultCharts();
    }
  }
}
