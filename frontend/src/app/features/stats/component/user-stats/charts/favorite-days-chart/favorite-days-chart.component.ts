import {Component, computed, input} from '@angular/core';
import {barY, defaultChartTheme, defineChart} from '@tanstack/charts';
import {scaleLinear} from '@tanstack/charts/scales/linear';

import {type StatsSlots} from '../../../../data/stats-response.models';
import {countTicks, statsAxis, statsBar, statsBarBand, statsTooltip} from '../../../shared/stats-chart-style';
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

  private readonly period = computed(() => {
    const {t, date} = this.text;
    const {year, month} = this.data();
    if (year === null) return t('allTime');
    if (month === null) return `${year}`;
    return date(`${year}-${String(month).padStart(2, '0')}`, {month: 'long', year: 'numeric'});
  });

  readonly chart = computed(() => {
    const {t, number, weekday, duration} = this.text;
    const {rows, days: counts} = this.data();
    const days = WEEKDAYS.map((day) => {
      const seconds = rows.filter((slot) => slot.weekday === day).reduce((sum, slot) => sum + slot.seconds, 0);
      const count = counts[day - 1];
      return {name: weekday(day), days: count, minutes: count > 0 ? seconds / 60 / count : 0};
    });
    const most = Math.max(1, ...days.map(({minutes}) => minutes));

    return {
      ariaLabel: t('chartLabel', {period: this.period()}),
      definition: defineChart({
        chart: ({width}) => ({
          marks: [barY(days, {x: 'name', y: 'minutes', fill: defaultChartTheme.palette[0], ...statsBar(width)})],
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
