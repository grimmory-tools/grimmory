import {Component, computed, input} from '@angular/core';
import {defineChart, dot, link} from '@tanstack/charts';
import {decorative} from '@tanstack/charts/mark/decorative';
import {scaleLinear} from '@tanstack/charts/scales/linear';

import {type StatsRows, type StatsComparisonCell} from '../../../../data/stats-response.models';
import {
  STATS_CHART_OTHER,
  STATS_CHART_PALETTE,
  statsAxis,
  statsBubbleRadius,
  statsTooltip,
} from '../../../shared/stats-chart-style';
import {StatsChartPlotComponent} from '../../../shared/stats-chart-plot.component';
import {injectStatsText, STATS_CHART_HOST_CLASS} from '../../../shared/stats-chart-text';
import {type StatsInsight, StatsInsightListComponent} from '../../../shared/stats-insight-list.component';
import {type StatsLegendItem, StatsLegendComponent, StatsSizeKeyComponent} from '../../../shared/stats-legend.component';

const SIDES = ['higher', 'same', 'lower'] as const;
type Side = (typeof SIDES)[number];

interface Cell {
  readonly outside: number;
  readonly rating: number;
  readonly books: number;
  readonly side: Side;
}

const SIDE_COLORS = {
  higher: STATS_CHART_PALETTE[0],
  same: STATS_CHART_OTHER,
  lower: STATS_CHART_PALETTE[1],
} satisfies Record<Side, string>;
const SIDE_LABEL_KEYS = {higher: 'rateHigher', same: 'same', lower: 'rateLower'} satisfies Record<Side, string>;
const DOMAIN = [-0.25, 5.25] as const;
const TICKS = [0, 1, 2, 3, 4, 5];

@Component({
  selector: 'app-rating-comparison-chart',
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
export class RatingComparisonChartComponent {
  private readonly text = injectStatsText('statsUser.ratingComparison');

  readonly data = input.required<StatsRows<StatsComparisonCell>>();

  private readonly cells = computed<readonly Cell[]>(() =>
    this.data().rows.map(({outside, rating, books}) => ({outside, rating, books, side: sideOf(outside, rating)})),
  );

  readonly mostBooks = computed(() => Math.max(1, ...this.cells().map(({books}) => books)));

  private readonly sides = computed(() =>
    SIDES.map((side) => ({side, books: this.cells().reduce((sum, cell) => (cell.side === side ? sum + cell.books : sum), 0)})),
  );

  readonly legend = computed<readonly StatsLegendItem[]>(() =>
    this.sides().filter(({books}) => books > 0).map(({side}) => ({label: this.text.t(SIDE_LABEL_KEYS[side]), color: SIDE_COLORS[side]})),
  );

  readonly chart = computed(() => {
    const {t, number, stars} = this.text;
    const cells = this.cells();
    const most = this.mostBooks();
    const axis = (label: string) => ({
      scale: scaleLinear().domain(DOMAIN),
      grid: true,
      axis: statsAxis<number>(label, {values: TICKS, format: (value: number) => stars(value)}),
    });
    const quarters = {minimumFractionDigits: 2, maximumFractionDigits: 2};

    return {
      ariaLabel: t('chartLabel'),
      definition: defineChart({
        marks: [
          decorative(link([{from: 0, to: 5}], {
            x1: 'from',
            y1: 'from',
            x2: 'to',
            y2: 'to',
            stroke: 'var(--color-text-strong)',
            strokeOpacity: 0.68,
            strokeDasharray: '5 4',
          })),
          dot(cells, {
            x: (cell) => cell.outside / 2,
            y: (cell) => cell.rating / 2,
            key: (cell) => `${cell.outside}:${cell.rating}`,
            z: 'side',
            r: 'books',
            rScale: (books: number) => statsBubbleRadius(books, most),
            fillOpacity: 0.75,
            stroke: 'var(--color-page)',
            strokeWidth: 1.25,
          }),
        ],
        scales: {x: axis(t('axisOtherReaders')), y: axis(t('axisPersonalRating'))},
        clip: true,
        color: {domain: SIDES, range: SIDES.map((side) => SIDE_COLORS[side])},
        svgAnimation: true,
        tooltip: statsTooltip<Cell>(([{datum: cell}]) => ({
          title: t(SIDE_LABEL_KEYS[cell.side]),
          rows: [
            {label: t('books'), value: number(cell.books)},
            {label: t('axisPersonalRating'), value: stars(cell.rating / 2)},
            {
              label: t('axisOtherReaders'),
              value: `${number(Math.max(0, cell.outside / 2 - 0.25), quarters)}–${stars(Math.min(5, cell.outside / 2 + 0.25), quarters)}`,
            },
          ],
        })),
      }),
    };
  });

  readonly insights = computed<readonly StatsInsight[]>(() => {
    const {t, number, stars} = this.text;
    const compared = this.cells().reduce((sum, {books}) => sum + books, 0);
    const average = this.data().rows.reduce((sum, {gapSum}) => sum + gapSum, 0) / Math.max(1, compared);
    const gap = stars(Math.abs(average), {minimumFractionDigits: 2, maximumFractionDigits: 2});
    const books = (side: Side) => {
      const count = this.sides().find((each) => each.side === side)?.books ?? 0;
      return t(count === 1 ? 'bookCount' : 'booksCount', {count: number(count)});
    };

    return [
      Math.abs(average) < 0.005
        ? {label: t('averageLabel'), value: t('averageSame'), detail: t('averageSameDetail')}
        : {label: t('averageLabel'), value: gap, detail: t(average > 0 ? 'averageHigher' : 'averageLower')},
      {label: t('higher'), value: books('higher')},
      {label: t('same'), value: books('same'), detail: t('sameDetail')},
      {label: t('lower'), value: books('lower')},
    ];
  });
}

function sideOf(outside: number, rating: number): Side {
  if (rating > outside) return 'higher';
  return rating < outside ? 'lower' : 'same';
}
