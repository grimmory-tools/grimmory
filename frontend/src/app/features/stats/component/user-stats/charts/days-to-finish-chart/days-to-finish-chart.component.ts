import {Component, computed, input} from '@angular/core';
import {barY, defineChart} from '@tanstack/charts';
import {scaleLinear} from '@tanstack/charts/scales/linear';

import {type StatsFinishBand, type StatsFinishTimes} from '../../../../data/stats-response.models';
import {
  countTicks,
  shorten,
  STATS_CHART_PALETTE,
  STATS_COMPACT_WIDTH,
  STATS_ROTATED_LABELS,
  statsAxis,
  statsBar,
  statsBarBand,
  statsTooltip,
} from '../../../shared/stats-chart-style';
import {StatsChartPlotComponent} from '../../../shared/stats-chart-plot.component';
import {injectStatsText, STATS_CHART_HOST_CLASS} from '../../../shared/stats-chart-text';
import {type StatsInsight, StatsInsightListComponent} from '../../../shared/stats-insight-list.component';

export interface DaysToFinishData extends StatsFinishTimes {
  readonly year: number;
}

const BANDS = [
  {id: 'under-a-week', labelKey: 'underAWeek'},
  {id: '1-to-2-weeks', labelKey: 'oneToTwoWeeks'},
  {id: '2-to-4-weeks', labelKey: 'twoToFourWeeks'},
  {id: '1-to-2-months', labelKey: 'oneToTwoMonths'},
  {id: '2-to-3-months', labelKey: 'twoToThreeMonths'},
  {id: '3-months-or-more', labelKey: 'threeMonthsOrMore'},
] as const satisfies readonly {id: StatsFinishBand; labelKey: string}[];
const TITLE_LENGTH = 40;

@Component({
  selector: 'app-days-to-finish-chart',
  imports: [StatsChartPlotComponent, StatsInsightListComponent],
  template: `
    <app-stats-chart-plot [options]="chart()" />
    <app-stats-insight-list [items]="insights()" />
  `,
  host: {class: STATS_CHART_HOST_CLASS},
})
export class DaysToFinishChartComponent {
  private readonly text = injectStatsText('statsUser.daysToFinish');

  readonly data = input.required<DaysToFinishData>();

  readonly bands = computed(() => {
    const byBand = new Map(this.data().rows.map((row) => [row.band, row]));
    return BANDS.map(({id, labelKey}) => {
      const row = byBand.get(id);
      return {id, label: this.text.t(labelKey), books: row?.books ?? 0, titles: row?.titles ?? []};
    });
  });

  readonly chart = computed(() => {
    const {t, number} = this.text;
    const bands = this.bands();
    const labels = bands.map(({label}) => label);

    return {
      ariaLabel: t('chartLabel', {year: this.data().year}),
      definition: defineChart({
        chart: ({width}) => ({
          marks: [barY(bands, {x: 'label', y: 'books', fill: STATS_CHART_PALETTE[0], ...statsBar(width)})],
          scales: {
            x: {
              scale: statsBarBand(labels),
              axis: statsAxis(t('axisTimeToFinish'), undefined, width < STATS_COMPACT_WIDTH ? STATS_ROTATED_LABELS : undefined),
            },
            y: {
              scale: scaleLinear,
              nice: true,
              grid: true,
              axis: statsAxis(t('axisNumberOfBooks'), countTicks(Math.max(0, ...bands.map(({books}) => books)), number)),
            },
          },
        }),
        focus: 'nearest-x',
        focusRing: false,
        svgAnimation: true,
        tooltip: statsTooltip<(typeof bands)[number]>(([{datum: band}]) => {
          const more = band.books - band.titles.length;
          return {
            title: band.label,
            rows: [
              {label: t('books'), value: number(band.books)},
              ...band.titles.map((book) => ({label: shorten(book.title || t('untitled'), TITLE_LENGTH), value: this.days(book.days)})),
              ...(more > 0 ? [{label: t('andMore', {count: number(more)}), value: ''}] : []),
            ],
          };
        }),
      }),
    };
  });

  readonly insights = computed<readonly StatsInsight[]>(() => {
    const {t, number} = this.text;
    const {rows, median, quickest, longest} = this.data();
    if (median === null || !quickest || !longest) return [];

    return [
      {label: t('booksFinished'), value: number(rows.reduce((total, {books}) => total + books, 0)), detail: t('withSessions')},
      {label: t('median'), value: this.days(median)},
      {label: t('quickest'), value: this.days(quickest.days), detail: quickest.title || t('untitled')},
      {label: t('longest'), value: this.days(longest.days), detail: longest.title || t('untitled')},
    ];
  });

  private days(count: number): string {
    return this.text.number(count, {style: 'unit', unit: 'day', unitDisplay: 'long', maximumFractionDigits: 0});
  }
}
