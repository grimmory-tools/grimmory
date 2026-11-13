import {Component, computed, inject, input} from '@angular/core';

import {STATS_CARD} from '../../card/stats-context';
import {statsBubbleRadius} from './stats-chart-style';
import {injectStatsText} from './stats-chart-text';

export interface StatsLegendItem {
  readonly label: string;
  readonly color: string;
  readonly swatch?: 'line' | 'dashed' | 'area';
}

@Component({
  selector: 'app-stats-legend',
  template: `
    <div class="flex min-h-4 flex-wrap items-center gap-x-5 gap-y-2 px-2 text-xs text-text-secondary">
      @if (items().length > 0) {
        <ul class="flex min-w-0 flex-wrap items-center gap-x-4 gap-y-1.5">
          @for (item of items(); track item.label) {
            <li class="flex min-w-0 items-center gap-1.5">
              @switch (item.swatch) {
                @case ('line') {
                  <span class="w-4 shrink-0 border-t-2" [style.border-color]="item.color" aria-hidden="true"></span>
                }
                @case ('dashed') {
                  <svg class="h-2.5 w-4 shrink-0" viewBox="0 0 16 10" aria-hidden="true">
                    <line x1="0" y1="5" x2="16" y2="5" stroke-width="2" stroke-dasharray="5 4" [attr.stroke]="item.color"></line>
                  </svg>
                }
                @case ('area') {
                  <span class="size-2.5 shrink-0 rounded-[2px]" [style.background-color]="item.color" aria-hidden="true"></span>
                }
                @default {
                  <span class="size-2.5 shrink-0 rounded-full" [style.background-color]="item.color" aria-hidden="true"></span>
                }
              }
              <span class="min-w-0 truncate" [title]="item.label">{{ item.label }}</span>
            </li>
          }
        </ul>
      }
      <ng-content />
    </div>
  `,
  host: {
    class: 'block min-w-0 -mt-2',
    '[class.hidden]': "card.state() === 'empty'",
  },
})
export class StatsLegendComponent {
  protected readonly card = inject(STATS_CARD);

  readonly items = input.required<readonly StatsLegendItem[]>();
}

@Component({
  selector: 'app-stats-size-key',
  template: `
    <span>{{ text.t('sizeKey') }}</span>
    @for (books of values(); track books) {
      <span class="flex items-center gap-1">
        <span
          class="shrink-0 rounded-full border border-current opacity-60"
          [style.width.px]="radius(books) * 2"
          [style.height.px]="radius(books) * 2"
          aria-hidden="true"></span>
        <span class="tabular-nums">{{ text.number(books) }}</span>
      </span>
    }
  `,
  host: {class: 'flex items-center gap-2 text-xs text-text-secondary'},
})
export class StatsSizeKeyComponent {
  readonly most = input.required<number>();

  protected readonly text = injectStatsText('stats');
  protected readonly values = computed(() => {
    const most = this.most();
    const middle = [500, 200, 100, 50, 20, 10, 5, 2].find((value) => value <= most / 2);
    return [...new Set([1, ...(middle === undefined ? [] : [middle]), Math.max(1, most)])];
  });

  protected radius(books: number): number {
    return statsBubbleRadius(books, this.most());
  }
}
