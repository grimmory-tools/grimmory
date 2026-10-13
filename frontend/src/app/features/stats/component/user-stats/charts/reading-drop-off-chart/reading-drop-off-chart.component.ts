import {Component, computed, input} from '@angular/core';
import {areaY, d3Curve, defineChart, lineY, ruleX, ruleY, text} from '@tanstack/charts';
import {decorative} from '@tanstack/charts/mark/decorative';
import {scaleLinear} from '@tanstack/charts/scales/linear';
import {curveStepBefore} from 'd3-shape';

import {type StatsDistribution} from '../../../../data/stats-response.models';
import {STATS_CHART_PALETTE, STATS_CHART_VALUE, statsAxis, statsTooltip} from '../../../shared/stats-chart-style';
import {StatsChartPlotComponent} from '../../../shared/stats-chart-plot.component';
import {injectStatsText, STATS_CHART_HOST_CLASS} from '../../../shared/stats-chart-text';
import {type StatsInsight, StatsInsightListComponent} from '../../../shared/stats-insight-list.component';
import {type StatsLegendItem, StatsLegendComponent} from '../../../shared/stats-legend.component';

const MILESTONES = [10, 25, 50, 75, 90];

@Component({
  selector: 'app-reading-drop-off-chart',
  imports: [StatsChartPlotComponent, StatsInsightListComponent, StatsLegendComponent],
  template: `
    <app-stats-chart-plot [options]="chart()" />
    <app-stats-legend [items]="legend()" />
    <app-stats-insight-list [items]="insights()" />
  `,
  host: {class: STATS_CHART_HOST_CLASS},
})
export class ReadingDropOffChartComponent {
  private readonly text = injectStatsText('statsUser.readingDropOff');

  readonly data = input.required<StatsDistribution<number>>();

  private readonly started = computed(() =>
    this.data().rows.flatMap(({value, books}) => (value !== null && value > 0 ? [{progress: value, books}] : [])),
  );
  private readonly startedBooks = computed(() => this.started().reduce((sum, {books}) => sum + books, 0));
  readonly points = computed(() => {
    const started = this.startedBooks();
    if (started === 0) return [];
    let remaining = started;
    return [
      {progress: 0, percent: 100},
      ...this.started().map(({progress, books}) => {
        const point = {progress, percent: (remaining / started) * 100};
        remaining -= books;
        return point;
      }),
    ];
  });

  readonly chart = computed(() => {
    const {t, number} = this.text;
    const percent = (value: number) => `${number(value, {maximumFractionDigits: 0})}%`;
    const points = this.points();
    const color = STATS_CHART_PALETTE[0];
    const curve = d3Curve(curveStepBefore);

    return {
      ariaLabel: t('chartLabel'),
      definition: defineChart({
        marks: [
          ruleX(MILESTONES, {strokeOpacity: 0.08}),
          ruleY([50], {strokeDasharray: '4 4'}),
          decorative(text(points.slice(-1), {
            x: 'progress',
            y: 'percent',
            text: (point) => percent(point.percent),
            dx: -9,
            dy: (point) => (point.percent < 10 ? -11 : -9),
            anchor: 'end',
            ...STATS_CHART_VALUE,
          })),
          decorative(areaY(points, {x: 'progress', y1: 0, y2: 'percent', fill: color, fillOpacity: 0.12, curve})),
          lineY(points, {x: 'progress', y: 'percent', stroke: color, points: true, curve}),
        ],
        scales: {
          x: {scale: scaleLinear().domain([0, 100]), axis: statsAxis(t('axisProgress'), {format: percent})},
          y: {scale: scaleLinear().domain([0, 100]), grid: true, axis: statsAxis(t('axisBooksRemaining'), {format: percent})},
        },
        focus: 'group-x',
        maxFocusDistance: Number.POSITIVE_INFINITY,
        svgAnimation: true,
        tooltip: statsTooltip<(typeof points)[number]>(([{datum: point}]) => ({
          title: t('tooltipProgress', {label: percent(point.progress)}),
          rows: [{label: t('booksRemaining'), value: `${number(point.percent, {maximumFractionDigits: 1})}%`, color}],
        })),
      }),
    };
  });

  readonly legend = computed<readonly StatsLegendItem[]>(() => [
    {label: this.text.t('booksRemaining'), color: STATS_CHART_PALETTE[0], swatch: 'line'},
  ]);

  readonly insights = computed<readonly StatsInsight[]>(() => {
    const {t, number} = this.text;
    const stopped = this.startedBooks();
    const finished = this.started().find((row) => row.progress === 100)?.books ?? 0;
    return [
      {
        label: t('completionRate'),
        value: number(stopped > 0 ? finished / stopped : 0, {style: 'percent'}),
        detail: t(stopped === 1 ? 'stoppedBook' : 'stoppedBooks', {finished: number(finished), stopped: number(stopped)}),
      },
    ];
  });
}
