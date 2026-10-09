import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  ElementRef,
  inject,
  input,
  signal,
  viewChildren,
} from '@angular/core';
import {CdkScrollable} from '@angular/cdk/scrolling';
import {injectVirtualizer, injectWindowVirtualizer} from '@tanstack/angular-virtual';

import {type BookMenuComponent} from '../../../book/components/book-menu/book-menu.component';
import {LayoutService} from '../../../../shared/layout/layout.service';
import {type DashboardRow} from '../../dashboard-rows';
import {DashboardRowComponent} from '../dashboard-row/dashboard-row.component';

const ESTIMATED_ROW_HEIGHT = 380;

@Component({
  selector: 'app-dashboard-row-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DashboardRowComponent],
  host: {class: 'block [overflow-anchor:none]'},
  template: `
    <div class="relative w-full" [style.height.px]="virtualizer.getTotalSize()">
      <div class="absolute inset-x-0 top-0" [style.transform]="'translateY(' + offset() + 'px)'">
        @for (item of virtualizer.getVirtualItems(); track item.key) {
          <app-dashboard-row #rowItem [attr.data-index]="item.index" [row]="rows()[item.index]" [bookMenu]="bookMenu()" />
        }
      </div>
    </div>
  `,
})
export class DashboardRowListComponent {
  readonly rows = input.required<readonly DashboardRow[]>();
  readonly bookMenu = input.required<BookMenuComponent>();

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly scroller = inject(CdkScrollable).getElementRef().nativeElement;
  private readonly windowMode = !inject(LayoutService).isDesktop();
  private readonly scrollMargin = signal(0);
  private readonly rowItems = viewChildren<unknown, ElementRef<HTMLElement>>('rowItem', {read: ElementRef});

  protected readonly virtualizer = this.windowMode
    ? injectWindowVirtualizer<HTMLElement>(() => this.options())
    : injectVirtualizer<HTMLElement, HTMLElement>(() => ({scrollElement: this.scroller, ...this.options()}));
  protected readonly offset = computed(() =>
    (this.virtualizer.getVirtualItems()[0]?.start ?? 0) - this.scrollMargin());

  constructor() {
    afterNextRender(() => {
      const top = this.host.nativeElement.getBoundingClientRect().top;
      this.scrollMargin.set(this.windowMode
        ? top + window.scrollY
        : top - this.scroller.getBoundingClientRect().top + this.scroller.scrollTop);
    });

    effect(() => this.rowItems().forEach(item => this.virtualizer.measureElement(item.nativeElement)));
  }

  private options() {
    const rows = this.rows();
    return {
      count: rows.length,
      getItemKey: (index: number) => rows[index].id,
      estimateSize: () => ESTIMATED_ROW_HEIGHT,
      scrollMargin: this.scrollMargin(),
    };
  }
}
