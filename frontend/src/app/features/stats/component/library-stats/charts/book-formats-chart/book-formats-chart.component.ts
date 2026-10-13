import {Component, computed, input} from '@angular/core';

import {type StatsDistribution} from '../../../../data/stats-response.models';
import {StatsCircularChartComponent, type StatsPie} from '../../../shared/stats-circular-chart.component';
import {injectStatsText, STATS_CHART_HOST_CLASS} from '../../../shared/stats-chart-text';

@Component({
  selector: 'app-book-formats-chart',
  imports: [StatsCircularChartComponent],
  template: `<app-stats-circular-chart [pie]="chart()" />`,
  host: {class: STATS_CHART_HOST_CLASS},
})
export class BookFormatsChartComponent {
  private readonly text = injectStatsText('statsLibrary');

  readonly data = input.required<StatsDistribution<string>>();

  readonly chart = computed<StatsPie>(() => {
    const {t} = this.text;
    return {
      label: t('bookFormats.title'),
      valueName: t('main.books'),
      slices: this.data().rows.map(({value, books}) => ({
        id: value ?? 'unknown',
        label: value ?? t('bookFormats.unknown'),
        value: books,
        neutral: value === null,
      })),
    };
  });
}
