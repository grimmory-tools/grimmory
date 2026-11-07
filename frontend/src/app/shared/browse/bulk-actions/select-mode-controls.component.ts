import {Component, computed, input, output} from '@angular/core';
import {TranslocoPipe} from '@jsverse/transloco';

import {AppButtonComponent} from '../../ui/button/app-button.component';

@Component({
  selector: 'app-browse-select-mode-controls',
  imports: [TranslocoPipe, AppButtonComponent],
  host: {class: 'contents'},
  template: `
    <span role="status" class="px-1 text-sm font-semibold tabular-nums text-text">
      {{ 'shared.ui.select.selectedCount' | transloco: {count: countLabel()} }}
    </span>
    @if (showSelectAll()) {
      <app-button
        class="ml-auto"
        variant="soft"
        [label]="'shared.ui.bulkActions.selectAll' | transloco"
        (clicked)="selectAll.emit()" />
    }
    <app-button
      [class]="showSelectAll() ? '' : 'ml-auto'"
      variant="soft"
      [label]="'common.cancel' | transloco"
      (clicked)="cancelled.emit()" />
  `,
})
export class BrowseSelectModeControlsComponent {
  readonly count = input.required<number>();
  readonly total = input<number | null>(null);

  readonly selectAll = output<void>();
  readonly cancelled = output<void>();

  protected readonly countLabel = computed(() => this.count().toLocaleString());
  protected readonly showSelectAll = computed(() => {
    const total = this.total();
    return total !== null && this.count() < total;
  });
}
