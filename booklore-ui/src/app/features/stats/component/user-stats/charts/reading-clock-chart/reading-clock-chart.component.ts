import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BaseChartDirective} from 'ng2-charts';
import {Tooltip} from 'primeng/tooltip';
import {BehaviorSubject, EMPTY, Observable, Subject} from 'rxjs';
import {catchError, takeUntil} from 'rxjs/operators';
import {ChartConfiguration, ChartData} from 'chart.js';
import {PeakHoursResponse, UserStatsService} from '../../../../../settings/user-management/user-stats.service';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

type ClockChartData = ChartData<'polarArea', number[], string>;

@Component({
  selector: 'app-reading-clock-chart',
  standalone: true,
  imports: [CommonModule, BaseChartDirective, Tooltip, TranslocoDirective],
  templateUrl: './reading-clock-chart.component.html',
  styleUrls: ['./reading-clock-chart.component.scss']
})
export class ReadingClockChartComponent implements OnInit, OnDestroy {
  private readonly userStatsService = inject(UserStatsService);
  private readonly t = inject(TranslocoService);
  private readonly destroy$ = new Subject<void>();

  public readonly chartType = 'polarArea' as const;
  public peakHour = '';
  public totalHoursRead = 0;
  public readerType = '';
  public hasData = false;

  private lastData: PeakHoursResponse[] = [];

  public readonly chartOptions: ChartConfiguration<'polarArea'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    layout: {
      padding: {top: 10, bottom: 10}
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
        padding: 12,
        titleFont: {size: 13, weight: 'bold'},
        bodyFont: {size: 12},
        callbacks: {
          title: (context) => this.buildHourLabels()[context[0].dataIndex],
          label: (context) => {
            const minutes = context.parsed.r;
            if (minutes >= 60) {
              const hrs = Math.floor(minutes / 60);
              const mins = Math.round(minutes % 60);
              return this.t.translate('statsUser.readingClock.tooltipReading', {time: `${hrs}h ${mins}m`});
            }
            return this.t.translate('statsUser.readingClock.tooltipReading', {time: `${Math.round(minutes)}m`});
          }
        }
      },
      datalabels: {display: false}
    },
    scales: {
      r: {
        ticks: {display: false},
        grid: {color: 'rgba(255, 255, 255, 0.1)'},
        pointLabels: {
          display: true,
          color: 'rgba(255, 255, 255, 0.7)',
          font: {family: "'Inter', sans-serif", size: 10}
        }
      }
    }
  };

  private readonly chartDataSubject = new BehaviorSubject<ClockChartData>({
    labels: [],
    datasets: []
  });

  public readonly chartData$: Observable<ClockChartData> = this.chartDataSubject.asObservable();

  ngOnInit(): void {
    this.userStatsService.getPeakHours()
      .pipe(
        takeUntil(this.destroy$),
        catchError((error) => {
          console.error('Error loading peak hours:', error);
          return EMPTY;
        })
      )
      .subscribe((data) => {
        this.lastData = data;
        this.processData(data);
      });

    this.t.langChanges$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        if (this.lastData.length > 0) {
          this.processData(this.lastData);
        }
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private buildHourLabels(): string[] {
    const formatter = new Intl.DateTimeFormat(this.t.getActiveLang(), {hour: 'numeric', hour12: true});
    return [...Array(24)].map((_, i) => formatter.format(new Date(2024, 0, 1, i)));
  }

  private processData(data: PeakHoursResponse[]): void {
    if (!data || data.length === 0) {
      this.hasData = false;
      return;
    }

    this.hasData = true;

    const hourLabels = this.buildHourLabels();

    const hourMinutes = new Array(24).fill(0);
    let totalSeconds = 0;
    let peakIdx = 0;
    let peakVal = 0;

    for (const entry of data) {
      const minutes = entry.totalDurationSeconds / 60;
      hourMinutes[entry.hourOfDay] = minutes;
      totalSeconds += entry.totalDurationSeconds;
      if (minutes > peakVal) {
        peakVal = minutes;
        peakIdx = entry.hourOfDay;
      }
    }

    this.peakHour = hourLabels[peakIdx];
    this.totalHoursRead = Math.round(totalSeconds / 3600);

    // Night owl vs early bird
    const nightHours = [20, 21, 22, 23, 0, 1, 2];
    const morningHours = [5, 6, 7, 8, 9, 10, 11];
    const nightTotal = nightHours.reduce((sum, h) => sum + hourMinutes[h], 0);
    const morningTotal = morningHours.reduce((sum, h) => sum + hourMinutes[h], 0);

    if (nightTotal > morningTotal * 1.2) {
      this.readerType = this.t.translate('statsUser.readingClock.nightOwl');
    } else if (morningTotal > nightTotal * 1.2) {
      this.readerType = this.t.translate('statsUser.readingClock.earlyBird');
    } else {
      this.readerType = this.t.translate('statsUser.readingClock.balanced');
    }

    // Generate colors: cool blues for low, warm oranges for peak
    const maxMinutes = Math.max(...hourMinutes, 1);
    const colors = hourMinutes.map(minutes => {
      const ratio = minutes / maxMinutes;
      if (ratio >= 0.7) return 'rgba(255, 152, 0, 0.8)';   // Warm orange
      if (ratio >= 0.4) return 'rgba(255, 193, 7, 0.7)';    // Yellow
      if (ratio >= 0.15) return 'rgba(100, 181, 246, 0.6)';  // Light blue
      return 'rgba(66, 133, 244, 0.35)';                      // Cool blue
    });

    this.chartDataSubject.next({
      labels: hourLabels,
      datasets: [{
        data: hourMinutes,
        backgroundColor: colors,
        borderColor: colors.map(c => c.replace(/[\d.]+\)$/, '1)')),
        borderWidth: 1
      }]
    });
  }
}
