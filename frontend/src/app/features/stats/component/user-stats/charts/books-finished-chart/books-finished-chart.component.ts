import {Component, computed, input} from '@angular/core';

import {type StatsActivityMonth, type StatsRows} from '../../../../data/stats-response.models';
import {currentMonth, firstMaximum} from '../../../shared/stats-chart-data';
import {injectStatsText, STATS_CHART_HOST_CLASS} from '../../../shared/stats-chart-text';
import {type StatsHeatmap, type StatsHeatmapColumnLabel, StatsHeatmapComponent} from '../../../shared/stats-heatmap.component';
import {type StatsInsight, StatsInsightListComponent} from '../../../shared/stats-insight-list.component';

const MONTHS = Array.from({length: 12}, (_, index) => String(index + 1).padStart(2, '0'));

@Component({
  selector: 'app-books-finished-chart',
  imports: [StatsHeatmapComponent, StatsInsightListComponent],
  template: `
    <app-stats-heatmap
      [heatmap]="chart()"
      [columns]="12"
      [columnLabels]="monthLabels()"
      [rowLabels]="years()"
      values />
    <app-stats-insight-list [items]="insights()" />
  `,
  host: {class: STATS_CHART_HOST_CLASS},
})
export class BooksFinishedChartComponent {
  private readonly text = injectStatsText('statsUser.booksFinished');

  readonly data = input.required<StatsRows<StatsActivityMonth>>();

  private readonly finished = computed(() => this.data().rows.filter(({finished}) => finished > 0));

  readonly years = computed(() => {
    const lastYear = new Date().getFullYear();
    const firstYear = Math.min(lastYear, ...this.finished().map(({month}) => Number(month.slice(0, 4))));
    return Array.from({length: lastYear - firstYear + 1}, (_, index) => String(lastYear - index));
  });

  readonly monthLabels = computed<readonly StatsHeatmapColumnLabel[]>(() =>
    MONTHS.map((month, column) => ({column, label: this.text.date(`2000-${month}`, {month: 'short'})})),
  );

  readonly chart = computed<StatsHeatmap>(() => {
    const {t, date, number} = this.text;
    const months = new Map(this.data().rows.map((row) => [row.month, row]));
    const now = currentMonth();

    return {
      label: t('chartLabel'),
      valueName: t('booksFinished'),
      cells: this.years().flatMap((year, row) =>
        MONTHS.map((month, column) => {
          const key = `${year}-${month}`;
          const pages = months.get(key)?.finishedPages ?? 0;
          const value = months.get(key)?.finished ?? 0;
          return {
            column,
            row,
            value,
            label: date(key, {month: 'short', year: 'numeric'}),
            future: key > now && value === 0,
            details: pages > 0 ? [{label: t('pagesFinished'), value: number(pages)}] : [],
          };
        }),
      ),
    };
  });

  readonly insights = computed<readonly StatsInsight[]>(() => {
    const {t, number, date} = this.text;
    const year = new Date().getFullYear();
    const thisYear = this.finished().filter(({month}) => month.startsWith(`${year}-`)).reduce((sum, {finished}) => sum + finished, 0);
    const best = firstMaximum(this.finished(), ({finished}) => finished);
    const books = (count: number) => t(count === 1 ? 'bookCount' : 'booksCount', {count: number(count)});

    return [
      {label: t('thisYear'), value: books(thisYear), detail: t('thisYearDetail', {year})},
      {
        label: t('bestMonth'),
        value: books(best?.finished ?? 0),
        detail: best ? date(best.month, {month: 'short', year: 'numeric'}) : '',
      },
    ];
  });
}
