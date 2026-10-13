import {Component, computed, input} from '@angular/core';

import {type StatsDistribution, type StatsMetadataBand} from '../../../../data/stats-response.models';
import {STATS_CHART_PALETTE} from '../../../shared/stats-chart-style';
import {StatsCircularChartComponent, type StatsPie} from '../../../shared/stats-circular-chart.component';
import {injectStatsText, STATS_CHART_HOST_CLASS} from '../../../shared/stats-chart-text';
import {type StatsInsight} from '../../../shared/stats-insight-list.component';

const BANDS = [
  {id: 'excellent', labelKey: 'excellent'},
  {id: 'good', labelKey: 'good'},
  {id: 'fair', labelKey: 'fair'},
  {id: 'poor', labelKey: 'poor'},
  {id: 'very-poor', labelKey: 'veryPoor'},
] as const satisfies readonly {id: StatsMetadataBand; labelKey: string}[];

@Component({
  selector: 'app-metadata-score-chart',
  imports: [StatsCircularChartComponent],
  template: `<app-stats-circular-chart [pie]="chart()" [headline]="average()" />`,
  host: {class: STATS_CHART_HOST_CLASS},
})
export class MetadataScoreChartComponent {
  private readonly text = injectStatsText('statsLibrary');

  readonly data = input.required<StatsDistribution<StatsMetadataBand>>();

  readonly chart = computed<StatsPie>(() => {
    const {t} = this.text;
    const books = new Map(this.data().rows.map(({value, books}) => [value, books]));
    return {
      label: t('metadataScore.title'),
      valueName: t('main.books'),
      slices: BANDS.map((band, index) => ({id: band.id, label: t(`metadataScore.${band.labelKey}`), value: books.get(band.id) ?? 0, color: STATS_CHART_PALETTE[index]})),
    };
  });

  readonly average = computed<StatsInsight | undefined>(() => {
    const average = this.data().average;
    return average === undefined
      ? undefined
      : {label: this.text.t('metadataScore.averageLabel'), value: this.text.number(average / 100, {style: 'percent'})};
  });
}
