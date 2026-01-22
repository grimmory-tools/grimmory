import {Component, inject, Input, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BaseChartDirective} from 'ng2-charts';
import {ChartConfiguration, ChartData} from 'chart.js';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {catchError, takeUntil} from 'rxjs/operators';
import {CompletionTimelineResponse, UserStatsService} from '../../../../../settings/user-management/user-stats.service';

type CompletionChartData = ChartData<'bar', number[], string>;

@Component({
  selector: 'app-completion-timeline-chart',
  standalone: true,
  imports: [CommonModule, BaseChartDirective],
  templateUrl: './completion-timeline-chart.component.html',
  styleUrls: ['./completion-timeline-chart.component.scss']
})
export class CompletionTimelineChartComponent implements OnInit, OnDestroy {
  @Input() initialYear: number = new Date().getFullYear();

  public currentYear: number = new Date().getFullYear();
  public readonly chartType = 'bar' as const;
  public readonly chartData$: Observable<CompletionChartData>;
  public readonly chartOptions: ChartConfiguration['options'];

  private readonly userStatsService = inject(UserStatsService);
  private readonly destroy$ = new Subject<void>();
  private readonly chartDataSubject: BehaviorSubject<CompletionChartData>;

  private readonly monthNames = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

  constructor() {
    this.chartDataSubject = new BehaviorSubject<CompletionChartData>({
      labels: [],
      datasets: []
    });
    this.chartData$ = this.chartDataSubject.asObservable();

    this.chartOptions = {
      responsive: true,
      maintainAspectRatio: false,
      layout: {
        padding: {top: 10, bottom: 10, left: 10, right: 10}
      },
      plugins: {
        legend: {
          display: true,
          position: 'top',
          labels: {
            color: '#ffffff',
            font: {family: "'Inter', sans-serif", size: 11},
            boxWidth: 12,
            padding: 10
          }
        },
        tooltip: {
          enabled: true,
          backgroundColor: 'rgba(0, 0, 0, 0.9)',
          titleColor: '#ffffff',
          bodyColor: '#ffffff',
          borderColor: '#ffffff',
          borderWidth: 1,
          cornerRadius: 6,
          displayColors: true,
          padding: 12,
          titleFont: {size: 14, weight: 'bold'},
          bodyFont: {size: 13},
          callbacks: {
            label: (context) => {
              const label = context.dataset.label || '';
              const value = context.parsed.y;
              return `${label}: ${value} book${value !== 1 ? 's' : ''}`;
            }
          }
        },
        datalabels: {display: false}
      },
      scales: {
        x: {
          title: {
            display: true,
            text: 'Month',
            color: '#ffffff',
            font: {
              family: "'Inter', sans-serif",
              size: 13,
              weight: 'bold'
            }
          },
          ticks: {
            color: '#ffffff',
            font: {family: "'Inter', sans-serif", size: 11}
          },
          grid: {display: false},
          border: {display: false}
        },
        y: {
          title: {
            display: true,
            text: 'Number of Books',
            color: '#ffffff',
            font: {
              family: "'Inter', sans-serif",
              size: 13,
              weight: 'bold'
            }
          },
          beginAtZero: true,
          ticks: {
            color: '#ffffff',
            font: {family: "'Inter', sans-serif", size: 11},
            stepSize: 1
          },
          grid: {
            color: 'rgba(255, 255, 255, 0.1)'
          },
          border: {display: false}
        }
      }
    };
  }

  ngOnInit(): void {
    this.currentYear = this.initialYear;
    this.loadCompletionTimeline(this.currentYear);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  public changeYear(delta: number): void {
    this.currentYear += delta;
    this.loadCompletionTimeline(this.currentYear);
  }

  private loadCompletionTimeline(year: number): void {
    this.userStatsService.getCompletionTimelineForYear(year)
      .pipe(
        takeUntil(this.destroy$),
        catchError((error) => {
          console.error('Error loading completion timeline:', error);
          return EMPTY;
        })
      )
      .subscribe((data) => {
        this.updateChartData(data);
      });
  }

  private updateChartData(timeline: CompletionTimelineResponse[]): void {
    const monthlyData = new Map<number, CompletionTimelineResponse>();
    timeline.forEach(item => {
      monthlyData.set(item.month, item);
    });

    const labels = this.monthNames;

    const completedBooks = this.monthNames.map((_, index) => {
      const monthData = monthlyData.get(index + 1);
      if (!monthData) return 0;
      const read = monthData.statusBreakdown['READ'] || 0;
      const partiallyRead = monthData.statusBreakdown['PARTIALLY_READ'] || 0;
      return read + partiallyRead;
    });

    const activeReading = this.monthNames.map((_, index) => {
      const monthData = monthlyData.get(index + 1);
      if (!monthData) return 0;
      const reading = monthData.statusBreakdown['READING'] || 0;
      const reReading = monthData.statusBreakdown['RE_READING'] || 0;
      return reading + reReading;
    });

    const pausedBooks = this.monthNames.map((_, index) => {
      const monthData = monthlyData.get(index + 1);
      return monthData?.statusBreakdown['PAUSED'] || 0;
    });

    const discontinuedBooks = this.monthNames.map((_, index) => {
      const monthData = monthlyData.get(index + 1);
      if (!monthData) return 0;
      const abandoned = monthData.statusBreakdown['ABANDONED'] || 0;
      const wontRead = monthData.statusBreakdown['WONT_READ'] || 0;
      return abandoned + wontRead;
    });

    this.chartDataSubject.next({
      labels,
      datasets: [
        {
          label: 'Completed',
          data: completedBooks,
          backgroundColor: 'rgba(106, 176, 76, 0.8)',
          borderColor: 'rgba(106, 176, 76, 1)',
          borderWidth: 1,
          borderRadius: 4,
          barPercentage: 0.8,
          categoryPercentage: 0.6
        },
        {
          label: 'Active Reading',
          data: activeReading,
          backgroundColor: 'rgba(59, 130, 246, 0.8)',
          borderColor: 'rgba(59, 130, 246, 1)',
          borderWidth: 1,
          borderRadius: 4,
          barPercentage: 0.8,
          categoryPercentage: 0.6
        },
        {
          label: 'Paused',
          data: pausedBooks,
          backgroundColor: 'rgba(255, 193, 7, 0.8)',
          borderColor: 'rgba(255, 193, 7, 1)',
          borderWidth: 1,
          borderRadius: 4,
          barPercentage: 0.8,
          categoryPercentage: 0.6
        },
        {
          label: 'Discontinued',
          data: discontinuedBooks,
          backgroundColor: 'rgba(239, 68, 68, 0.8)',
          borderColor: 'rgba(239, 68, 68, 1)',
          borderWidth: 1,
          borderRadius: 4,
          barPercentage: 0.8,
          categoryPercentage: 0.6
        }
      ]
    });
  }
}
