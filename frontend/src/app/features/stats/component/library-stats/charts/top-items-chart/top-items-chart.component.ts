import {Component, computed, input} from '@angular/core';
import {barX, defaultChartTheme, defineChart, stack} from '@tanstack/charts';
import {scaleLinear} from '@tanstack/charts/scales/linear';

import {type KnownBookReadStatus} from '../../../../../book/data/book-response.models';
import {STATS_ITEM_NAMES} from '../../../../card/stats-chart-definition';
import {type StatsItemDimension, type StatsItemMeasure, type StatsTopName} from '../../../../data/stats-response.models';
import {
  countTicks,
  shorten,
  STATS_CHART_OTHER,
  STATS_STATUS_COLORS,
  statsAxis,
  statsBar,
  statsBarBand,
  statsColorScale,
  statsTooltip,
} from '../../../shared/stats-chart-style';
import {StatsChartPlotComponent} from '../../../shared/stats-chart-plot.component';
import {injectStatsText, STATS_CHART_HOST_CLASS} from '../../../shared/stats-chart-text';
import {type StatsLegendItem, StatsLegendComponent} from '../../../shared/stats-legend.component';

const STATUSES = ['READ', 'READING', 'RE_READING', 'PARTIALLY_READ', 'PAUSED', 'UNREAD', 'WONT_READ', 'ABANDONED', 'UNSET'] as const satisfies readonly KnownBookReadStatus[];
const OTHER = 'OTHER';
const NAME_LENGTH = 29;

type Segment = KnownBookReadStatus | typeof OTHER;

interface TopBar {
  readonly name: string;
  readonly status: Segment | null;
  readonly value: number;
}

export interface TopItemsData {
  readonly item: StatsItemDimension;
  readonly by: StatsItemMeasure;
  readonly rows: readonly StatsTopName[];
}

@Component({
  selector: 'app-top-items-chart',
  imports: [StatsChartPlotComponent, StatsLegendComponent],
  template: `
    <app-stats-chart-plot [options]="chart()" />
    @if (legend().length > 0) {
      <app-stats-legend [items]="legend()" />
    }
  `,
  host: {class: STATS_CHART_HOST_CLASS},
})
export class TopItemsChartComponent {
  private readonly text = injectStatsText('statsLibrary.topItems');
  private readonly filterText = injectStatsText('stats.filter');

  readonly data = input.required<TopItemsData>();

  private readonly segments = computed(() => {
    const rows = this.data().rows;
    const present = STATUSES.filter((status) => rows.some((row) => row.statuses.some((each) => each.status === status)));
    const grey = present.filter((status) => STATS_STATUS_COLORS[status] === STATS_CHART_OTHER);
    const merged = grey.length > 1;
    const segments: readonly Segment[] = merged ? [...present.filter((status) => !grey.includes(status)), OTHER] : present;
    return {
      of: (status: KnownBookReadStatus): Segment => (merged && grey.includes(status) ? OTHER : status),
      colors: segments.map((segment) => [segment, segment === OTHER ? STATS_CHART_OTHER : STATS_STATUS_COLORS[segment]] as const),
    };
  });

  readonly legend = computed<readonly StatsLegendItem[]>(() =>
    this.data().by === 'read' ? this.segments().colors.map(([status, color]) => ({label: this.statusName(status), color})) : [],
  );

  readonly chart = computed(() => {
    const {t, number, stars} = this.text;
    const {item, by, rows} = this.data();
    const itemName = this.filterText.t(`dataTypes.${STATS_ITEM_NAMES[item]}`);
    const kind = itemName.toLocaleLowerCase(this.text.locale());
    const colors = this.segments().colors;
    const statuses = colors.map(([status]) => status);
    const segment = this.segments().of;
    const bars: readonly TopBar[] = by === 'read'
      ? rows.flatMap((row) => {
          const counts = new Map<Segment, number>();
          row.statuses.forEach(({status, books}) => counts.set(segment(status), (counts.get(segment(status)) ?? 0) + books));
          return [...counts].map(([status, value]) => ({name: row.name, status, value}));
        })
      : rows.map((row) => ({name: row.name, status: null, value: by === 'rating' ? (row.rating ?? 0) / 2 : row.books}));
    const value = (bar: TopBar) => (by === 'rating' ? stars(bar.value) : number(bar.value));
    const totalLabel = t(by === 'rating' ? 'averageRating' : 'axisNumberOfBooks');

    return {
      ariaLabel: t(`chartLabels.${by}`, {kind}),
      definition: defineChart({
        chart: ({width}) => ({
          marks: [
            by === 'read'
              ? barX(bars, {id: 'top-read', x: 'value', y: 'name', z: 'status', layout: stack({order: statuses}), ...statsBar(width, true)})
              : barX(bars, {id: `top-${by}`, x: 'value', y: 'name', fill: defaultChartTheme.palette[0], ...statsBar(width)}),
          ],
          scales: {
            x: by === 'rating'
              ? {scale: scaleLinear().domain([0, 5]), grid: true, axis: statsAxis<number>(t('axisAverageRating'), {values: [0, 1, 2, 3, 4, 5], format: (stop: number) => stars(stop)})}
              : {scale: scaleLinear, nice: true, grid: true, axis: statsAxis(t('axisNumberOfBooks'), countTicks(Math.max(0, ...rows.map(({books}) => books)), number))},
            y: {scale: statsBarBand(rows.map(({name}) => name)), axis: statsAxis<string>(itemName, {format: (name: string) => shorten(name, NAME_LENGTH)})},
          },
          color: statsColorScale(colors),
        }),
        focus: 'group-y',
        focusRing: false,
        maxFocusDistance: Number.POSITIVE_INFINITY,
        svgAnimation: true,
        tooltip: statsTooltip<TopBar>((points) => ({
          title: points[0].datum.name,
          rows: points.map(({datum: bar, color}) => ({
            label: bar.status ? this.statusName(bar.status) : totalLabel,
            value: value(bar),
            color: bar.status ? color : undefined,
          })),
        })),
      }),
    };
  });

  private statusName(status: Segment): string {
    return status === OTHER ? this.text.t('readStatus.other') : this.text.readStatus(status);
  }
}
