import {Component, computed, input} from '@angular/core';
import {barY, defineChart} from '@tanstack/charts';
import {scaleLinear} from '@tanstack/charts/scales/linear';

import {type StatsSlots} from '../../../../data/stats-response.models';
import {countTicks, STATS_CHART_PALETTE, statsAxis, statsBar, statsBarBand, statsTooltip} from '../../../shared/stats-chart-style';
import {StatsChartPlotComponent} from '../../../shared/stats-chart-plot.component';
import {injectStatsText, STATS_CHART_HOST_CLASS} from '../../../shared/stats-chart-text';

export interface FavoriteDaysData extends StatsSlots {
  readonly year: number | null;
  readonly month: number | null;
}

const WEEKDAYS = [1, 2, 3, 4, 5, 6, 7];

@Component({
  selector: 'app-favorite-days-chart',
  imports: [StatsChartPlotComponent],
  template: `<app-stats-chart-plot [options]="chart()" />`,
  host: {class: STATS_CHART_HOST_CLASS},
})
export class FavoriteDaysChartComponent {
  private readonly text = injectStatsText('statsUser.favoriteDays');

  readonly data = input.required<FavoriteDaysData>();

  private readonly days = computed(() => {
    const {rows, days} = this.data();
    return WEEKDAYS.map((weekday) => {
      const seconds = rows.filter((slot) => slot.weekday === weekday).reduce((sum, slot) => sum + slot.seconds, 0);
      const count = days[weekday - 1] ?? 0;
      return {weekday, days: count, minutes: count > 0 ? seconds / 60 / count : 0};
    });
  });

  readonly chart = computed(() => {
    const {t, number, weekday, date, duration} = this.text;
    const {year, month} = this.data();
    const days = this.days().map((day) => ({...day, name: weekday(day.weekday)}));
    const period = year === null ? t('allTime') : month === null ? `${year}` : date(`${year}-${String(month).padStart(2, '0')}`, {month: 'long', year: 'numeric'});
    const most = Math.max(1, ...days.map(({minutes}) => minutes));

    return {
      ariaLabel: t('chartLabel', {period}),
      definition: defineChart({
        chart: ({width}) => ({
          marks: [barY(days, {x: 'name', y: 'minutes', fill: STATS_CHART_PALETTE[0], ...statsBar(width)})],
          scales: {
            x: {scale: statsBarBand(days.map(({name}) => name)), axis: statsAxis(t('axisDayOfWeek'))},
            y: {
              scale: scaleLinear().domain([0, most]),
              nice: true,
              grid: true,
              axis: statsAxis(t('averagePerDay'), countTicks(Math.ceil(most), duration)),
            },
          },
        }),
        focus: 'nearest-x',
        focusRing: false,
        svgAnimation: true,
        tooltip: statsTooltip<(typeof days)[number]>(([{datum: day}]) => ({
          title: day.name,
          rows: [
            {label: t('averagePerDay'), value: duration(day.minutes)},
            {label: t('daysCounted'), value: number(day.days)},
          ],
        })),
      }),
    };
  });
}
