import {Injectable} from '@angular/core';
import {BehaviorSubject, Observable} from 'rxjs';
import {ReadingSessionHeatmapComponent} from '../charts/reading-session-heatmap/reading-session-heatmap.component';
import {FavoriteDaysChartComponent} from '../charts/favorite-days-chart/favorite-days-chart.component';
import {PeakHoursChartComponent} from '../charts/peak-hours-chart/peak-hours-chart.component';
import {ReadingSessionTimelineComponent} from '../charts/reading-session-timeline/reading-session-timeline.component';
import {ReadingHeatmapChartComponent} from '../charts/reading-heatmap-chart/reading-heatmap-chart.component';
import {PersonalRatingChartComponent} from '../charts/personal-rating-chart/personal-rating-chart.component';
import {ReadingProgressChartComponent} from '../charts/reading-progress-chart/reading-progress-chart.component';
import {ReadStatusChartComponent} from '../charts/read-status-chart/read-status-chart.component';
import {GenreStatsChartComponent} from '../charts/genre-stats-chart/genre-stats-chart.component';
import {CompletionTimelineChartComponent} from '../charts/completion-timeline-chart/completion-timeline-chart.component';
import {RatingTasteChartComponent} from '../charts/rating-taste-chart/rating-taste-chart.component';
import {SeriesProgressChartComponent} from '../charts/series-progress-chart/series-progress-chart.component';
import {ReadingDNAChartComponent} from '../charts/reading-dna-chart/reading-dna-chart.component';
import {ReadingHabitsChartComponent} from '../charts/reading-habits-chart/reading-habits-chart.component';
import {ReadingBacklogChartComponent} from '../charts/reading-backlog-chart/reading-backlog-chart.component';

export interface UserChartConfig {
  id: string;
  title: string;
  component: unknown;
  enabled: boolean;
  sizeClass: string;
  order: number;
}

@Injectable({
  providedIn: 'root'
})
export class UserChartConfigService {
  private readonly STORAGE_KEY = 'userStatsChartConfig';

  private readonly defaultCharts: UserChartConfig[] = [
    {id: 'heatmap', title: 'Reading Session Heatmap', component: ReadingSessionHeatmapComponent, enabled: true, sizeClass: 'chart-full', order: 0},
    {id: 'favorite-days', title: 'Favorite Reading Days', component: FavoriteDaysChartComponent, enabled: true, sizeClass: 'chart-medium', order: 1},
    {id: 'peak-hours', title: 'Peak Reading Hours', component: PeakHoursChartComponent, enabled: true, sizeClass: 'chart-medium', order: 2},
    {id: 'timeline', title: 'Reading Session Timeline', component: ReadingSessionTimelineComponent, enabled: true, sizeClass: 'chart-full', order: 3},
    {id: 'reading-heatmap', title: 'Reading Activity Heatmap', component: ReadingHeatmapChartComponent, enabled: true, sizeClass: 'chart-small-square', order: 4},
    {id: 'personal-rating', title: 'Personal Rating Distribution', component: PersonalRatingChartComponent, enabled: true, sizeClass: 'chart-small-square', order: 5},
    {id: 'reading-progress', title: 'Reading Progress Distribution', component: ReadingProgressChartComponent, enabled: true, sizeClass: 'chart-small-square', order: 6},
    {id: 'read-status', title: 'Reading Status Distribution', component: ReadStatusChartComponent, enabled: true, sizeClass: 'chart-small-square', order: 7},
    {id: 'genre-stats', title: 'Genre Statistics', component: GenreStatsChartComponent, enabled: true, sizeClass: 'chart-medium', order: 8},
    {id: 'completion-timeline', title: 'Completion Timeline', component: CompletionTimelineChartComponent, enabled: true, sizeClass: 'chart-medium', order: 9},
    {id: 'rating-taste', title: 'Rating Taste Comparison', component: RatingTasteChartComponent, enabled: true, sizeClass: 'chart-medium', order: 10},
    {id: 'series-progress', title: 'Series Progress Tracker', component: SeriesProgressChartComponent, enabled: true, sizeClass: 'chart-medium', order: 11},
    {id: 'reading-dna', title: 'Reading DNA Profile', component: ReadingDNAChartComponent, enabled: true, sizeClass: 'chart-medium', order: 12},
    {id: 'reading-habits', title: 'Reading Habits Analysis', component: ReadingHabitsChartComponent, enabled: true, sizeClass: 'chart-medium', order: 13},
    {id: 'reading-backlog', title: 'Reading Backlog Analysis', component: ReadingBacklogChartComponent, enabled: true, sizeClass: 'chart-full', order: 14},
  ];

  private chartsSubject = new BehaviorSubject<UserChartConfig[]>(this.loadChartConfig());
  public charts$: Observable<UserChartConfig[]> = this.chartsSubject.asObservable();

  getVisibleCharts(): UserChartConfig[] {
    return this.chartsSubject.value.filter(chart => chart.enabled);
  }

  toggleChart(chartId: string): void {
    const charts = this.chartsSubject.value;
    const chart = charts.find(c => c.id === chartId);
    if (chart) {
      chart.enabled = !chart.enabled;
      this.saveChartConfig(charts);
      this.chartsSubject.next([...charts]);
    }
  }

  reorderCharts(previousIndex: number, currentIndex: number): void {
    const charts = [...this.chartsSubject.value];
    const [movedChart] = charts.splice(previousIndex, 1);
    charts.splice(currentIndex, 0, movedChart);

    charts.forEach((chart, index) => {
      chart.order = index;
    });

    this.saveChartConfig(charts);
    this.chartsSubject.next(charts);
  }

  resetLayout(): void {
    const resetCharts = JSON.parse(JSON.stringify(this.defaultCharts));
    this.saveChartConfig(resetCharts);
    this.chartsSubject.next(resetCharts);
  }

  private saveChartConfig(charts: UserChartConfig[]): void {
    const config = charts.map(chart => ({
      id: chart.id,
      enabled: chart.enabled,
      sizeClass: chart.sizeClass,
      order: chart.order
    }));
    localStorage.setItem(this.STORAGE_KEY, JSON.stringify(config));
  }

  private loadChartConfig(): UserChartConfig[] {
    const savedConfig = localStorage.getItem(this.STORAGE_KEY);
    if (!savedConfig) {
      return JSON.parse(JSON.stringify(this.defaultCharts));
    }

    try {
      const config = JSON.parse(savedConfig);
      const charts = JSON.parse(JSON.stringify(this.defaultCharts));

      config.forEach((saved: Partial<UserChartConfig>) => {
        const chart = charts.find((c: UserChartConfig) => c.id === saved.id);
        if (chart) {
          chart.enabled = saved.enabled;
          chart.sizeClass = saved.sizeClass;
          chart.order = saved.order ?? chart.order;
        }
      });

      charts.sort((a: UserChartConfig, b: UserChartConfig) => a.order - b.order);

      return charts;
    } catch (e) {
      console.error('Failed to load chart config', e);
      return JSON.parse(JSON.stringify(this.defaultCharts));
    }
  }
}
