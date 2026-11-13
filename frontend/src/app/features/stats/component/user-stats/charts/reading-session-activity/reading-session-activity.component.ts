import {Component, computed, input} from '@angular/core';

import {type StatsDays} from '../../../../data/stats-response.models';
import {injectStatsText, STATS_CHART_HOST_CLASS} from '../../../shared/stats-chart-text';
import {type StatsHeatmap, type StatsHeatmapColumnLabel, StatsHeatmapComponent} from '../../../shared/stats-heatmap.component';
import {type StatsInsight, StatsInsightListComponent} from '../../../shared/stats-insight-list.component';

const DAY_MS = 86_400_000;

export interface SessionActivityData extends StatsDays {
  readonly year: number;
}

@Component({
  selector: 'app-reading-session-activity',
  imports: [StatsHeatmapComponent, StatsInsightListComponent],
  template: `
    <app-stats-heatmap
      [heatmap]="chart()"
      [columns]="weekCount()"
      [columnLabels]="monthLabels()"
      [rowLabels]="weekdayLabels()"
      square />

    <app-stats-insight-list [items]="insights()" />
  `,
  host: {class: STATS_CHART_HOST_CLASS},
})
export class ReadingSessionActivityComponent {
  private readonly text = injectStatsText('statsUser.sessionActivity');

  readonly data = input.required<SessionActivityData>();

  readonly cells = computed(() => {
    const {year, rows} = this.data();
    const sessions = new Map(rows.map(({day, sessions}) => [day, sessions]));
    const first = Date.UTC(year, 0, 1);
    const offset = (new Date(first).getUTCDay() + 6) % 7;

    return Array.from({length: (Date.UTC(year + 1, 0, 1) - first) / DAY_MS}, (_, index) => {
      const date = new Date(first + index * DAY_MS).toISOString().slice(0, 10);
      return {week: Math.floor((index + offset) / 7), weekday: (index + offset) % 7, date, count: sessions.get(date) ?? 0};
    });
  });

  readonly weekCount = computed(() => this.cells()[this.cells().length - 1].week + 1);

  readonly monthLabels = computed<readonly StatsHeatmapColumnLabel[]>(() =>
    this.cells()
      .filter(({date}) => date.endsWith('-01'))
      .map(({week, date}) => ({column: week, label: this.text.date(date, {month: 'short'})})),
  );

  readonly weekdayLabels = computed(() =>
    Array.from({length: 7}, (_, weekday) =>
      weekday % 2 === 0 && weekday < 6 ? this.text.weekday(weekday + 1) : '',
    ),
  );

  readonly chart = computed<StatsHeatmap>(() => {
    const {t, date} = this.text;

    return {
      label: t('title'),
      valueName: t('readingSessions'),
      cells: this.cells().map((cell) => ({
        column: cell.week,
        row: cell.weekday,
        value: cell.count,
        label: date(cell.date, {dateStyle: 'medium'}),
      })),
    };
  });

  readonly insights = computed<readonly StatsInsight[]>(() => {
    const {t, number} = this.text;
    const streaks = this.data().streaks;
    const days = (count: number) => t(count === 1 ? 'dayCount' : 'daysCount', {count: number(count)});

    return [
      {label: t('currentStreak'), value: days(streaks.current)},
      {label: t('longestStreak'), value: days(streaks.longest)},
      {label: t('totalDays'), value: days(streaks.readingDays)},
    ];
  });
}
