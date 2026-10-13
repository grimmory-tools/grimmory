import {Component, inject, input} from '@angular/core';

import {STATS_CARD} from '../../card/stats-context';

export interface StatsInsight {
  readonly label: string;
  readonly value: string;
  readonly detail?: string;
}

@Component({
  selector: 'app-stats-insight-list',
  template: `
    @if (items().length > 0 && card.state() !== 'empty') {
      <dl class="grid grid-cols-[repeat(auto-fit,minmax(calc((100%_-_1.25rem)/2),1fr))] gap-x-5 gap-y-4 border-t border-border px-2 pt-4 @lg:grid-cols-[repeat(auto-fit,minmax(calc((100%_-_3.75rem)/4),1fr))]">
        @for (item of items(); track item.label) {
          <div class="min-w-0">
            <dt class="text-xs font-medium text-text-secondary">{{ item.label }}</dt>
            <dd class="mt-1 truncate text-lg font-semibold tabular-nums text-text-strong" [title]="item.value">
              @if (card.state() === 'loading') {
                <span class="inline-block h-5 w-14 animate-pulse rounded bg-text/8 align-middle motion-reduce:animate-none"></span>
              } @else {
                {{ item.value }}
              }
            </dd>
            @if (item.detail !== undefined) {
              <dd class="line-clamp-4 min-h-4 text-xs tabular-nums text-text-muted" [title]="item.detail">{{ item.detail }}</dd>
            }
          </div>
        }
      </dl>
    }
  `,
})
export class StatsInsightListComponent {
  protected readonly card = inject(STATS_CARD);

  readonly items = input.required<readonly StatsInsight[]>();
}
