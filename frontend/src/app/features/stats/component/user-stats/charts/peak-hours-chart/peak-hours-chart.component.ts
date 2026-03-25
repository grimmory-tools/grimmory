import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BaseChartDirective} from 'ng2-charts';
import {ChartConfiguration, ChartData} from 'chart.js';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {catchError, takeUntil} from 'rxjs/operators';
import {Select} from 'primeng/select';
import {Tooltip} from 'primeng/tooltip';
import {FormsModule} from '@angular/forms';
import {PeakHoursResponse, UserStatsService} from '../../../../../settings/user-management/user-stats.service';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

type PeakHoursChartData = ChartData<'line', number[], string>;

@Component({
  selector: 'app-peak-hours-chart',
  standalone: true,
  imports: [CommonModule, BaseChartDirective, Select, FormsModule, Tooltip, TranslocoDirective],
  templateUrl: './peak-hours-chart.component.html',
  styleUrls: ['./peak-hours-chart.component.scss']
})
export class PeakHoursChartComponent implements OnInit, OnDestroy {
  public readonly chartType = 'line' as const;
  public readonly chartData$: Observable<PeakHoursChartData>;
  public readonly chartOptions: ChartConfiguration['options'];

  private readonly userStatsService = inject(UserStatsService);
  private readonly t = inject(TranslocoService);
  private readonly destroy$ = new Subject<void>();
  private readonly chartDataSubject: BehaviorSubject<PeakHoursChartData>;

  public selectedYear: number | null = null;
  public selectedMonth: number | null = null;
  public yearOptions: { label: string; value: number | null }[] = [];
  public monthOptions: { label: string; value: number | null }[] = [];

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
              const sessionsLabel = this.t.translate('statsUser.peakHours.sessions');
              if (label === sessionsLabel) {
                const key = value !== 1 ? 'statsUser.peakHours.tooltipSessionsPlural' : 'statsUser.peakHours.tooltipSessions';
                return this.t.translate(key, {label, value});
              } else {
                return this.t.translate('statsUser.peakHours.tooltipMin', {label, value});
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
            text: this.t.translate('statsUser.peakHours.axisHourOfDay'),
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
            text: this.t.translate('statsUser.peakHours.axisNumberOfSessions'),
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
            text: this.t.translate('statsUser.peakHours.axisAvgDuration'),
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
    this.yearOptions = [{label: this.t.translate('statsUser.peakHours.allYears'), value: null}];
    for (let year = currentYear; year >= currentYear - 10; year--) {
      this.yearOptions.push({label: year.toString(), value: year});
    }
    const monthKeys = ['january', 'february', 'march', 'april', 'may', 'june', 'july', 'august', 'september', 'october', 'november', 'december'];
    this.monthOptions = [
      {label: this.t.translate('statsUser.peakHours.allMonths'), value: null},
      ...monthKeys.map((key, i) => ({label: this.t.translate(`statsUser.peakHours.${key}`), value: i + 1}))
    ];
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
          label: this.t.translate('statsUser.peakHours.sessions'),
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
          label: this.t.translate('statsUser.peakHours.avgDurationMin'),
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
