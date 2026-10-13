import {Component, computed, input} from '@angular/core';
import {defineChart} from '@tanstack/charts';
import {angleGrid, polar, radialBarRadius, radialGrid} from '@tanstack/charts/polar';
import {scaleBand} from '@tanstack/charts/scales/band';
import {scaleLinear} from '@tanstack/charts/scales/linear';
import {groupBy} from '@tanstack/charts/transform/group';

import {type StatsSlot} from '../../../../data/stats-response.models';
import {STATS_BAR_FOCUS, STATS_CHART_PALETTE, STATS_POLAR_GRID, statsTooltip} from '../../../shared/stats-chart-style';
import {firstMaximum} from '../../../shared/stats-chart-data';
import {StatsChartPlotComponent} from '../../../shared/stats-chart-plot.component';
import {injectStatsText, STATS_CHART_HOST_CLASS} from '../../../shared/stats-chart-text';
import {type StatsInsight, StatsInsightListComponent} from '../../../shared/stats-insight-list.component';

export interface ReadingClockData {
  readonly year: number | null;
  readonly weekday: number | null;
  readonly rows: readonly StatsSlot[];
}

@Component({
  selector: 'app-reading-clock-chart',
  imports: [StatsChartPlotComponent, StatsInsightListComponent],
  template: `
    <app-stats-chart-plot [options]="chart()" />
    <app-stats-insight-list [items]="insights()" />
  `,
  host: {class: STATS_CHART_HOST_CLASS},
})
export class ReadingClockChartComponent {
  private readonly text = injectStatsText('statsUser.readingClock');

  readonly data = input.required<ReadingClockData>();

  private readonly shown = computed(() => {
    const {rows, weekday} = this.data();
    return weekday === null ? rows : rows.filter((slot) => slot.weekday === weekday);
  });

  private readonly minutes = computed(() =>
    groupBy(this.shown(), {by: 'hour', outputs: {seconds: {value: 'seconds', reduce: 'sum'}}})
      .map(({hour, seconds}) => ({hour, minutes: seconds / 60})),
  );

  private readonly longest = computed(() => {
    const {rows, weekday} = this.data();
    if (weekday !== null) return Math.max(1, ...rows.map(({seconds}) => seconds / 60));
    return Math.max(1, ...this.minutes().map(({minutes}) => minutes));
  });

  private readonly hourLabels = computed(() => Array.from({length: 24}, (_, hour) => this.text.hour(hour)));

  readonly chart = computed(() => {
    const {t} = this.text;
    const {year, weekday} = this.data();
    const labels = this.hourLabels();
    const hours = this.minutes().map(({hour, minutes}) => ({hour: labels[hour], minutes}));
    const halfStep = Math.PI / labels.length;
    const days = weekday === null ? t('everyDay') : this.text.weekday(weekday, 'long');

    return {
      ariaLabel: t('chartLabel', {days, period: year ?? t('allTime')}),
      definition: defineChart({
        marks: [
          polar({
            inset: 24,
            startAngle: -halfStep,
            endAngle: 2 * Math.PI - halfStep,
            scales: {
              angle: {scale: scaleBand<string>().domain(labels).paddingInner(0.08).paddingOuter(0.04)},
              radius: {scale: scaleLinear().domain([0, this.longest()])},
            },
            guides: [
              radialGrid({ticks: 3, ...STATS_POLAR_GRID}),
              angleGrid({values: labels.filter((_, hour) => hour % 3 === 0), labelOffset: 10, ...STATS_POLAR_GRID}),
            ],
            marks: [
              radialBarRadius(hours, {angle: 'hour', radius: 'minutes', fill: STATS_CHART_PALETTE[0], cornerRadius: 2, states: STATS_BAR_FOCUS}),
            ],
          }),
        ],
        scales: {x: null, y: null},
        focusRing: false,
        svgAnimation: true,
        tooltip: statsTooltip<(typeof hours)[number]>(([{datum: hour}]) => ({
          title: hour.hour,
          rows: [{label: t('readingTime'), value: this.text.duration(hour.minutes)}],
        })),
      }),
    };
  });

  readonly insights = computed<readonly StatsInsight[]>(() => {
    const {t, duration} = this.text;
    const peak = firstMaximum(this.minutes().filter(({minutes}) => minutes > 0), ({minutes}) => minutes);

    return [
      {label: t('peakHour'), value: peak ? this.hourLabels()[peak.hour] : '—'},
      {label: t('readingTime'), value: duration(this.shown().reduce((sum, slot) => sum + slot.seconds, 0) / 60)},
    ];
  });
}
