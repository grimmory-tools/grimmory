import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Subject} from 'rxjs';
import {CdkDragDrop, DragDropModule} from '@angular/cdk/drag-drop';
import {DialogModule} from 'primeng/dialog';
import {ButtonModule} from 'primeng/button';
import {UserService} from '../../../settings/user-management/user.service';
import {takeUntil} from 'rxjs/operators';
import {PeakHoursChartComponent} from './charts/peak-hours-chart/peak-hours-chart.component';
import {FavoriteDaysChartComponent} from './charts/favorite-days-chart/favorite-days-chart.component';
import {ReadingDNAChartComponent} from './charts/reading-dna-chart/reading-dna-chart.component';
import {CompletionTimelineChartComponent} from './charts/completion-timeline-chart/completion-timeline-chart.component';
import {ReadingHabitsChartComponent} from './charts/reading-habits-chart/reading-habits-chart.component';
import {GenreStatsChartComponent} from './charts/genre-stats-chart/genre-stats-chart.component';
import {ReadingHeatmapChartComponent} from './charts/reading-heatmap-chart/reading-heatmap-chart.component';
import {ReadingSessionTimelineComponent} from './charts/reading-session-timeline/reading-session-timeline.component';
import {PersonalRatingChartComponent} from './charts/personal-rating-chart/personal-rating-chart.component';
import {ReadingSessionHeatmapComponent} from './charts/reading-session-heatmap/reading-session-heatmap.component';
import {ReadingProgressChartComponent} from './charts/reading-progress-chart/reading-progress-chart.component';
import {ReadStatusChartComponent} from './charts/read-status-chart/read-status-chart.component';
import {RatingTasteChartComponent} from './charts/rating-taste-chart/rating-taste-chart.component';
import {SeriesProgressChartComponent} from './charts/series-progress-chart/series-progress-chart.component';
import {ReadingBacklogChartComponent} from './charts/reading-backlog-chart/reading-backlog-chart.component';
import {UserChartConfig, UserChartConfigService} from './service/user-chart-config.service';

@Component({
  selector: 'app-user-stats',
  standalone: true,
  imports: [
    CommonModule,
    DragDropModule,
    DialogModule,
    ButtonModule,
    ReadingSessionHeatmapComponent,
    ReadingSessionTimelineComponent,
    GenreStatsChartComponent,
    CompletionTimelineChartComponent,
    FavoriteDaysChartComponent,
    PeakHoursChartComponent,
    ReadingDNAChartComponent,
    ReadingHabitsChartComponent,
    ReadingHeatmapChartComponent,
    PersonalRatingChartComponent,
    ReadingProgressChartComponent,
    ReadStatusChartComponent,
    RatingTasteChartComponent,
    ReadingBacklogChartComponent,
    SeriesProgressChartComponent
  ],
  templateUrl: './user-stats.component.html',
  styleUrls: ['./user-stats.component.scss']
})
export class UserStatsComponent implements OnInit, OnDestroy {
  private readonly destroy$ = new Subject<void>();

  private userService = inject(UserService);
  private chartConfigService = inject(UserChartConfigService);

  public currentYear = new Date().getFullYear();
  public userName: string = '';
  public showConfigPanel = false;
  public charts: UserChartConfig[] = [];
  public visibleCharts: UserChartConfig[] = [];

  ngOnInit(): void {
    this.chartConfigService.charts$
      .pipe(takeUntil(this.destroy$))
      .subscribe(charts => {
        this.charts = charts;
        this.visibleCharts = this.chartConfigService.getVisibleCharts();
      });

    this.userService.userState$
      .pipe(takeUntil(this.destroy$))
      .subscribe(state => {
        if (state.user) {
          this.userName = state.user.name || state.user.username;
        }
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  toggleChart(chartId: string): void {
    this.chartConfigService.toggleChart(chartId);
  }

  drop(event: CdkDragDrop<UserChartConfig[]>): void {
    this.chartConfigService.reorderCharts(event.previousIndex, event.currentIndex);
  }

  toggleConfigPanel(): void {
    this.showConfigPanel = !this.showConfigPanel;
  }

  resetLayout(): void {
    this.chartConfigService.resetLayout();
  }

  hideAllCharts(): void {
    this.charts.forEach(chart => {
      if (chart.enabled) {
        this.chartConfigService.toggleChart(chart.id);
      }
    });
  }

  showAllCharts(): void {
    this.charts.forEach(chart => {
      if (!chart.enabled) {
        this.chartConfigService.toggleChart(chart.id);
      }
    });
  }
}
