import {NgTemplateOutlet} from '@angular/common';
import {Component, TemplateRef, computed, input} from '@angular/core';

import {type BrowseGridItemContext} from './grid.directives';

export type BrowseGridItemState = 'skeleton' | 'pending' | 'mounted';

@Component({
  selector: 'app-browse-grid-item',
  imports: [NgTemplateOutlet],
  host: {
    class: 'absolute left-0 top-0',
    '[style.width.px]': 'width()',
    '[style.--card-width.px]': 'width()',
    '[style.transform]': 'transform()',
  },
  template: `
    @switch (state()) {
      @case ('mounted') {
        @if (context(); as context) {
          <ng-container [ngTemplateOutlet]="itemTemplate()" [ngTemplateOutletContext]="context" />
        }
      }
      @case ('skeleton') {
        <ng-container [ngTemplateOutlet]="skeletonTemplate()" />
      }
    }
  `,
})
export class BrowseGridItemComponent<T> {
  readonly data = input.required<T | undefined>();
  readonly index = input.required<number>();
  readonly width = input.required<number>();
  readonly transform = input.required<string>();
  readonly state = input.required<BrowseGridItemState>();
  readonly itemTemplate = input.required<TemplateRef<BrowseGridItemContext<T>>>();
  readonly skeletonTemplate = input.required<TemplateRef<unknown>>();

  protected readonly context = computed<BrowseGridItemContext<T> | null>(() => {
    const data = this.data();
    return data === undefined ? null : {$implicit: data, index: this.index()};
  });
}
