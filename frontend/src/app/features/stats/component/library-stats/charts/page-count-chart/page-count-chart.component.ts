import {Component, computed, input} from '@angular/core';
import {barY, defineChart} from '@tanstack/charts';
import {scaleLinear} from '@tanstack/charts/scales/linear';

import {type StatsDistribution, type StatsPageBand} from '../../../../data/stats-response.models';
import {
  countTicks,
  STATS_CHART_PALETTE,
  STATS_COMPACT_WIDTH,
  STATS_PAGE_BAND_LABELS,
  STATS_PAGE_LENGTHS,
  STATS_ROTATED_LABELS,
  statsAxis,
  statsBar,
  statsBarBand,
  statsTooltip,
} from '../../../shared/stats-chart-style';
import {StatsChartPlotComponent} from '../../../shared/stats-chart-plot.component';
import {injectStatsText, STATS_CHART_HOST_CLASS} from '../../../shared/stats-chart-text';

@Component({
  selector: 'app-page-count-chart',
  imports: [StatsChartPlotComponent],
  template: `<app-stats-chart-plot [options]="chart()" />`,
  host: {class: STATS_CHART_HOST_CLASS},
})
export class PageCountChartComponent {
  private readonly text = injectStatsText('statsLibrary.pageCount');

  readonly data = input.required<StatsDistribution<StatsPageBand>>();

  readonly chart = computed(() => {
    const {t, number} = this.text;
    const bands = this.data().rows.flatMap(({value, books}) =>
      value === null || books === 0 ? [] : [{band: STATS_PAGE_BAND_LABELS[value], books}],
    );

    return {
      ariaLabel: t('title'),
      definition: defineChart({
        chart: ({width}) => ({
          marks: [barY(bands, {x: 'band', y: 'books', fill: STATS_CHART_PALETTE[0], ...statsBar(width)})],
          scales: {
            x: {
              scale: statsBarBand(STATS_PAGE_LENGTHS),
              axis: statsAxis(t('axisPageCount'), undefined, width < STATS_COMPACT_WIDTH ? STATS_ROTATED_LABELS : undefined),
            },
            y: {
              scale: scaleLinear,
              nice: true,
              grid: true,
              axis: statsAxis(t('axisBooks'), countTicks(Math.max(0, ...bands.map(({books}) => books)), number)),
            },
          },
        }),
        focus: 'nearest-x',
        focusRing: false,
        svgAnimation: true,
        tooltip: statsTooltip<(typeof bands)[number]>(([{datum: row}]) => ({
          title: t('tooltipTitle', {label: row.band}),
          rows: [{label: t('axisBooks'), value: number(row.books)}],
        })),
      }),
    };
  });
}
