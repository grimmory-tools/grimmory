import {Component, inject, Input, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BaseChartDirective} from 'ng2-charts';
import {ChartConfiguration, ChartData} from 'chart.js';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {catchError, takeUntil} from 'rxjs/operators';
import {GenreStatsResponse, UserStatsService} from '../../../../../settings/user-management/user-stats.service';

type GenreChartData = ChartData<'bar', number[], string>;

@Component({
  selector: 'app-genre-stats-chart',
  standalone: true,
  imports: [CommonModule, BaseChartDirective],
  templateUrl: './genre-stats-chart.component.html',
  styleUrls: ['./genre-stats-chart.component.scss']
})
export class GenreStatsChartComponent implements OnInit, OnDestroy {
  @Input() maxGenres: number = 35;

  public readonly chartType = 'bar' as const;
  public readonly chartData$: Observable<GenreChartData>;
  public readonly chartOptions: ChartConfiguration['options'];

  private readonly userStatsService = inject(UserStatsService);
  private readonly destroy$ = new Subject<void>();
  private readonly chartDataSubject: BehaviorSubject<GenreChartData>;

  constructor() {
    this.chartDataSubject = new BehaviorSubject<GenreChartData>({
      labels: [],
      datasets: []
    });
    this.chartData$ = this.chartDataSubject.asObservable();

    this.chartOptions = {
      responsive: true,
      maintainAspectRatio: false,
      layout: {
        padding: {top: 10}
      },
      plugins: {
        legend: {display: false},
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
              const dataIndex = context.dataIndex;
              const dataset = context.dataset;
              const label = context.chart.data.labels?.[dataIndex] as string;
              const minutes = Math.floor((dataset.data[dataIndex] as number) / 60);
              const hours = Math.floor(minutes / 60);
              const mins = minutes % 60;

              const timeStr = hours > 0
                ? `${hours}h ${mins}m`
                : `${mins}m`;

              return `${label}: ${timeStr}`;
            }
          }
        },
        datalabels: {display: false}
      },
      scales: {
        x: {
          title: {
            display: true,
            text: 'Genres',
            color: '#ffffff',
            font: {
              family: "'Inter', sans-serif",
              size: 12
            }
          },
          ticks: {
            color: '#ffffff',
            font: {family: "'Inter', sans-serif", size: 11},
            maxRotation: 90,
            minRotation: 90,
            callback: (value, index) => {
              const label = this.chartDataSubject.value.labels?.[index] as string;
              const maxLength = 12;
              if (label && label.length > maxLength) {
                return label.substring(0, maxLength) + '...';
              }
              return label;
            }
          },
          grid: {display: false},
          border: {display: false}
        },
        y: {
          title: {
            display: true,
            text: 'Time Read',
            color: '#ffffff',
            font: {
              family: "'Inter', sans-serif",
              size: 12
            }
          },
          beginAtZero: true,
          ticks: {
            color: '#ffffff',
            font: {family: "'Inter', sans-serif", size: 11},
            callback: (value) => {
              const seconds = value as number;
              const minutes = Math.floor(seconds / 60);
              const hours = Math.floor(minutes / 60);
              const days = Math.floor(hours / 24);

              if (days > 0) {
                const remainingHours = hours % 24;
                if (remainingHours > 0) {
                  return `${days} ${days === 1 ? 'day' : 'days'} ${remainingHours} ${remainingHours === 1 ? 'hr' : 'hrs'}`;
                }
                return `${days} ${days === 1 ? 'day' : 'days'}`;
              } else if (hours > 0) {
                const remainingMinutes = minutes % 60;
                if (remainingMinutes > 0) {
                  return `${hours} ${hours === 1 ? 'hr' : 'hrs'} ${remainingMinutes} min`;
                }
                return `${hours} ${hours === 1 ? 'hr' : 'hrs'}`;
              } else if (minutes > 0) {
                return `${minutes} min`;
              }
              return `${seconds} sec`;
            },
            stepSize: undefined,
            maxTicksLimit: 8
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
    this.loadGenreStats();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private loadGenreStats(): void {
    this.userStatsService.getGenreStats()
      .pipe(
        takeUntil(this.destroy$),
        catchError((error) => {
          console.error('Error loading genre stats:', error);
          return EMPTY;
        })
      )
      .subscribe((data) => {
        this.updateChartData(data);
      });
  }

  private updateChartData(genreStats: GenreStatsResponse[]): void {
    const sortedStats = [...genreStats]
      .sort((a, b) => b.totalDurationSeconds - a.totalDurationSeconds)
      .slice(0, this.maxGenres);

    const labels = sortedStats.map(stat => stat.genre);
    const durations = sortedStats.map(stat => stat.totalDurationSeconds);

    this.chartDataSubject.next({
      labels,
      datasets: [
        {
          label: 'Reading Time',
          data: durations,
          backgroundColor: 'rgba(34, 197, 94, 0.8)',
          borderColor: 'rgba(34, 197, 94, 1)',
          borderWidth: 1,
          borderRadius: 4,
          barPercentage: 0.8,
          categoryPercentage: 0.6
        }
      ]
    });
  }
}
