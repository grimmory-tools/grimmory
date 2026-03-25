import {Component, inject, Input, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {BaseChartDirective} from 'ng2-charts';
import {Tooltip} from 'primeng/tooltip';
import {EMPTY, Subject} from 'rxjs';
import {catchError, takeUntil} from 'rxjs/operators';
import {ChartConfiguration, ChartData} from 'chart.js';
import {UserStatsService, SessionScatterResponse} from '../../../../../settings/user-management/user-stats.service';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

const DAY_COLORS = [
  '#ef5350', '#ff9800', '#ffc107', '#66bb6a', '#42a5f5', '#7e57c2', '#ec407a'
];
const DAY_NAMES = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

@Component({
  selector: 'app-session-archetypes-chart',
  standalone: true,
  imports: [CommonModule, BaseChartDirective, Tooltip, TranslocoDirective],
  templateUrl: './session-archetypes-chart.component.html',
  styleUrls: ['./session-archetypes-chart.component.scss']
})
export class SessionArchetypesChartComponent implements OnInit, OnDestroy {
  @Input() initialYear = new Date().getFullYear();

  private readonly userStatsService = inject(UserStatsService);
  private readonly t = inject(TranslocoService);
  private readonly destroy$ = new Subject<void>();

  public readonly chartType = 'scatter' as const;
  public currentYear: number = new Date().getFullYear();
  public hasData = false;
  public sessionCount = 0;
  public dominantArchetype = '';

  public chartData: ChartData<'scatter', {x: number; y: number}[], string> = {labels: [], datasets: []};

  public readonly chartOptions: ChartConfiguration<'scatter'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    animation: {duration: 400},
    layout: {padding: {top: 10, right: 20}},
    plugins: {
      legend: {
        display: true, position: 'bottom',
        labels: {color: 'rgba(255, 255, 255, 0.8)', font: {family: "'Inter', sans-serif", size: 11}, boxWidth: 10, padding: 12}
      },
      tooltip: {
        enabled: true, backgroundColor: 'rgba(0, 0, 0, 0.9)',
        titleColor: '#ffffff', bodyColor: '#ffffff',
        cornerRadius: 6, padding: 10,
        callbacks: {
          label: (ctx) => {
            const hour = ctx.parsed.x ?? 0;
            const h = Math.floor(hour);
            const m = Math.round((hour - h) * 60);
            const duration = Math.round(ctx.parsed.y ?? 0);
            return `${ctx.dataset.label}: ${h}:${String(m).padStart(2, '0')} - ${duration} min`;
          }
        }
      },
      datalabels: {display: false}
    },
    scales: {
      x: {
        min: 0, max: 24,
        grid: {color: 'rgba(255, 255, 255, 0.08)'},
        ticks: {
          color: 'rgba(255, 255, 255, 0.6)', font: {size: 10}, stepSize: 3,
          callback: (val) => {
            const h = Number(val);
            if (h === 0) return '12am';
            if (h === 12) return '12pm';
            return h < 12 ? `${h}am` : `${h - 12}pm`;
          }
        },
        title: {display: true, text: 'Time of Day', color: 'rgba(255, 255, 255, 0.5)', font: {size: 11}}
      },
      y: {
        min: 0,
        grid: {color: 'rgba(255, 255, 255, 0.08)'},
        ticks: {color: 'rgba(255, 255, 255, 0.6)', font: {size: 11}},
        title: {display: true, text: 'Duration (min)', color: 'rgba(255, 255, 255, 0.5)', font: {size: 11}}
      }
    }
  };

  ngOnInit(): void {
    this.currentYear = this.initialYear;
    this.loadData(this.currentYear);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  changeYear(delta: number): void {
    this.currentYear += delta;
    this.loadData(this.currentYear);
  }

  private loadData(year: number): void {
    this.userStatsService.getSessionScatter(year)
      .pipe(takeUntil(this.destroy$), catchError(() => EMPTY))
      .subscribe(data => this.processData(data));
  }

  private processData(data: SessionScatterResponse[]): void {
    if (!data || data.length === 0) {
      this.hasData = false;
      this.chartData = {labels: [], datasets: []};
      return;
    }

    const durations = data.map(s => s.durationMinutes).sort((a, b) => a - b);
    const q1 = durations[Math.floor(durations.length * 0.25)];
    const q3 = durations[Math.floor(durations.length * 0.75)];
    const iqr = q3 - q1;
    const upperFence = q3 + 1.5 * iqr;
    const filtered = data.filter(s => s.durationMinutes <= upperFence);

    this.sessionCount = filtered.length;

    const byDay = new Map<number, {x: number; y: number}[]>();
    for (let d = 1; d <= 7; d++) byDay.set(d, []);

    const quadrants = {morning: 0, afternoon: 0, evening: 0, night: 0};

    for (const s of filtered) {
      const points = byDay.get(s.dayOfWeek) || [];
      points.push({x: s.hourOfDay, y: s.durationMinutes});
      byDay.set(s.dayOfWeek, points);

      if (s.hourOfDay >= 5 && s.hourOfDay < 12) quadrants.morning++;
      else if (s.hourOfDay >= 12 && s.hourOfDay < 17) quadrants.afternoon++;
      else if (s.hourOfDay >= 17 && s.hourOfDay < 22) quadrants.evening++;
      else quadrants.night++;
    }

    const maxQ = Object.entries(quadrants).sort((a, b) => b[1] - a[1])[0];
    this.dominantArchetype = this.t.translate(`statsUser.sessionArchetypes.archetype_${maxQ[0]}`);

    const datasets = DAY_NAMES.map((name, i) => {
      const dayNum = i + 1;
      return {
        label: name,
        data: byDay.get(dayNum) || [],
        backgroundColor: DAY_COLORS[i] + 'AA',
        borderColor: DAY_COLORS[i],
        borderWidth: 1,
        pointRadius: 5,
        pointHoverRadius: 8
      };
    }).filter(ds => ds.data.length > 0);

    this.chartData = {datasets};
    this.hasData = true;
  }
}
