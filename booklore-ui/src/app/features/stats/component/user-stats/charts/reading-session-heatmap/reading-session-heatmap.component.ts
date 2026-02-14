import {Component, inject, Input, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BaseChartDirective} from 'ng2-charts';
import {Tooltip} from 'primeng/tooltip';
import {Chart, ChartConfiguration, ChartData, registerables} from 'chart.js';
import {MatrixController, MatrixElement} from 'chartjs-chart-matrix';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {catchError, takeUntil} from 'rxjs/operators';
import {ReadingSessionHeatmapResponse, UserStatsService} from '../../../../../settings/user-management/user-stats.service';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

const DAY_NAMES = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
const MONTH_NAMES = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

interface MatrixDataPoint {
  x: number;
  y: number;
  v: number;
  date: string;
}

interface Milestone {
  label: string;
  icon: string;
  requirement: number;
  type: 'streak' | 'total';
  unlocked: boolean;
}

type SessionHeatmapChartData = ChartData<'matrix', MatrixDataPoint[], string>;

@Component({
  selector: 'app-reading-session-heatmap',
  standalone: true,
  imports: [CommonModule, BaseChartDirective, Tooltip, TranslocoDirective],
  templateUrl: './reading-session-heatmap.component.html',
  styleUrls: ['./reading-session-heatmap.component.scss']
})
export class ReadingSessionHeatmapComponent implements OnInit, OnDestroy {
  @Input() initialYear: number = new Date().getFullYear();

  public currentYear: number = new Date().getFullYear();
  public readonly chartType = 'matrix' as const;
  public readonly chartData$: Observable<SessionHeatmapChartData>;
  public readonly chartOptions: ChartConfiguration['options'];

  public currentStreak = 0;
  public longestStreak = 0;
  public totalReadingDays = 0;
  public consistencyPercent = 0;
  public milestones: Milestone[] = [];
  public hasStreakData = false;

  private readonly userStatsService = inject(UserStatsService);
  private readonly translocoService = inject(TranslocoService);
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
              return point.v === 1
                ? this.translocoService.translate('statsUser.sessionHeatmap.readingSession', {count: point.v})
                : this.translocoService.translate('statsUser.sessionHeatmap.readingSessions_plural', {count: point.v});
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
    this.loadStreakData();
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
        const year = currentDate.getFullYear();
        const month = String(currentDate.getMonth() + 1).padStart(2, '0');
        const day = String(currentDate.getDate()).padStart(2, '0');
        const dateStr = `${year}-${month}-${day}`;

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

  private loadStreakData(): void {
    this.userStatsService.getReadingDates()
      .pipe(
        takeUntil(this.destroy$),
        catchError((error) => {
          console.error('Error loading reading dates:', error);
          return EMPTY;
        })
      )
      .subscribe((data) => this.processStreakData(data));
  }

  private processStreakData(data: ReadingSessionHeatmapResponse[]): void {
    if (!data || data.length === 0) {
      this.hasStreakData = false;
      return;
    }

    this.hasStreakData = true;
    this.totalReadingDays = data.length;

    const sortedDates = Array.from(new Set(data.map(d => d.date))).sort();
    const dateSet = new Set(sortedDates);

    // Calculate all streaks
    const streakLengths: number[] = [];
    let streakStart: string | null = null;
    let prevDate: string | null = null;
    let lastStreakEnd: string | null = null;
    let lastStreakLength = 0;

    for (const dateStr of sortedDates) {
      if (!prevDate || !this.isConsecutiveDay(prevDate, dateStr)) {
        if (prevDate && streakStart) {
          const len = this.daysBetween(streakStart, prevDate) + 1;
          streakLengths.push(len);
          lastStreakEnd = prevDate;
          lastStreakLength = len;
        }
        streakStart = dateStr;
      }
      prevDate = dateStr;
    }
    if (prevDate && streakStart) {
      const len = this.daysBetween(streakStart, prevDate) + 1;
      streakLengths.push(len);
      lastStreakEnd = prevDate;
      lastStreakLength = len;
    }

    this.longestStreak = streakLengths.length > 0 ? Math.max(...streakLengths) : 0;

    // Current streak
    const today = this.toDateStr(new Date());
    const yesterday = this.toDateStr(new Date(Date.now() - 86400000));

    if ((dateSet.has(today) || dateSet.has(yesterday)) && (lastStreakEnd === today || lastStreakEnd === yesterday)) {
      this.currentStreak = lastStreakLength;
    } else {
      this.currentStreak = 0;
    }

    // Consistency
    if (sortedDates.length >= 2) {
      const totalPossibleDays = this.daysBetween(sortedDates[0], today) + 1;
      this.consistencyPercent = totalPossibleDays > 0
        ? Math.round((this.totalReadingDays / totalPossibleDays) * 100)
        : 0;
    }

    // Milestones
    this.milestones = [
      {label: this.translocoService.translate('statsUser.sessionHeatmap.milestone7DayStreak'), icon: '\uD83D\uDD25', requirement: 7, type: 'streak', unlocked: this.longestStreak >= 7},
      {label: this.translocoService.translate('statsUser.sessionHeatmap.milestone30DayStreak'), icon: '\u26A1', requirement: 30, type: 'streak', unlocked: this.longestStreak >= 30},
      {label: this.translocoService.translate('statsUser.sessionHeatmap.milestone100ReadingDays'), icon: '\uD83D\uDCDA', requirement: 100, type: 'total', unlocked: this.totalReadingDays >= 100},
      {label: this.translocoService.translate('statsUser.sessionHeatmap.milestone365ReadingDays'), icon: '\uD83C\uDFC6', requirement: 365, type: 'total', unlocked: this.totalReadingDays >= 365},
      {label: this.translocoService.translate('statsUser.sessionHeatmap.milestoneYearOfReading'), icon: '\uD83D\uDC51', requirement: 365, type: 'streak', unlocked: this.longestStreak >= 365},
    ];
  }

  private toDateStr(d: Date): string {
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }

  private isConsecutiveDay(dateStr1: string, dateStr2: string): boolean {
    const d1 = new Date(dateStr1);
    const d2 = new Date(dateStr2);
    return Math.abs(d2.getTime() - d1.getTime() - 86400000) < 3600000;
  }

  private daysBetween(dateStr1: string, dateStr2: string): number {
    return Math.round((new Date(dateStr2).getTime() - new Date(dateStr1).getTime()) / 86400000);
  }

  private getDateFromWeek(year: number, week: number): Date {
    const date = new Date(year, 0, 1);
    date.setDate(date.getDate() + (week * 7) - date.getDay());
    return date;
  }
}
