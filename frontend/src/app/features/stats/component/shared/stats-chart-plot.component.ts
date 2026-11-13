import {NgTemplateOutlet} from '@angular/common';
import {Component, computed, inject, input, type TemplateRef} from '@angular/core';
import {TranslocoPipe} from '@jsverse/transloco';
import {type ChartValue} from '@tanstack/charts';
import {Chart, type ChartOptions, ChartTooltipBodyDirective} from '@tanstack/charts/angular';

import {STATS_CARD} from '../../card/stats-context';

@Component({
  selector: 'app-stats-chart-plot',
  imports: [Chart, ChartTooltipBodyDirective, NgTemplateOutlet, TranslocoPipe],
  template: `
    @if (card.state() === 'loading') {
      <div class="absolute inset-0 overflow-hidden rounded-lg bg-text/4" role="status" [attr.aria-label]="'stats.card.loading' | transloco">
        <div
          class="absolute inset-0 animate-pulse bg-linear-to-r from-transparent via-text/8 to-transparent motion-reduce:animate-none"
          aria-hidden="true"></div>
      </div>
    } @else if (card.state() === 'empty') {
      <p class="absolute inset-0 flex items-center justify-center px-4 text-center text-sm leading-5 text-text-secondary">
        {{ 'stats.card.noData' | transloco }}
      </p>
    } @else {
      <tanstack-chart class="absolute inset-0 text-text-secondary" [options]="chart()">
        @if (tooltipBody(); as body) {
          <ng-template [tanstackChartTooltipBody]="chart().definition" let-tooltip>
            <ng-container [ngTemplateOutlet]="body" [ngTemplateOutletContext]="{$implicit: tooltip}" />
          </ng-template>
        }
      </tanstack-chart>
    }
  `,
  host: {
    class: 'relative block min-w-0',
    '[style.flex]': 'height() ? "none" : "1 1 0%"',
    '[style.min-height]': 'height() ? null : "var(--stats-plot-height, 14rem)"',
    '[style.height]': 'height() ?? null',
  },
})
export class StatsChartPlotComponent<T, X extends ChartValue, Y extends ChartValue> {
  protected readonly card = inject(STATS_CARD);

  readonly options = input.required<ChartOptions<T, X, Y>>();
  readonly height = input<string>();
  readonly tooltipBody = input<TemplateRef<unknown>>();

  protected readonly chart = computed<ChartOptions<T, X, Y>>(() => ({
    ...this.options(),
    ariaDescription: this.card.description(),
    style: 'height:100%',
    className: 'rounded-lg outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary',
  }));
}
