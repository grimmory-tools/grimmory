import {Component, computed, inject, input} from '@angular/core';

import {LanguageResolverService} from '../../../../../../shared/service/language-resolver.service';
import {type StatsKnownDistribution} from '../../../shared/stats-chart-data';
import {StatsCircularChartComponent, type StatsPie} from '../../../shared/stats-circular-chart.component';
import {injectStatsText, STATS_CHART_HOST_CLASS} from '../../../shared/stats-chart-text';

@Component({
  selector: 'app-language-chart',
  imports: [StatsCircularChartComponent],
  template: `<app-stats-circular-chart [pie]="chart()" />`,
  host: {class: STATS_CHART_HOST_CLASS},
})
export class LanguageChartComponent {
  private readonly languageResolver = inject(LanguageResolverService);
  private readonly text = injectStatsText('statsLibrary');

  readonly data = input.required<StatsKnownDistribution<string>>();

  readonly chart = computed<StatsPie>(() => {
    const {t} = this.text;
    const {rows, other, unknown} = this.data();
    return {
      label: t('languages.title'),
      valueName: t('main.books'),
      slices: [
        ...rows.map(({value, books}) => ({id: value, label: this.languageResolver.displayName(value), value: books})),
        {id: '(other)', label: t('languages.other'), value: other, neutral: true},
        {id: '(unknown)', label: t('languages.unknown'), value: unknown, neutral: true},
      ],
    };
  });
}
