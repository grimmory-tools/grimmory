import {
  type ChartAxisLabelOptions,
  type ChartAxisPresentationOptions,
  type ChartAxisTickLabelOptions,
  type ChartAxisTickOptions,
  type ChartMarkStateContext,
  type ChartTooltipContent,
  type ChartTooltipContentContext,
  defaultChartTheme,
} from '@tanstack/charts';
import {scaleBand} from '@tanstack/charts/scales/band';
import {tooltip} from '@tanstack/charts/tooltip';
import {portal} from '@tanstack/charts/tooltip/portal';

import {type KnownBookReadStatus} from '../../../book/data/book-response.models';
import {STATS_PAGE_BANDS, type StatsPageBand} from '../../data/stats-response.models';

export const STATS_CHART_PALETTE = defaultChartTheme.palette;
export const STATS_CHART_OTHER = 'var(--stats-chart-other)';

export const STATS_STATUS_COLORS = {
  READ: STATS_CHART_PALETTE[0],
  READING: STATS_CHART_PALETTE[1],
  RE_READING: STATS_CHART_PALETTE[1],
  PARTIALLY_READ: STATS_CHART_PALETTE[2],
  PAUSED: STATS_CHART_PALETTE[3],
  ABANDONED: STATS_CHART_PALETTE[4],
  WONT_READ: STATS_CHART_PALETTE[5],
  UNREAD: STATS_CHART_OTHER,
  UNSET: STATS_CHART_OTHER,
} as const satisfies Record<KnownBookReadStatus, string>;

export function statsChartColors<T>(items: readonly T[], neutral: (item: T) => boolean = () => false): readonly (readonly [T, string])[] {
  let slot = 0;
  return items.map((item) => [item, neutral(item) ? STATS_CHART_OTHER : (STATS_CHART_PALETTE[slot++] ?? STATS_CHART_OTHER)]);
}

export function statsColorScale<K extends string>(colors: readonly (readonly [K, string])[]) {
  return {domain: colors.map(([key]) => key), range: colors.map(([, color]) => color)};
}

export function statsAxis<TValue extends string | number>(
  label?: string,
  ticks?: ChartAxisTickOptions<TValue>,
  tickLabels?: ChartAxisTickLabelOptions<TValue>,
): ChartAxisPresentationOptions<TValue> {
  const title: ChartAxisLabelOptions | undefined =
    label === undefined ? undefined : {text: label, fontWeight: 400, opacity: 1};
  return {line: false, tickLabels: {fontSize: 11, opacity: 1, ...tickLabels}, label: title, ticks};
}

export function shorten(text: string, max: number): string {
  return text.length > max ? `${text.slice(0, max - 1)}…` : text;
}

export function countTicks(maximum: number, format: (value: number) => string): ChartAxisTickOptions<number> {
  return {
    ...(maximum < 5 ? {count: Math.max(1, maximum)} : {}),
    format: (value) => (Number.isInteger(value) ? format(value) : ''),
  };
}

const STATS_BUBBLE_RADIUS = 11;
const SMALLEST_BUBBLE_RADIUS = 3;

export function statsBubbleRadius(books: number, most: number): number {
  return Math.max(SMALLEST_BUBBLE_RADIUS, STATS_BUBBLE_RADIUS * Math.sqrt(books / Math.max(1, most)));
}

export const STATS_POLAR_GRID = {strokeOpacity: 0.11, labelFontSize: 11} as const;

export const STATS_CHART_TEXT = {fontSize: 11} as const;
export const STATS_CHART_VALUE = {fill: 'var(--color-text-strong)', fontSize: 11, fontWeight: 600} as const;

export const STATS_COMPACT_WIDTH = 480;
export const STATS_ROTATED_LABELS = {rotate: -45, thin: false} as const;

export const STATS_PAGE_BAND_LABELS = {
  'up-to-100': '1–100',
  '101-to-200': '101–200',
  '201-to-300': '201–300',
  '301-to-500': '301–500',
  '501-to-750': '501–750',
  '751-to-1000': '751–1000',
  'over-1000': '1001+',
} satisfies Record<StatsPageBand, string>;

export const STATS_PAGE_LENGTHS = STATS_PAGE_BANDS.map((band) => STATS_PAGE_BAND_LABELS[band]);

export function statsBarBand(domain: readonly string[]) {
  return scaleBand<string>().domain(domain).paddingInner(0.18).paddingOuter(0.08);
}

export const STATS_BAR_FOCUS = [
  {
    when: {focus: 'group'},
    style: {fill: ({point}: ChartMarkStateContext) => `color-mix(in oklab, ${point.color} 78%, var(--color-text-strong))`},
  },
] as const;

export function statsBar(width: number, stacked = false) {
  return {inset: width < STATS_COMPACT_WIDTH ? 1 : 2, radius: stacked ? {end: 4} : 4, states: STATS_BAR_FOCUS} as const;
}

export function statsTooltip<T>(
  content: (points: readonly {readonly datum: T; readonly color: string}[], context: ChartTooltipContentContext) => ChartTooltipContent,
) {
  return {
    use: tooltip,
    portal,
    anchor: 'pointer' as const,
    placement: ['right', 'left', 'top', 'bottom'] as const,
    offset: 14,
    content,
  };
}
