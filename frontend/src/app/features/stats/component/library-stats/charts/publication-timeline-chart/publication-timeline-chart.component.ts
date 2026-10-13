import {Component, computed, input} from '@angular/core';
import {barY, defineChart, ruleY} from '@tanstack/charts';
import {scaleLinear} from '@tanstack/charts/scales/linear';

import {type StatsDistribution} from '../../../../data/stats-response.models';
import {
  countTicks,
  STATS_CHART_PALETTE,
  statsAxis,
  statsBar,
  statsBarBand,
  statsTooltip,
} from '../../../shared/stats-chart-style';
import {firstMaximum} from '../../../shared/stats-chart-data';
import {StatsChartPlotComponent} from '../../../shared/stats-chart-plot.component';
import {injectStatsText, STATS_CHART_HOST_CLASS} from '../../../shared/stats-chart-text';
import {type StatsInsight, StatsInsightListComponent} from '../../../shared/stats-insight-list.component';

const BEFORE_1900 = 'before-1900';
const FIRST_DECADE = 1900;

@Component({
  selector: 'app-publication-timeline-chart',
  imports: [StatsChartPlotComponent, StatsInsightListComponent],
  template: `
    <app-stats-chart-plot [options]="chart()" />
    <app-stats-insight-list [items]="insights()" />
  `,
  host: {class: STATS_CHART_HOST_CLASS},
})
export class PublicationTimelineChartComponent {
  private readonly text = injectStatsText('statsLibrary.publicationTimeline');

  readonly data = input.required<StatsDistribution<number>>();

  private readonly years = computed(() =>
    this.data().rows.flatMap(({value, books}) => (value === null ? [] : [{year: value, books}])),
  );

  readonly decades = computed(() => {
    const years = this.years();
    const oldest = years.at(0)?.year;
    const newest = years.at(-1)?.year;
    if (oldest === undefined || newest === undefined) return [];

    const books = new Map<string, number>();
    for (const {year, books: count} of years) {
      const decade = year < FIRST_DECADE ? BEFORE_1900 : `${Math.floor(year / 10) * 10}`;
      books.set(decade, (books.get(decade) ?? 0) + count);
    }
    const decades = oldest < FIRST_DECADE ? [BEFORE_1900] : [];
    for (let decade = Math.max(FIRST_DECADE, Math.floor(oldest / 10) * 10); decade <= newest; decade += 10) {
      decades.push(`${decade}`);
    }
    return decades.map((decade) => ({decade, books: books.get(decade) ?? 0}));
  });

  readonly chart = computed(() => {
    const {t, number} = this.text;
    const decades = this.decades().map(({decade, books}) => ({decade: this.decadeLabel(decade), books}));
    const labels = decades.map(({decade}) => decade);

    return {
      ariaLabel: t('chartLabel'),
      definition: defineChart({
        chart: ({width}) => ({
          marks: [
            ruleY([0], {strokeOpacity: 0.3}),
            barY(decades, {x: 'decade', y: 'books', fill: STATS_CHART_PALETTE[0], ...statsBar(width)}),
          ],
          scales: {
            x: {
              scale: statsBarBand(labels),
              axis: statsAxis(t('axisDecade'), undefined, {thin: {priority: 'ends', minGap: 8}}),
            },
            y: {
              scale: scaleLinear,
              nice: true,
              grid: true,
              axis: statsAxis(t('axisNumberOfBooks'), countTicks(Math.max(0, ...decades.map(({books}) => books)), number)),
            },
          },
        }),
        focus: 'nearest-x',
        focusRing: false,
        svgAnimation: true,
        tooltip: statsTooltip<(typeof decades)[number]>(([{datum: row}]) => ({
          title: row.decade,
          rows: [{label: t('axisNumberOfBooks'), value: number(row.books)}],
        })),
      }),
    };
  });

  readonly insights = computed<readonly StatsInsight[]>(() => {
    const oldest = this.years().at(0)?.year;
    const newest = this.years().at(-1)?.year;
    if (oldest === undefined || newest === undefined) return [];
    const {t, number} = this.text;
    const peak = firstMaximum(this.decades(), ({books}) => books);
    const noYear = this.data().rows.find(({value}) => value === null)?.books ?? 0;
    return [
      {label: t('insightOldest'), value: `${oldest}`},
      {label: t('insightNewest'), value: `${newest}`},
      {
        label: t('insightMostBooks'),
        value: peak ? this.decadeLabel(peak.decade) : '—',
        detail: t(peak?.books === 1 ? 'bookCount' : 'booksCount', {count: number(peak?.books ?? 0)}),
      },
      {label: t('insightNoYear'), value: number(noYear), detail: t('insightNoYearDetail')},
    ];
  });

  private decadeLabel(decade: string): string {
    return decade === BEFORE_1900 ? this.text.t('decadePre1900') : this.text.t('decadeLabel', {decade});
  }
}
