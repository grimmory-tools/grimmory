import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BaseChartDirective} from 'ng2-charts';
import {Tooltip} from 'primeng/tooltip';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {catchError, takeUntil} from 'rxjs/operators';
import {ChartConfiguration, ChartData} from 'chart.js';
import {PageTurnerScoreResponse, UserStatsService} from '../../../../../settings/user-management/user-stats.service';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

type PageTurnerChartData = ChartData<'bar', number[], string>;

@Component({
  selector: 'app-page-turner-chart',
  standalone: true,
  imports: [CommonModule, BaseChartDirective, Tooltip, TranslocoDirective],
  templateUrl: './page-turner-chart.component.html',
  styleUrls: ['./page-turner-chart.component.scss']
})
export class PageTurnerChartComponent implements OnInit, OnDestroy {
  public readonly chartType = 'bar' as const;
  public readonly chartData$: Observable<PageTurnerChartData>;
  public readonly chartOptions: ChartConfiguration['options'];

  public stats = {mostGripping: '', avgGripScore: 0, guiltyPleasure: ''};
  public topBooks: PageTurnerScoreResponse[] = [];

  private readonly userStatsService = inject(UserStatsService);
  private readonly t = inject(TranslocoService);
  private readonly destroy$ = new Subject<void>();
  private readonly chartDataSubject: BehaviorSubject<PageTurnerChartData>;

  constructor() {
    this.chartDataSubject = new BehaviorSubject<PageTurnerChartData>({labels: [], datasets: []});
    this.chartData$ = this.chartDataSubject.asObservable();

    this.chartOptions = {
      indexAxis: 'y',
      responsive: true,
      maintainAspectRatio: false,
      layout: {padding: {top: 10, bottom: 10, left: 10, right: 10}},
      plugins: {
        legend: {display: false},
        tooltip: {
          enabled: true,
          backgroundColor: 'rgba(0, 0, 0, 0.95)',
          titleColor: '#ffffff',
          bodyColor: '#ffffff',
          borderColor: 'rgba(251, 146, 60, 0.8)',
          borderWidth: 2,
          cornerRadius: 8,
          displayColors: false,
          padding: 16,
          titleFont: {size: 14, weight: 'bold'},
          bodyFont: {size: 13},
          callbacks: {
            label: () => ''
          }
        },
        datalabels: {display: false}
      },
      scales: {
        x: {
          title: {
            display: true,
            text: this.t.translate('statsUser.pageTurner.axisGripScore'),
            color: '#ffffff',
            font: {family: "'Inter', sans-serif", size: 13, weight: 'bold'}
          },
          min: 0,
          max: 100,
          ticks: {
            color: '#ffffff',
            font: {family: "'Inter', sans-serif", size: 11}
          },
          grid: {color: 'rgba(255, 255, 255, 0.1)'},
          border: {display: false}
        },
        y: {
          ticks: {
            color: '#ffffff',
            font: {family: "'Inter', sans-serif", size: 11}
          },
          grid: {display: false},
          border: {display: false}
        }
      }
    };
  }

  ngOnInit(): void {
    this.loadData();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private loadData(): void {
    this.userStatsService.getPageTurnerScores()
      .pipe(
        takeUntil(this.destroy$),
        catchError((error) => {
          console.error('Error loading page turner scores:', error);
          return EMPTY;
        })
      )
      .subscribe((data) => {
        this.updateStats(data);
        this.updateChartData(data);
      });
  }

  private updateStats(data: PageTurnerScoreResponse[]): void {
    if (data.length === 0) {
      this.stats = {mostGripping: '-', avgGripScore: 0, guiltyPleasure: '-'};
      this.topBooks = [];
      return;
    }

    this.stats.mostGripping = data[0].bookTitle;
    this.stats.avgGripScore = Math.round(data.reduce((sum, d) => sum + d.gripScore, 0) / data.length);

    const guiltyPleasure = data.find(d => d.gripScore >= 60 && d.personalRating != null && d.personalRating <= 3);
    this.stats.guiltyPleasure = guiltyPleasure?.bookTitle || '-';

    this.topBooks = data.slice(0, 3);
  }

  private updateChartData(data: PageTurnerScoreResponse[]): void {
    const top15 = data.slice(0, 15);

    const labels = top15.map(d => d.bookTitle.length > 25 ? d.bookTitle.substring(0, 25) + '...' : d.bookTitle);
    const values = top15.map(d => d.gripScore);
    const bgColors = top15.map(d => {
      const t = d.gripScore / 100;
      const r = Math.round(59 + t * (239 - 59));
      const g = Math.round(130 + t * (68 - 130));
      const b = Math.round(246 + t * (68 - 246));
      return `rgba(${r}, ${g}, ${b}, 0.85)`;
    });

    if (this.chartOptions?.plugins?.tooltip?.callbacks) {
      this.chartOptions.plugins.tooltip.callbacks.label = (context) => {
        const idx = context.dataIndex;
        const item = top15[idx];
        if (!item) return '';
        const lines = [
          this.t.translate('statsUser.pageTurner.tooltipGripScore', {score: item.gripScore}),
          this.t.translate('statsUser.pageTurner.tooltipSessions', {count: item.totalSessions}),
          this.t.translate('statsUser.pageTurner.tooltipAvgSession', {minutes: Math.round(item.avgSessionDurationSeconds / 60)}),
        ];
        if (item.personalRating) {
          lines.push(this.t.translate('statsUser.pageTurner.tooltipRating', {rating: item.personalRating}));
        }
        return lines;
      };
    }

    this.chartDataSubject.next({
      labels,
      datasets: [{
        label: this.t.translate('statsUser.pageTurner.gripScore'),
        data: values,
        backgroundColor: bgColors,
        borderColor: bgColors.map(c => c.replace('0.85', '1')),
        borderWidth: 1,
        borderRadius: 4,
        barPercentage: 0.8,
        categoryPercentage: 0.7
      }]
    });
  }

  getAccelerationLabel(value: number): string {
    if (value > 5) return this.t.translate('statsUser.pageTurner.increasing');
    if (value < -5) return this.t.translate('statsUser.pageTurner.decreasing');
    return this.t.translate('statsUser.pageTurner.steady');
  }

  getGapLabel(value: number): string {
    if (value < -2) return this.t.translate('statsUser.pageTurner.shrinking');
    if (value > 2) return this.t.translate('statsUser.pageTurner.growing');
    return this.t.translate('statsUser.pageTurner.steady');
  }
}
