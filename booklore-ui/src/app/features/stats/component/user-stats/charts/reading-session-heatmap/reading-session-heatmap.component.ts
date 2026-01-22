import {Component, inject, Input, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BaseChartDirective} from 'ng2-charts';
import {Chart, ChartConfiguration, ChartData, registerables} from 'chart.js';
import {MatrixController, MatrixElement} from 'chartjs-chart-matrix';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {catchError, takeUntil} from 'rxjs/operators';
import {ReadingSessionHeatmapResponse, UserStatsService} from '../../../../../settings/user-management/user-stats.service';

const DAY_NAMES = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
const MONTH_NAMES = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

interface MatrixDataPoint {
  x: number;
  y: number;
  v: number;
  date: string;
}

type SessionHeatmapChartData = ChartData<'matrix', MatrixDataPoint[], string>;

@Component({
  selector: 'app-reading-session-heatmap',
  standalone: true,
  imports: [CommonModule, BaseChartDirective],
  templateUrl: './reading-session-heatmap.component.html',
  styleUrls: ['./reading-session-heatmap.component.scss']
})
export class ReadingSessionHeatmapComponent implements OnInit, OnDestroy {
  @Input() initialYear: number = new Date().getFullYear();

  public currentYear: number = new Date().getFullYear();
  public readonly chartType = 'matrix' as const;
  public readonly chartData$: Observable<SessionHeatmapChartData>;
  public readonly chartOptions: ChartConfiguration['options'];

  private readonly userStatsService = inject(UserStatsService);
  private readonly destroy$ = new Subject<void>();
  private readonly chartDataSubject: BehaviorSubject<SessionHeatmapChartData>;
  private maxSessionCount = 1;

  constructor() {
    this.chartDataSubject = new BehaviorSubject<SessionHeatmapChartData>({
      labels: [],
      datasets: [{label: 'Reading Sessions', data: []}]
    });
    this.chartData$ = this.chartDataSubject.asObservable();

    this.chartOptions = {
      responsive: true,
      maintainAspectRatio: false,
      layout: {
        padding: {top: 20, bottom: 20, left: 10, right: 10}
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
          displayColors: false,
          padding: 12,
          titleFont: {size: 14, weight: 'bold'},
          bodyFont: {size: 13},
          callbacks: {
            title: (context) => {
              const point = context[0].raw as MatrixDataPoint;
              const date = new Date(point.date);
              return date.toLocaleDateString('en-US', {
                weekday: 'short',
                year: 'numeric',
                month: 'short',
                day: 'numeric'
              });
            },
            label: (context) => {
              const point = context.raw as MatrixDataPoint;
              return `${point.v} reading session${point.v === 1 ? '' : 's'}`;
            }
          }
        },
        datalabels: {display: false}
      },
      scales: {
        x: {
          type: 'linear',
          position: 'top',
          min: 0,
          max: 52,
          ticks: {
            stepSize: 4,
            callback: (value) => {
              const weekNum = value as number;
              if (weekNum % 4 === 0) {
                const date = this.getDateFromWeek(this.currentYear, weekNum);
                return MONTH_NAMES[date.getMonth()];
              }
              return '';
            },
            color: '#ffffff',
            font: {family: "'Inter', sans-serif", size: 11}
          },
          grid: {display: false},
          border: {display: false}
        },
        y: {
          type: 'linear',
          min: 0,
          max: 6,
          ticks: {
            stepSize: 1,
            callback: (value) => {
              const dayIndex = value as number;
              return dayIndex >= 0 && dayIndex <= 6 ? DAY_NAMES[dayIndex] : '';
            },
            color: '#ffffff',
            font: {family: "'Inter', sans-serif", size: 11}
          },
          border: {display: false}
        }
      }
    };
  }

  ngOnInit(): void {
    Chart.register(...registerables, MatrixController, MatrixElement);
    this.currentYear = this.initialYear;
    this.loadYearData(this.currentYear);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  public changeYear(delta: number): void {
    this.currentYear += delta;
    this.loadYearData(this.currentYear);
  }

  private loadYearData(year: number): void {
    this.userStatsService.getHeatmapForYear(year)
      .pipe(
        takeUntil(this.destroy$),
        catchError((error) => {
          console.error('Error loading reading session heatmap:', error);
          return EMPTY;
        })
      )
      .subscribe((data) => {
        this.updateChartData(data);
      });
  }

  private updateChartData(sessionData: ReadingSessionHeatmapResponse[]): void {
    const sessionMap = new Map<string, number>();
    sessionData.forEach(item => {
      sessionMap.set(item.date, item.count);
    });

    this.maxSessionCount = Math.max(1, ...sessionData.map(d => d.count));

    const heatmapData: MatrixDataPoint[] = [];
    const startDate = new Date(this.currentYear, 0, 1);
    const endDate = new Date(this.currentYear, 11, 31);

    const firstMonday = new Date(startDate);
    const dayOfWeek = firstMonday.getDay();
    const daysToMonday = dayOfWeek === 0 ? 6 : dayOfWeek - 1;
    firstMonday.setDate(firstMonday.getDate() - daysToMonday);

    let weekIndex = 0;
    const currentDate = new Date(firstMonday);

    while (currentDate <= endDate || weekIndex === 0) {
      for (let dayOfWeek = 0; dayOfWeek < 7; dayOfWeek++) {
        const dateStr = currentDate.toISOString().split('T')[0];

        if (currentDate >= startDate && currentDate <= endDate) {
          const count = sessionMap.get(dateStr) || 0;

          heatmapData.push({
            x: weekIndex,
            y: dayOfWeek,
            v: count,
            date: dateStr
          });
        }

        currentDate.setDate(currentDate.getDate() + 1);
      }

      weekIndex++;

      if (currentDate > endDate) {
        break;
      }
    }

    this.chartDataSubject.next({
      labels: [],
      datasets: [{
        label: 'Reading Sessions',
        data: heatmapData,
        backgroundColor: (context) => {
          const point = context.raw as MatrixDataPoint;
          if (!point?.v) return 'rgba(255, 255, 255, 0.05)';

          const intensity = point.v / this.maxSessionCount;
          const alpha = Math.max(0.3, Math.min(0.9, intensity * 0.6 + 0.3));
          return `rgba(59, 130, 246, ${alpha})`;
        },
        borderColor: 'rgba(255, 255, 255, 0.1)',
        borderWidth: 1
      }]
    });
  }

  private getDateFromWeek(year: number, week: number): Date {
    const date = new Date(year, 0, 1);
    date.setDate(date.getDate() + (week * 7) - date.getDay());
    return date;
  }
}
