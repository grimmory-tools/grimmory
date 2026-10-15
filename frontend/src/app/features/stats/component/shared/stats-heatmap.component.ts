import {booleanAttribute, Component, computed, inject, input} from '@angular/core';
import {cell, type ChartTooltipRow, defaultChartTheme, defineChart, text} from '@tanstack/charts';
import {decorative} from '@tanstack/charts/mark/decorative';
import {scaleBand} from '@tanstack/charts/scales/band';

import {STATS_CARD} from '../../card/stats-context';
import {STATS_CHART_VALUE, statsAxis, statsTooltip} from './stats-chart-style';
import {StatsChartPlotComponent} from './stats-chart-plot.component';
import {injectStatsText} from './stats-chart-text';

export interface StatsHeatmapCell {
  readonly column: number;
  readonly row: number;
  readonly value: number;
  readonly label: string;
  readonly future?: boolean;
  readonly details?: readonly ChartTooltipRow[];
}

export interface StatsHeatmap {
  readonly label: string;
  readonly valueName: string;
  readonly cells: readonly StatsHeatmapCell[];
}

export interface StatsHeatmapColumnLabel {
  readonly column: number;
  readonly label: string;
}

const STATS_HEATMAP_COLORS = [4, 22, 44, 68, 100].map((share) => `color-mix(in oklab, ${defaultChartTheme.palette[0]} ${share}%, var(--color-page))`);

const STATS_HEATMAP_SCALE = {domain: ['0', '1', '2', '3', '4'], range: STATS_HEATMAP_COLORS};

const VALUE_CELL_SIZE = 22;
const SQUARE_MARGIN = {top: 24, right: 0, bottom: 0, left: 32};
const MINIMUM_SQUARE_CELL = 12;

@Component({
  selector: 'app-stats-heatmap',
  imports: [StatsChartPlotComponent],
  template: `
    <div class="scroll-fade-x flex min-h-0 min-w-0 flex-1 flex-col overflow-x-auto px-2">
      <div class="@container flex min-h-0 flex-1 flex-col" [style.min-width.px]="minimumWidth()">
        <app-stats-chart-plot [options]="chart()" [height]="height()" />
      </div>
    </div>
    @if (card.state() !== 'empty') {
      <div class="-mt-2 flex items-center justify-end gap-2 px-2 text-xs text-text-muted">
        <span>{{ text.t('heatmapLess') }}</span>
        <span class="flex items-center gap-1" aria-hidden="true">
          @for (color of colors; track color) {
            <span class="size-3 rounded-[3px]" [style.background]="color"></span>
          }
        </span>
        <span>{{ text.t('heatmapMore') }}</span>
      </div>
    }
  `,
  host: {class: 'flex min-h-0 min-w-0 flex-col gap-4', '[class.flex-1]': '!square()'},
})
export class StatsHeatmapComponent {
  protected readonly card = inject(STATS_CARD);
  protected readonly text = injectStatsText('stats');

  readonly heatmap = input.required<StatsHeatmap>();
  readonly columns = input.required<number>();
  readonly columnLabels = input.required<readonly StatsHeatmapColumnLabel[]>();
  readonly rowLabels = input.required<readonly string[]>();
  readonly square = input(false, {transform: booleanAttribute});
  readonly values = input(false, {transform: booleanAttribute});

  protected readonly colors = STATS_HEATMAP_COLORS;

  protected readonly minimumWidth = computed(() =>
    this.square() && this.card.state() === 'ready' ? this.columns() * MINIMUM_SQUARE_CELL + SQUARE_MARGIN.left : null,
  );

  protected readonly height = computed(() => {
    if (!this.square()) return undefined;
    const {top, left} = SQUARE_MARGIN;
    return `calc((100cqw - ${left}px) * ${this.rowLabels().length} / ${this.columns()} + ${top}px)`;
  });

  protected readonly chart = computed(() => {
    const {label, valueName, cells} = this.heatmap();
    const columns = Array.from({length: this.columns()}, (_, column) => String(column));
    const columnLabels = new Map(this.columnLabels().map(({column, label}) => [String(column), label]));
    const rowLabels = this.rowLabels();
    const rows = rowLabels.map((_, row) => String(row));
    const past = cells.filter(({future}) => !future);
    const future = cells.filter(({future}) => future);
    const values = this.values();
    const maximum = Math.max(0, ...cells.map(({value}) => value));
    const level = ({value}: StatsHeatmapCell) => (value <= 0 ? 0 : Math.min(4, Math.ceil((value / maximum) * 4)));

    return {
      ariaLabel: label,
      definition: defineChart({
        chart: ({width, height}) => {
          const size = Math.min(width / columns.length, height / rows.length);
          const inset = Math.min(1.5, size * 0.1);
          return {
            marks: [
              cell(past, {
                x: ({column}) => String(column),
                y: ({row}) => String(row),
                color: (each) => String(level(each)),
                inset,
                radius: 2,
                states: [{when: {focus: 'primary'}, style: {stroke: 'var(--color-text-strong)', strokeWidth: 1.5}}],
              }),
              decorative(cell(future, {
                x: ({column}) => String(column),
                y: ({row}) => String(row),
                fill: 'none',
                stroke: 'var(--color-border)',
                strokeWidth: 1,
                inset: inset + 0.5,
                radius: 2,
              })),
              decorative(text(values && size >= VALUE_CELL_SIZE ? past.filter(({value}) => value > 0) : [], {
                x: ({column}) => String(column),
                y: ({row}) => String(row),
                text: ({value}) => this.text.number(value),
                ...STATS_CHART_VALUE,
                fill: (each) => (level(each) === 4 ? 'white' : STATS_CHART_VALUE.fill),
              })),
            ],
            scales: {
              x: {
                scale: scaleBand().domain(columns),
                side: 'top',
                axis: statsAxis(
                  undefined,
                  {values: [...columnLabels.keys()], size: 0, format: (column: string) => columnLabels.get(column) ?? ''},
                  columnLabels.size < columns.length ? {anchor: 'start', dx: ({bandwidth}) => -bandwidth / 2} : undefined,
                ),
              },
              y: {
                scale: scaleBand().domain(rows),
                axis: statsAxis(undefined, {
                  values: rows.filter((row) => rowLabels[Number(row)]),
                  size: 0,
                  format: (row: string) => rowLabels[Number(row)],
                }),
              },
            },
            color: STATS_HEATMAP_SCALE,
            ...(this.square() ? {margin: SQUARE_MARGIN} : {}),
          };
        },
        focusRing: false,
        svgAnimation: true,
        tooltip: statsTooltip<StatsHeatmapCell>(([{datum: square}]) => ({
          title: square.label,
          rows: [{label: valueName, value: this.text.number(square.value)}, ...(square.details ?? [])],
        })),
      }),
    };
  });
}
