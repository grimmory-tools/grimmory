import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BaseChartDirective} from 'ng2-charts';
import {ChartConfiguration, ChartData} from 'chart.js';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {catchError, takeUntil} from 'rxjs/operators';
import {Select} from 'primeng/select';
import {FormsModule} from '@angular/forms';
import {PeakHoursResponse, UserStatsService} from '../../../../../settings/user-management/user-stats.service';

type PeakHoursChartData = ChartData<'line', number[], string>;

@Component({
  selector: 'app-peak-hours-chart',
  standalone: true,
  imports: [CommonModule, BaseChartDirective, Select, FormsModule],
  templateUrl: './peak-hours-chart.component.html',
  styleUrls: ['./peak-hours-chart.component.scss']
})
export class PeakHoursChartComponent implements OnInit, OnDestroy {
  public readonly chartType = 'line' as const;
  public readonly chartData$: Observable<PeakHoursChartData>;
  public readonly chartOptions: ChartConfiguration['options'];

  private readonly userStatsService = inject(UserStatsService);
  private readonly destroy$ = new Subject<void>();
  private readonly chartDataSubject: BehaviorSubject<PeakHoursChartData>;

  public selectedYear: number | null = null;
  public selectedMonth: number | null = null;
  public yearOptions: { label: string; value: number | null }[] = [];
  public monthOptions: { label: string; value: number | null }[] = [
    {label: 'All Months', value: null},
    {label: 'January', value: 1},
    {label: 'February', value: 2},
    {label: 'March', value: 3},
    {label: 'April', value: 4},
    {label: 'May', value: 5},
    {label: 'June', value: 6},
    {label: 'July', value: 7},
    {label: 'August', value: 8},
    {label: 'September', value: 9},
    {label: 'October', value: 10},
    {label: 'November', value: 11},
    {label: 'December', value: 12}
  ];

  constructor() {
    this.chartDataSubject = new BehaviorSubject<PeakHoursChartData>({
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
              if (label === 'Sessions') {
                return `${label}: ${value} session${value !== 1 ? 's' : ''}`;
              } else {
                return `${label}: ${value} min`;
              }
            }
          }
        },
        datalabels: {display: false}
      },
      scales: {
        x: {
          title: {
            display: true,
            text: 'Hour of Day',
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
          grid: {
            color: 'rgba(255, 255, 255, 0.1)'
          },
          border: {display: false}
        },
        y: {
          type: 'linear',
          display: true,
          position: 'left',
          title: {
            display: true,
            text: 'Number of Sessions',
            color: 'rgba(34, 197, 94, 0.9)',
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
        },
        y1: {
          type: 'linear',
          display: true,
          position: 'right',
          title: {
            display: true,
            text: 'Avg Duration (min)',
            color: 'rgba(251, 191, 36, 0.9)',
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
            callback: function (value) {
              return (typeof value === 'number' ? Math.round(value) : '0') + 'm';
            }
          },
          grid: {
            drawOnChartArea: false
          },
          border: {display: false}
        }
      }
    };
    this.initializeYearOptions();
  }

  ngOnInit(): void {
    this.loadPeakHours();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private initializeYearOptions(): void {
    const currentYear = new Date().getFullYear();
    this.yearOptions = [{label: 'All Years', value: null}];
    for (let year = currentYear; year >= currentYear - 10; year--) {
      this.yearOptions.push({label: year.toString(), value: year});
    }
  }

  public onFilterChange(): void {
    this.loadPeakHours();
  }

  private loadPeakHours(): void {
    const year = this.selectedYear ?? undefined;
    const month = this.selectedMonth ?? undefined;

    this.userStatsService.getPeakHours(year, month)
      .pipe(
        takeUntil(this.destroy$),
        catchError((error) => {
          console.error('Error loading peak hours:', error);
          return EMPTY;
        })
      )
      .subscribe((data) => {
        this.updateChartData(data);
      });
  }

  private updateChartData(peakHours: PeakHoursResponse[]): void {
    const hourMap = new Map<number, PeakHoursResponse>();
    peakHours.forEach(item => {
      hourMap.set(item.hourOfDay, item);
    });

    const allHours = Array.from({length: 24}, (_, i) => i);
    const labels = allHours.map(h => this.formatHour(h));

    const sessionCounts = allHours.map(hour => {
      const hourData = hourMap.get(hour);
      return hourData?.sessionCount || 0;
    });

    // Calculate average duration per session (in minutes) for each hour
    const avgDurations = allHours.map(hour => {
      const hourData = hourMap.get(hour);
      if (hourData && hourData.sessionCount > 0) {
        return Math.round(hourData.totalDurationSeconds / 60 / hourData.sessionCount);
      }
      return 0;
    });

    this.chartDataSubject.next({
      labels,
      datasets: [
        {
          label: 'Sessions',
          data: sessionCounts,
          borderColor: 'rgba(34, 197, 94, 0.9)',
          backgroundColor: 'rgba(34, 197, 94, 0.1)',
          borderWidth: 2,
          tension: 0.4,
          fill: true,
          pointRadius: 4,
          pointHoverRadius: 6,
          pointBackgroundColor: 'rgba(34, 197, 94, 0.9)',
          pointBorderColor: '#ffffff',
          pointBorderWidth: 2,
          yAxisID: 'y'
        },
        {
          label: 'Avg Duration (min)',
          data: avgDurations,
          borderColor: 'rgba(251, 191, 36, 0.9)',
          backgroundColor: 'rgba(251, 191, 36, 0.1)',
          borderWidth: 2,
          tension: 0.4,
          fill: true,
          pointRadius: 4,
          pointHoverRadius: 6,
          pointBackgroundColor: 'rgba(251, 191, 36, 0.9)',
          pointBorderColor: '#ffffff',
          pointBorderWidth: 2,
          yAxisID: 'y1'
        }
      ]
    });
  }

  private formatHour(hour: number): string {
    if (hour === 0) return '12 AM';
    if (hour === 12) return '12 PM';
    if (hour < 12) return `${hour} AM`;
    return `${hour - 12} PM`;
  }
}
