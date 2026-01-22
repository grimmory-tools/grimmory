import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BaseChartDirective} from 'ng2-charts';
import {ChartConfiguration, ChartData} from 'chart.js';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {catchError, takeUntil} from 'rxjs/operators';
import {Select} from 'primeng/select';
import {FormsModule} from '@angular/forms';
import {FavoriteDaysResponse, UserStatsService} from '../../../../../settings/user-management/user-stats.service';

type FavoriteDaysChartData = ChartData<'bar', number[], string>;

@Component({
  selector: 'app-favorite-days-chart',
  standalone: true,
  imports: [CommonModule, BaseChartDirective, Select, FormsModule],
  templateUrl: './favorite-days-chart.component.html',
  styleUrls: ['./favorite-days-chart.component.scss']
})
export class FavoriteDaysChartComponent implements OnInit, OnDestroy {
  public readonly chartType = 'bar' as const;
  public readonly chartData$: Observable<FavoriteDaysChartData>;
  public readonly chartOptions: ChartConfiguration['options'];

  private readonly userStatsService = inject(UserStatsService);
  private readonly destroy$ = new Subject<void>();
  private readonly chartDataSubject: BehaviorSubject<FavoriteDaysChartData>;

  private readonly allDays = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];

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
    this.chartDataSubject = new BehaviorSubject<FavoriteDaysChartData>({
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
                const hours = Math.floor(value);
                const minutes = Math.floor((value % 1) * 60);
                return `${label}: ${hours}h ${minutes}m`;
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
            text: 'Day of Week',
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
          type: 'linear',
          display: true,
          position: 'left',
          title: {
            display: true,
            text: 'Number of Sessions',
            color: 'rgba(139, 92, 246, 1)',
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
            text: 'Duration (hours)',
            color: 'rgba(236, 72, 153, 1)',
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
              return (typeof value === 'number' ? value.toFixed(1) : '0.0') + 'h';
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
    this.loadFavoriteDays();
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
    this.loadFavoriteDays();
  }

  private loadFavoriteDays(): void {
    const year = this.selectedYear ?? undefined;
    const month = this.selectedMonth ?? undefined;

    this.userStatsService.getFavoriteDays(year, month)
      .pipe(
        takeUntil(this.destroy$),
        catchError((error) => {
          console.error('Error loading favorite days:', error);
          return EMPTY;
        })
      )
      .subscribe((data) => {
        this.updateChartData(data);
      });
  }

  private updateChartData(favoriteDays: FavoriteDaysResponse[]): void {
    const dayMap = new Map<number, FavoriteDaysResponse>();
    favoriteDays.forEach(item => {
      dayMap.set(item.dayOfWeek - 1, item);
    });

    const labels = this.allDays;
    const sessionCounts = this.allDays.map((_, index) => {
      const dayData = dayMap.get(index);
      return dayData?.sessionCount || 0;
    });

    const durations = this.allDays.map((_, index) => {
      const dayData = dayMap.get(index);
      return dayData ? dayData.totalDurationSeconds / 3600 : 0; // Convert to hours
    });

    this.chartDataSubject.next({
      labels,
      datasets: [
        {
          label: 'Sessions',
          data: sessionCounts,
          backgroundColor: 'rgba(139, 92, 246, 0.8)',
          borderColor: 'rgba(139, 92, 246, 1)',
          borderWidth: 1,
          borderRadius: 4,
          barPercentage: 0.8,
          categoryPercentage: 0.6,
          yAxisID: 'y'
        },
        {
          label: 'Duration (hours)',
          data: durations,
          backgroundColor: 'rgba(236, 72, 153, 0.8)',
          borderColor: 'rgba(236, 72, 153, 1)',
          borderWidth: 1,
          borderRadius: 4,
          barPercentage: 0.8,
          categoryPercentage: 0.6,
          yAxisID: 'y1'
        }
      ]
    });
  }
}
