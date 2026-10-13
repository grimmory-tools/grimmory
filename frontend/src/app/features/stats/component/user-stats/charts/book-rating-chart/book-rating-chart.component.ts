import {Component, computed, input} from '@angular/core';
import {defineChart, dot, lineY, rect, text} from '@tanstack/charts';
import {decorative} from '@tanstack/charts/mark/decorative';
import {scaleLinear} from '@tanstack/charts/scales/linear';

import {type StatsDecadeCell, type StatsLengthCell} from '../../../../data/stats-response.models';
import {
  STATS_CHART_PALETTE,
  STATS_CHART_TEXT,
  STATS_COMPACT_WIDTH,
  STATS_PAGE_BAND_LABELS,
  STATS_PAGE_LENGTHS,
  STATS_ROTATED_LABELS,
  statsAxis,
  statsBarBand,
  statsBubbleRadius,
  statsTooltip,
} from '../../../shared/stats-chart-style';
import {firstMaximum} from '../../../shared/stats-chart-data';
import {StatsChartPlotComponent} from '../../../shared/stats-chart-plot.component';
import {injectStatsText, STATS_CHART_HOST_CLASS} from '../../../shared/stats-chart-text';
import {type StatsInsight, StatsInsightListComponent} from '../../../shared/stats-insight-list.component';
import {type StatsLegendItem, StatsLegendComponent, StatsSizeKeyComponent} from '../../../shared/stats-legend.component';

export type BookRatingData =
  | {readonly kind: 'length'; readonly rows: readonly StatsLengthCell[]}
  | {readonly kind: 'decade'; readonly rows: readonly StatsDecadeCell[]};

interface Cell {
  readonly column: string;
  readonly rating: number;
  readonly books: number;
}

const CHARTS = {
  length: {prefix: 'ratingsByLength', axis: 'axisPageCount', insight: 'bestLength', detail: 'bestLengthDetail', countWidth: 52, wordedWidth: 92},
  decade: {prefix: 'ratingsByDecade', axis: 'axisDecade', insight: 'bestDecade', detail: 'bestDecadeDetail', countWidth: 64, wordedWidth: 96},
};
const MINIMUM_BOOKS = 5;
const BUBBLE_COLOR = STATS_CHART_PALETTE[0];
const AVERAGE_COLOR = 'var(--color-text-strong)';
const HIGHLIGHT_COLOR = 'color-mix(in oklab, var(--stats-chart-other) 25%, transparent)';
const BUBBLE_DOMAIN = [-0.4, 5.35] as const;
const RATING_DOMAIN = [0, 5.35] as const;
const COUNT_ROW = -0.1;

@Component({
  selector: 'app-book-rating-chart',
  imports: [StatsChartPlotComponent, StatsInsightListComponent, StatsLegendComponent, StatsSizeKeyComponent],
  template: `
    <app-stats-chart-plot [options]="chart()" />
    <app-stats-legend [items]="legend()">
      <app-stats-size-key [most]="mostBooks()" />
    </app-stats-legend>
    <app-stats-insight-list [items]="insights()" />
  `,
  host: {class: STATS_CHART_HOST_CLASS},
})
export class BookRatingChartComponent {
  private readonly text = injectStatsText('statsUser');

  readonly data = input.required<BookRatingData>();

  private readonly cells = computed<readonly Cell[]>(() => {
    const data = this.data();
    return data.kind === 'length'
      ? data.rows.map(({band, rating, books}) => ({column: STATS_PAGE_BAND_LABELS[band], rating: rating / 2, books}))
      : data.rows.map(({decade, rating, books}) => ({column: `${decade}s`, rating: rating / 2, books}));
  });

  readonly mostBooks = computed(() => Math.max(1, ...this.cells().map(({books}) => books)));

  readonly columns = computed(() => {
    const data = this.data();
    let labels: readonly string[] = STATS_PAGE_LENGTHS;
    if (data.kind === 'decade') {
      if (data.rows.length === 0) return [];
      const first = Math.min(...data.rows.map(({decade}) => decade));
      const last = Math.max(...data.rows.map(({decade}) => decade));
      labels = Array.from({length: (last - first) / 10 + 1}, (_, index) => `${first + index * 10}s`);
    }
    return labels.map((column) => {
      const cells = this.cells().filter((cell) => cell.column === column);
      const books = cells.reduce((sum, cell) => sum + cell.books, 0);
      const rating = books === 0 ? null : cells.reduce((sum, cell) => sum + cell.rating * cell.books, 0) / books;
      return {column, books, rating};
    });
  });

  readonly best = computed(() =>
    firstMaximum(this.columns().filter(({books}) => books >= MINIMUM_BOOKS), ({rating}) => rating ?? 0),
  );

  readonly legend = computed<readonly StatsLegendItem[]>(() => {
    const {t} = this.text;
    const {prefix} = CHARTS[this.data().kind];
    return [
      ...(this.columns().some(({books}) => books > 0) ? [{label: t(`${prefix}.averageRating`), color: AVERAGE_COLOR, swatch: 'dashed' as const}] : []),
      ...(this.best() ? [{label: t(`${prefix}.highestAverage`, {count: MINIMUM_BOOKS}), color: HIGHLIGHT_COLOR, swatch: 'area' as const}] : []),
    ];
  });

  readonly chart = computed(() => {
    const {t, number, stars} = this.text;
    const kind = this.data().kind;
    const {prefix, axis, countWidth, wordedWidth} = CHARTS[kind];
    const cells = this.cells();
    const columns = this.columns();
    const best = this.best();
    const most = this.mostBooks();
    const labels = columns.map(({column}) => column);
    const ratings = new Map(columns.map(({column, rating}) => [column, rating]));
    const average = (value: number) => stars(value, {minimumFractionDigits: 1, maximumFractionDigits: 1});

    return {
      ariaLabel: t(`${prefix}.chartLabel`),
      definition: defineChart({
        chart: ({width}) => {
          const space = width / labels.length;
          const shown = space >= countWidth ? columns.map(({books}) => space >= wordedWidth
            ? t(`${prefix}.${books === 1 ? 'bookCount' : 'booksCount'}`, {count: number(books)}) : number(books)) : [];
          const domain = shown.length > 0 ? BUBBLE_DOMAIN : RATING_DOMAIN;
          return {
            marks: [
              decorative(rect(best ? [best] : [], {
                x: 'column',
                y1: () => domain[0],
                y2: () => domain[1],
                fill: HIGHLIGHT_COLOR,
                inset: 0,
              })),
              decorative(lineY(columns, {x: 'column', y: 'rating', stroke: AVERAGE_COLOR, strokeDasharray: '5 4', strokeWidth: 2, points: true})),
              dot(cells, {
                x: 'column',
                y: 'rating',
                key: (cell) => `${cell.column}:${cell.rating}`,
                r: 'books',
                rScale: (books: number) => statsBubbleRadius(books, most),
                fill: BUBBLE_COLOR,
                fillOpacity: 0.75,
                stroke: 'var(--color-page)',
                strokeWidth: 1.25,
              }),
              decorative(text(shown.map((label, index) => ({column: labels[index], label})), {
                x: 'column',
                y: () => COUNT_ROW,
                text: 'label',
                fill: 'var(--color-text-muted)',
                ...STATS_CHART_TEXT,
              })),
            ],
            scales: {
              x: {
                scale: statsBarBand(labels),
                axis: statsAxis(t(`${prefix}.${axis}`), undefined, kind === 'length' && width < STATS_COMPACT_WIDTH ? STATS_ROTATED_LABELS : undefined),
              },
              y: {
                scale: scaleLinear().domain(domain),
                grid: true,
                axis: statsAxis<number>(t(`${prefix}.axisPersonalRating`), {values: [1, 2, 3, 4, 5], format: (value: number) => stars(value)}),
              },
            },
          };
        },
        svgAnimation: true,
        tooltip: statsTooltip<Cell>(([{datum: cell}]) => {
          const rating = ratings.get(cell.column);
          return {
            title: kind === 'length' ? t(`${prefix}.pageCount`, {count: cell.column}) : cell.column,
            rows: [
              {label: t(`${prefix}.axisPersonalRating`), value: stars(cell.rating)},
              {label: t(`${prefix}.books`), value: number(cell.books)},
              ...(rating == null ? [] : [{label: t(`${prefix}.averageHere`), value: average(rating)}]),
            ],
          };
        }),
      }),
    };
  });

  readonly insights = computed<readonly StatsInsight[]>(() => {
    const {t, number, stars} = this.text;
    const {prefix, insight, detail} = CHARTS[this.data().kind];
    const best = this.best();
    if (!best || best.rating === null) return [{label: t(`${prefix}.${insight}`), value: '—'}];
    const rating = stars(best.rating, {minimumFractionDigits: 1, maximumFractionDigits: 1});
    return [{label: t(`${prefix}.${insight}`), value: best.column, detail: t(`${prefix}.${detail}`, {rating, count: number(best.books)})}];
  });
}
