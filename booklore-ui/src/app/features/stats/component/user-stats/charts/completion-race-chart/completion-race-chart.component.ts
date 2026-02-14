import {Component, inject, Input, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BaseChartDirective} from 'ng2-charts';
import {Tooltip} from 'primeng/tooltip';
import {ChartConfiguration, ChartData} from 'chart.js';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {catchError, takeUntil} from 'rxjs/operators';
import {CompletionRaceResponse, UserStatsService} from '../../../../../settings/user-management/user-stats.service';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

interface BookRace {
  bookId: number;
  bookTitle: string;
  sessions: { dayNumber: number; progress: number; date: string }[];
  totalDays: number;
}

type RaceChartData = ChartData<'line', { x: number; y: number }[], number>;

const LINE_COLORS = [
  '#4caf50', '#2196f3', '#ff9800', '#e91e63', '#9c27b0',
  '#00bcd4', '#ff5722', '#8bc34a', '#3f51b5', '#ffc107',
  '#795548', '#607d8b', '#f44336', '#009688', '#cddc39'
];

@Component({
  selector: 'app-completion-race-chart',
  standalone: true,
  imports: [CommonModule, BaseChartDirective, Tooltip, TranslocoDirective],
  templateUrl: './completion-race-chart.component.html',
  styleUrls: ['./completion-race-chart.component.scss']
})
export class CompletionRaceChartComponent implements OnInit, OnDestroy {
  @Input() initialYear: number = new Date().getFullYear();

  public currentYear: number = new Date().getFullYear();
  public readonly chartType = 'line' as const;
  public readonly chartData$: Observable<RaceChartData>;
  public chartOptions: ChartConfiguration<'line'>['options'];

  public totalBooks = 0;
  public avgDaysToFinish = 0;
  public medianDaysToFinish = 0;
  public totalSessions = 0;
  public fastestDays = '';
  public fastestTitle = '';
  public slowestDays = '';
  public slowestTitle = '';

  private readonly userStatsService = inject(UserStatsService);
  private readonly t = inject(TranslocoService);
  private readonly destroy$ = new Subject<void>();
  private readonly chartDataSubject: BehaviorSubject<RaceChartData>;

  constructor() {
    this.chartDataSubject = new BehaviorSubject<RaceChartData>({labels: [], datasets: []});
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
            padding: 8,
            usePointStyle: true,
            pointStyle: 'line'
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
          padding: 12,
          titleFont: {size: 13, weight: 'bold'},
          bodyFont: {size: 11},
          callbacks: {
            title: (context) => context[0].dataset.label || '',
            label: (context) => {
              const progress = (context.parsed.y ?? 0).toFixed(1);
              const day = context.parsed.x;
              return this.t.translate('statsUser.completionRace.tooltipDayProgress', {day, progress});
            }
          }
        },
        datalabels: {display: false}
      },
      scales: {
        x: {
          type: 'linear',
          title: {
            display: true,
            text: this.t.translate('statsUser.completionRace.axisDaysSinceFirstSession'),
            color: '#ffffff',
            font: {family: "'Inter', sans-serif", size: 12, weight: 'bold'}
          },
          ticks: {
            color: '#ffffff',
            font: {family: "'Inter', sans-serif", size: 11},
            stepSize: 1
          },
          grid: {color: 'rgba(255, 255, 255, 0.1)'},
          border: {display: false}
        },
        y: {
          min: 0,
          max: 100,
          title: {
            display: true,
            text: this.t.translate('statsUser.completionRace.axisProgress'),
            color: '#ffffff',
            font: {family: "'Inter', sans-serif", size: 12, weight: 'bold'}
          },
          ticks: {
            color: '#ffffff',
            font: {family: "'Inter', sans-serif", size: 11},
            callback: (value) => `${value}%`
          },
          grid: {color: 'rgba(255, 255, 255, 0.1)'},
          border: {display: false}
        }
      },
      interaction: {
        mode: 'nearest',
        intersect: false
      }
    };
  }

  ngOnInit(): void {
    this.currentYear = this.initialYear;
    this.loadData(this.currentYear);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  public changeYear(delta: number): void {
    this.currentYear += delta;
    this.loadData(this.currentYear);
  }

  private loadData(year: number): void {
    this.userStatsService.getCompletionRace(year)
      .pipe(
        takeUntil(this.destroy$),
        catchError((error) => {
          console.error('Error loading completion race:', error);
          return EMPTY;
        })
      )
      .subscribe((data) => this.processData(data));
  }

  private processData(data: CompletionRaceResponse[]): void {
    const bookMap = new Map<number, { title: string; sessions: { date: Date; progress: number }[] }>();

    for (const item of data) {
      if (!bookMap.has(item.bookId)) {
        bookMap.set(item.bookId, {title: item.bookTitle, sessions: []});
      }
      bookMap.get(item.bookId)!.sessions.push({
        date: new Date(item.sessionDate),
        progress: Math.min(item.endProgress * 100, 100)
      });
    }

    const races: BookRace[] = [];
    bookMap.forEach((value, bookId) => {
      if (value.sessions.length === 0) return;
      const firstDate = value.sessions[0].date;
      const sessionPoints = value.sessions.map(s => ({
        dayNumber: Math.floor((s.date.getTime() - firstDate.getTime()) / (1000 * 60 * 60 * 24)),
        progress: s.progress,
        date: s.date.toLocaleDateString()
      }));
      const totalDays = sessionPoints.length > 0 ? sessionPoints[sessionPoints.length - 1].dayNumber : 0;
      races.push({bookId, bookTitle: value.title, sessions: sessionPoints, totalDays});
    });

    this.totalBooks = races.length;

    if (races.length > 0) {
      const days = races.map(r => r.totalDays).sort((a, b) => a - b);
      const fastest = races.reduce((a, b) => a.totalDays <= b.totalDays ? a : b);
      const slowest = races.reduce((a, b) => a.totalDays >= b.totalDays ? a : b);
      this.avgDaysToFinish = Math.round(days.reduce((a, b) => a + b, 0) / days.length);
      this.medianDaysToFinish = days.length % 2 === 0
        ? Math.round((days[days.length / 2 - 1] + days[days.length / 2]) / 2)
        : days[Math.floor(days.length / 2)];
      this.totalSessions = races.reduce((sum, r) => sum + r.sessions.length, 0);
      this.fastestDays = `${fastest.totalDays}d`;
      this.fastestTitle = fastest.bookTitle.length > 25 ? fastest.bookTitle.substring(0, 25) + '...' : fastest.bookTitle;
      this.slowestDays = `${slowest.totalDays}d`;
      this.slowestTitle = slowest.bookTitle.length > 25 ? slowest.bookTitle.substring(0, 25) + '...' : slowest.bookTitle;
    } else {
      this.avgDaysToFinish = 0;
      this.medianDaysToFinish = 0;
      this.totalSessions = 0;
      this.fastestDays = '-';
      this.fastestTitle = '';
      this.slowestDays = '-';
      this.slowestTitle = '';
    }

    const datasets = races.map((race, i) => {
      const color = LINE_COLORS[i % LINE_COLORS.length];
      return {
        label: race.bookTitle.length > 30 ? race.bookTitle.substring(0, 30) + '...' : race.bookTitle,
        data: race.sessions.map(s => ({x: s.dayNumber, y: s.progress})),
        borderColor: color,
        backgroundColor: color,
        fill: false,
        tension: 0.3,
        stepped: 'before' as const,
        pointRadius: 3,
        pointHoverRadius: 5,
        borderWidth: 2
      };
    });

    this.chartDataSubject.next({datasets});
  }
}
