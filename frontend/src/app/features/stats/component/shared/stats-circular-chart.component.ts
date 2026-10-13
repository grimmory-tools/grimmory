import {Component, computed, inject, input} from '@angular/core';
import {defineChart} from '@tanstack/charts';
import {pie, polar, radialArc} from '@tanstack/charts/polar';

import {STATS_CARD} from '../../card/stats-context';
import {statsChartColors, statsTooltip} from './stats-chart-style';
import {StatsChartPlotComponent} from './stats-chart-plot.component';
import {injectStatsText} from './stats-chart-text';
import {type StatsInsight} from './stats-insight-list.component';

export interface StatsSlice {
  readonly id: string;
  readonly label: string;
  readonly value: number;
  readonly neutral?: boolean;
  readonly color?: string;
}

export interface StatsPie {
  readonly label: string;
  readonly valueName: string;
  readonly slices: readonly StatsSlice[];
  readonly donut?: boolean;
}

@Component({
  selector: 'app-stats-circular-chart',
  imports: [StatsChartPlotComponent],
  template: `
    @if (card.state() === 'loading' || card.state() === 'empty') {
      <app-stats-chart-plot [options]="chart()" />
    } @else {
      <div class="@container min-w-0">
        <div class="grid min-w-0 grid-cols-1 place-content-center place-items-center gap-x-4 gap-y-3 @min-[21.75rem]:grid-cols-[minmax(8rem,var(--stats-plot-height))_11.25rem]">
          <div class="w-full max-w-(--stats-plot-height)">
            <app-stats-chart-plot [options]="chart()" />
          </div>

          <div class="w-45 max-w-full min-w-0">
            @if (headline(); as headline) {
              <dl class="mb-2 min-w-0">
                <dt class="text-xs font-medium text-text-secondary">{{ headline.label }}</dt>
                <dd class="mt-1 text-lg font-semibold tabular-nums text-text-strong">{{ headline.value }}</dd>
              </dl>
            }
            <ul class="min-w-0 space-y-1.5 text-xs text-text-secondary">
              @for (slice of slices(); track slice.id) {
                <li class="flex min-w-0 items-center gap-2">
                  <svg class="size-2.5 shrink-0" viewBox="0 0 10 10" aria-hidden="true">
                    <circle cx="5" cy="5" r="5" [attr.fill]="slice.color"></circle>
                  </svg>
                  <span class="min-w-0 flex-1 break-words">{{ slice.label }}</span>
                  <span class="shrink-0 pl-5 font-semibold tabular-nums text-text-strong">{{ text.number(slice.value) }}</span>
                </li>
              }
            </ul>
          </div>
        </div>
      </div>
    }
  `,
  host: {class: 'flex min-w-0 flex-1 flex-col'},
})
export class StatsCircularChartComponent {
  protected readonly card = inject(STATS_CARD);
  protected readonly text = injectStatsText('stats');

  readonly pie = input.required<StatsPie>();
  readonly headline = input<StatsInsight>();

  protected readonly slices = computed(() =>
    statsChartColors(this.pie().slices.filter(({value}) => value > 0), ({neutral, color}) => neutral === true || color !== undefined)
      .map(([slice, color]) => ({...slice, color: slice.color ?? color})),
  );

  protected readonly chart = computed(() => {
    const {label, valueName, donut} = this.pie();
    const {number} = this.text;
    const slices = this.slices();
    const total = slices.reduce((sum, {value}) => sum + value, 0);
    const id = (arc: {readonly source: readonly StatsSlice[]}) => arc.source[0].id;

    return {
      ariaLabel: label,
      definition: defineChart({
        marks: [
          polar({
            inset: 6,
            scales: {angle: null, radius: null},
            marks: [
              radialArc(pie(slices, {value: 'value'}), {
                key: id,
                color: id,
                innerRadius: ({radius}) => (donut ? radius * 0.52 : 0),
              }),
            ],
          }),
        ],
        scales: {x: null, y: null},
        color: {domain: slices.map(({id}) => id), range: slices.map(({color}) => color)},
        svgAnimation: true,
        tooltip: statsTooltip<{readonly source: readonly StatsSlice[]}>(([{datum: arc, color}]) => {
          const slice = arc.source[0];
          const share = number(Math.round((slice.value / total) * 1000) / 10);
          return {
            title: slice.label,
            rows: [{label: valueName, value: `${number(slice.value)} (${share}%)`, color}],
          };
        }),
      }),
    };
  });
}
