import {Component, computed, input} from '@angular/core';
import {d3Curve, defineChart, link, rect, text} from '@tanstack/charts';
import {sankeyDiagram, type SankeyLink, type SankeyNode} from '@tanstack/charts/network/sankey';
import {curveBumpX} from 'd3-shape';

import {type StatsBookFlow, type StatsStatusGroup} from '../../../../data/stats-response.models';
import {STATS_CHART_TEXT, STATS_STATUS_COLORS, statsChartColors, statsTooltip} from '../../../shared/stats-chart-style';
import {firstMaximum} from '../../../shared/stats-chart-data';
import {StatsChartPlotComponent} from '../../../shared/stats-chart-plot.component';
import {injectStatsText, STATS_CHART_HOST_CLASS} from '../../../shared/stats-chart-text';
import {type StatsInsight, StatsInsightListComponent} from '../../../shared/stats-insight-list.component';

const FLOW_STATUSES = ['read', 'reading', 'paused', 'abandoned', 'unread'] as const satisfies readonly StatsStatusGroup[];
const FLOW_RATINGS = ['high', 'mid', 'low', 'unrated'] as const;
type FlowRating = (typeof FLOW_RATINGS)[number];
type FlowColumn = 'added' | 'status' | 'rating';

interface FlowNode {
  readonly key: string;
  readonly column: FlowColumn;
  readonly label: string;
  readonly color: string;
}

interface FlowLink {
  readonly source: string;
  readonly target: string;
  readonly value: number;
}

interface FlowHeader {
  readonly kind?: undefined;
  readonly x: number;
  readonly y: number;
  readonly label: string;
}

type FlowItem = SankeyNode<FlowNode, FlowLink> | SankeyLink<FlowNode, FlowLink> | FlowHeader;

const STATUS_COLORS = {
  read: STATS_STATUS_COLORS.READ,
  reading: STATS_STATUS_COLORS.READING,
  paused: STATS_STATUS_COLORS.PAUSED,
  abandoned: STATS_STATUS_COLORS.ABANDONED,
  unread: STATS_STATUS_COLORS.UNREAD,
} satisfies Record<StatsStatusGroup, string>;
const STATUS_LABEL_KEYS = {
  read: 'statusRead',
  reading: 'statusReading',
  unread: 'statusUnread',
  paused: 'statusPaused',
  abandoned: 'statusAbandoned',
} satisfies Record<StatsStatusGroup, string>;
const RATING_LABEL_KEYS = {
  high: 'ratingHigh',
  mid: 'ratingMid',
  low: 'ratingLow',
  unrated: 'ratingUnrated',
} satisfies Record<FlowRating, string>;
const COLUMN_KEYS = ['colAdded', 'colStatus', 'colRating'];
const UNKNOWN_YEAR = 'unknown';
const EARLIER_YEARS = 'earlier';
const ALL_YEARS_UP_TO = 6;
const RECENT_YEARS = 5;
const NEUTRAL_NODES = new Set([`added:${EARLIER_YEARS}`, `added:${UNKNOWN_YEAR}`, 'rating:unrated']);
const NODE_WIDTH = 16;
const LABEL_GAP = 8;
const COUNT_LABEL_WIDTH = 640;

@Component({
  selector: 'app-book-flow-chart',
  imports: [StatsChartPlotComponent, StatsInsightListComponent],
  template: `
    <div class="scroll-fade-x flex min-h-0 flex-1 @max-lg:overflow-x-auto @max-lg:pb-1">
      <div class="flex min-w-120 flex-1 flex-col">
        <app-stats-chart-plot [options]="chart()" />
      </div>
    </div>
    <app-stats-insight-list [items]="insights()" />
  `,
  host: {class: STATS_CHART_HOST_CLASS},
})
export class BookFlowChartComponent {
  private readonly text = injectStatsText('statsUser.bookFlow');

  readonly data = input.required<StatsBookFlow>();

  private readonly knownYears = computed(() =>
    [...new Set(this.data().byYear.flatMap(({year}) => (year === null ? [] : [year])))].sort((left, right) => left - right),
  );

  private readonly shownYears = computed(() => {
    const known = this.knownYears();
    return new Set(known.length <= ALL_YEARS_UP_TO ? known : known.slice(-RECENT_YEARS));
  });

  private readonly years = computed(() => {
    const present = new Set(this.data().byYear.map(({year}) => this.yearKey(year)));
    return [EARLIER_YEARS, ...[...this.shownYears()].map(String), UNKNOWN_YEAR].filter((key) => present.has(key));
  });

  private readonly flows = computed(() => {
    const flows = new Map<string, FlowLink>();
    const add = (source: string, target: string, value: number) => {
      const key = `${source}>${target}`;
      flows.set(key, {source, target, value: (flows.get(key)?.value ?? 0) + value});
    };
    this.data().byYear.forEach(({year, group, books}) => add(`added:${this.yearKey(year)}`, `status:${group}`, books));
    this.data().byRating.forEach(({group, level, books}) => add(`status:${group}`, `rating:${level ?? 'unrated'}`, books));
    return [...flows.values()];
  });

  private readonly nodes = computed<readonly FlowNode[]>(() => {
    const {t} = this.text;
    const flows = this.flows();
    const used = new Set(flows.flatMap(({source, target}) => [source, target]));
    const column = <Id extends string>(column: FlowColumn, ids: readonly Id[], label: (id: Id) => string, colors?: Readonly<Record<Id, string>>): FlowNode[] =>
      statsChartColors(ids.map((id) => ({id, key: `${column}:${id}`})), ({key}) => NEUTRAL_NODES.has(key))
        .filter(([{key}]) => used.has(key))
        .map(([entry, color]) => ({key: entry.key, column, label: label(entry.id), color: colors?.[entry.id] ?? color}));

    return [
      ...column('added', this.years(), (id) =>
        id === EARLIER_YEARS ? t('earlierYears') : id === UNKNOWN_YEAR ? t('unknownYear') : id),
      ...column('status', FLOW_STATUSES, (id) => t(STATUS_LABEL_KEYS[id]), STATUS_COLORS),
      ...column('rating', FLOW_RATINGS, (id) => t(RATING_LABEL_KEYS[id])),
    ];
  });

  readonly chart = computed(() => {
    const {t, number} = this.text;
    const nodes = this.nodes();
    const links = this.flows();
    const lastLayer = (node: SankeyNode<FlowNode, FlowLink>) => node.data.column === 'rating';

    return {
      ariaLabel: t('chartLabel'),
      definition: defineChart({
        chart: ({width}) => {
          const label = (node: SankeyNode<FlowNode, FlowLink>) =>
            width >= COUNT_LABEL_WIDTH ? `${node.data.label} (${number(node.value)})` : node.data.label;
          return {
            marks: [
              sankeyDiagram({
                nodes,
                links,
                nodeKey: 'key',
                source: 'source',
                target: 'target',
                value: 'value',
                nodeSort: null,
                nodeWidth: NODE_WIDTH,
                inset: {top: 24, bottom: 4},
                marks: ({nodes: layout, links: flows}) => {
                  const top = Math.min(...layout.map((node) => node.y0));
                  const headers = [0, 1, 2].flatMap((layer): FlowHeader[] => {
                    const column = layout.filter((node) => node.layer === layer);
                    return column.length ? [{x: column[0].x, y: top - 12, label: t(COLUMN_KEYS[layer])}] : [];
                  });
                  return [
                    link(flows, {
                      x1: 'x1',
                      y1: 'y1',
                      x2: 'x2',
                      y2: 'y2',
                      key: 'key',
                      stroke: (flow) => flow.sourceNode.data.color,
                      strokeOpacity: 0.3,
                      strokeWidth: (flow) => Math.max(1, flow.width),
                      lineCap: 'butt',
                      curve: d3Curve(curveBumpX),
                    }),
                    rect(layout, {x1: 'x0', x2: 'x1', y1: 'y0', y2: 'y1', key: 'key', color: (node) => node.data.key, inset: 0, radius: 3}),
                    text(layout, {
                      x: (node) => (lastLayer(node) ? node.x1 + LABEL_GAP : node.x0 - LABEL_GAP),
                      y: 'y',
                      key: 'key',
                      text: label,
                      anchor: (node) => (lastLayer(node) ? 'start' : 'end'),
                      ...STATS_CHART_TEXT,
                    }),
                    text(headers, {x: 'x', y: 'y', key: 'label', text: 'label', ...STATS_CHART_TEXT}),
                  ] as const;
                },
              }),
            ],
            guides: false,
            scales: {x: null, y: null},
            color: {domain: nodes.map((node) => node.key), range: nodes.map((node) => node.color)},
          };
        },
        svgAnimation: true,
        tooltip: statsTooltip<FlowItem>(([{datum: item}]) => {
          switch (item.kind) {
            case 'node':
              return {title: item.data.label, rows: [{label: t('totalBooks'), value: number(item.value), color: item.data.color}]};
            case 'link':
              return {
                title: `${item.sourceNode.data.label} → ${item.targetNode.data.label}`,
                rows: [{label: t('totalBooks'), value: number(item.value), color: item.sourceNode.data.color}],
              };
            default:
              return {title: item.label, rows: []};
          }
        }),
      }),
    };
  });

  readonly insights = computed<readonly StatsInsight[]>(() => {
    const {t, number} = this.text;
    const rows = this.data().byYear;
    const total = rows.reduce((sum, {books}) => sum + books, 0);
    const read = rows.reduce((sum, row) => (row.group === 'read' ? sum + row.books : sum), 0);
    const busiest = firstMaximum(
      this.knownYears().map((year) => ({year, books: rows.reduce((sum, row) => (row.year === year ? sum + row.books : sum), 0)})),
      ({books}) => books,
    );
    return [
      {
        label: t('read'),
        value: number(total > 0 ? read / total : 0, {style: 'percent'}),
        detail: t(total === 1 ? 'readOfLibraryBook' : 'readOfLibrary', {read: number(read), total: number(total)}),
      },
      {
        label: t('busiestYear'),
        value: busiest ? `${busiest.year}` : t('unknownYear'),
        detail: busiest ? t(busiest.books === 1 ? 'busiestYearBook' : 'busiestYearBooks', {count: number(busiest.books)}) : '',
      },
    ];
  });

  private yearKey(year: number | null): string {
    if (year === null) return UNKNOWN_YEAR;
    return this.shownYears().has(year) ? `${year}` : EARLIER_YEARS;
  }
}
