import {Component, computed, input} from '@angular/core';

import {type KnownBookReadStatus} from '../../../../../book/data/book-response.models';
import {type StatsDistribution} from '../../../../data/stats-response.models';
import {STATS_STATUS_COLORS} from '../../../shared/stats-chart-style';
import {StatsCircularChartComponent, type StatsPie} from '../../../shared/stats-circular-chart.component';
import {injectStatsText, STATS_CHART_HOST_CLASS} from '../../../shared/stats-chart-text';

@Component({
  selector: 'app-read-status-chart',
  imports: [StatsCircularChartComponent],
  template: `<app-stats-circular-chart [pie]="chart()" />`,
  host: {class: STATS_CHART_HOST_CLASS},
})
export class ReadStatusChartComponent {
  private readonly text = injectStatsText('statsUser');

  readonly data = input.required<StatsDistribution<KnownBookReadStatus>>();

  readonly chart = computed<StatsPie>(() => {
    const {t, readStatus} = this.text;
    return {
      label: t('readStatus.title'),
      valueName: t('personalRating.axisNumberOfBooks'),
      donut: true,
      slices: this.data().rows.flatMap(({value, books}) => (value === null ? [] : [{
        id: value,
        label: readStatus(value),
        value: books,
        color: STATS_STATUS_COLORS[value],
      }])),
    };
  });
}
