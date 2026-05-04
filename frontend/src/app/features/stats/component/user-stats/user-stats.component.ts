import {Component, computed, inject, signal} from '@angular/core';
import {CdkDragDrop, DragDropModule} from '@angular/cdk/drag-drop';
import {Dialog} from 'primeng/dialog';
import {Button} from 'primeng/button';
import {UserService} from '../../../settings/user-management/user.service';
import {TranslocoDirective} from '@jsverse/transloco';
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
import {PageTurnerChartComponent} from './charts/page-turner-chart/page-turner-chart.component';
import {CompletionRaceChartComponent} from './charts/completion-race-chart/completion-race-chart.component';
import {ReadingSurvivalChartComponent} from './charts/reading-survival-chart/reading-survival-chart.component';
import {ReadingClockChartComponent} from './charts/reading-clock-chart/reading-clock-chart.component';
import {BookLengthChartComponent} from './charts/book-length-chart/book-length-chart.component';
import {BookFlowChartComponent} from './charts/book-flow-chart/book-flow-chart.component';
import {ReadingDebtChartComponent} from './charts/reading-debt-chart/reading-debt-chart.component';
import {PublicationEraChartComponent} from './charts/publication-era-chart/publication-era-chart.component';
import {SessionArchetypesChartComponent} from './charts/session-archetypes-chart/session-archetypes-chart.component';
import {UserChartConfig, UserChartConfigService} from './service/user-chart-config.service';

import {provideCharts, withDefaultRegisterables} from 'ng2-charts';
import ChartDataLabels from 'chartjs-plugin-datalabels';

@Component({
  selector: 'app-user-stats',
  standalone: true,
  imports: [
    DragDropModule,
    Dialog,
    Button,
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
    SeriesProgressChartComponent,
    PageTurnerChartComponent,
    CompletionRaceChartComponent,
    ReadingSurvivalChartComponent,
    ReadingClockChartComponent,
    BookLengthChartComponent,
    BookFlowChartComponent,
    ReadingDebtChartComponent,
    PublicationEraChartComponent,
    SessionArchetypesChartComponent,
    TranslocoDirective
  ],
  providers: [provideCharts(withDefaultRegisterables(), ChartDataLabels)],
  templateUrl: './user-stats.component.html',
  styleUrls: ['./user-stats.component.scss']
})
export class UserStatsComponent {
  private userService = inject(UserService);
  private chartConfigService = inject(UserChartConfigService);

  public currentYear = new Date().getFullYear();
  public readonly userName = computed(() => {
    const user = this.userService.currentUser();
    return user ? user.name || user.username : '';
  });
  public readonly showConfigPanel = signal(false);
  public readonly charts = this.chartConfigService.charts;
  public readonly visibleCharts = this.chartConfigService.visibleCharts;

  toggleChart(chartId: string): void {
    this.chartConfigService.toggleChart(chartId);
  }

  drop(event: CdkDragDrop<UserChartConfig[]>): void {
    this.chartConfigService.reorderCharts(event.container.data, event.previousIndex, event.currentIndex);
  }

  toggleConfigPanel(): void {
    this.showConfigPanel.update(visible => !visible);
  }

  resetLayout(): void {
    this.chartConfigService.resetLayout();
  }

  hideAllCharts(): void {
    this.chartConfigService.setAllChartsEnabled(false);
  }

  showAllCharts(): void {
    this.chartConfigService.setAllChartsEnabled(true);
  }

  chartNameKey(chartId: string): string {
    const [firstSegment, ...rest] = chartId.split('-');
    const chartName = firstSegment + rest
      .map(segment => segment.charAt(0).toUpperCase() + segment.slice(1))
      .join('');

    return `chartNames.${chartName}`;
  }
}
