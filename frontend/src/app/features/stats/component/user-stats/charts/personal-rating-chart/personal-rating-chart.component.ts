import {Component, computed, input} from '@angular/core';
import {barY, defineChart} from '@tanstack/charts';
import {scaleLinear} from '@tanstack/charts/scales/linear';

import {type StatsDistribution} from '../../../../data/stats-response.models';
import {countTicks, STATS_CHART_PALETTE, statsAxis, statsBar, statsBarBand, statsTooltip} from '../../../shared/stats-chart-style';
import {StatsChartPlotComponent} from '../../../shared/stats-chart-plot.component';
import {injectStatsText, STATS_CHART_HOST_CLASS} from '../../../shared/stats-chart-text';

@Component({
  selector: 'app-personal-rating-chart',
  imports: [StatsChartPlotComponent],
  template: `<app-stats-chart-plot [options]="chart()" />`,
  host: {class: STATS_CHART_HOST_CLASS},
})
export class PersonalRatingChartComponent {
  private readonly text = injectStatsText('statsUser.personalRating');

  readonly data = input.required<StatsDistribution<number>>();

  readonly chart = computed(() => {
    const {t, number, stars} = this.text;
    const ratings = this.data().rows.flatMap(({value, books}) =>
      value === null || books === 0 ? [] : [{rating: stars(value / 2), books}],
    );

    return {
      ariaLabel: t('chartLabel'),
      definition: defineChart({
        chart: ({width}) => ({
          marks: [barY(ratings, {x: 'rating', y: 'books', fill: STATS_CHART_PALETTE[0], ...statsBar(width)})],
          scales: {
            x: {
              scale: statsBarBand(Array.from({length: 10}, (_, index) => stars((index + 1) / 2))),
              axis: statsAxis(t('axisPersonalRating')),
            },
            y: {
              scale: scaleLinear,
              nice: true,
              grid: true,
              axis: statsAxis(t('axisNumberOfBooks'), countTicks(Math.max(0, ...ratings.map(({books}) => books)), number)),
            },
          },
        }),
        focus: 'nearest-x',
        focusRing: false,
        svgAnimation: true,
        tooltip: statsTooltip<(typeof ratings)[number]>(([{datum: row}]) => ({
          title: t('tooltipTitle', {label: row.rating}),
          rows: [{label: t('axisNumberOfBooks'), value: number(row.books)}],
        })),
      }),
    };
  });
}
