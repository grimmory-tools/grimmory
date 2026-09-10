import {
  Component,
  Directive,
  ElementRef,
  afterRenderEffect,
  computed,
  contentChildren,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import {TranslocoPipe} from '@jsverse/transloco';
import {LucideEllipsis, LucideX} from '@lucide/angular';

import {AppButtonComponent} from '../../ui/button/app-button.component';
import {AppMenuComponent} from '../../ui/menu/app-menu.component';
import {AppMenuTriggerDirective} from '../../ui/menu/app-menu-trigger.directive';
import {LayoutService} from '../../layout/layout.service';

const PILL_CHROME_WIDTH = 46;
const ITEM_GAP = 4;
const MORE_BUTTON_WIDTH = 40 + ITEM_GAP;

@Component({
  selector: 'app-browse-bulk-actions-divider',
  template: `@if (!mobileShell()) {
    <span class="mx-1.5 block h-6 w-px bg-border" aria-hidden="true"></span>
  }`,
  host: {class: 'contents'},
})
export class BrowseBulkActionsDividerComponent {
  private readonly layout = inject(LayoutService);
  protected readonly mobileShell = computed(() => !this.layout.isDesktop());
}

@Directive({
  selector: '[appBrowseBulkActionsItem]',
  host: {
    '[class.invisible]': 'overflowed()',
    '[class.absolute]': 'overflowed()',
    '[class.left-0]': 'overflowed()',
    '[class.top-0]': 'overflowed()',
    '[attr.inert]': "overflowed() ? '' : null",
  },
})
export class BrowseBulkActionsItemDirective {
  readonly id = input.required<string>({alias: 'appBrowseBulkActionsItem'});
  readonly element = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;

  private readonly bar = inject(BrowseBulkActionsBarComponent);
  protected readonly overflowed = computed(() => this.bar.overflowed().has(this.id()));
}

@Component({
  selector: 'app-browse-bulk-actions-bar',
  imports: [TranslocoPipe, AppButtonComponent, AppMenuTriggerDirective, BrowseBulkActionsDividerComponent, LucideEllipsis, LucideX],
  host: {class: 'contents'},
  template: `
    <div
      #strip
      class="pointer-events-none fixed inset-x-0 bottom-[max(1.25rem,env(safe-area-inset-bottom))] z-30 flex justify-center pl-[calc(var(--sidebar-width,0px)*(1-var(--mobile-shell-active,0)))]"
    >
      <div
        class="pointer-events-auto relative flex h-12 max-w-[calc(100%-2rem)] items-center gap-1 overflow-hidden whitespace-nowrap rounded-xl border border-border bg-card px-1.5 text-sm shadow-float animate-in fade-in-0 slide-in-from-bottom-1 motion-reduce:animate-none"
      >
      @if (!mobileShell()) {
        <span #leading class="flex items-center gap-1">
          <app-button
            variant="ghost"
            size="md"
            iconOnly
            [ariaLabel]="'shared.ui.select.clearSelection' | transloco"
            (clicked)="clearSelection.emit()"
          >
            <svg lucideX aria-hidden="true"></svg>
          </app-button>
          <span role="status" class="px-1 font-semibold tabular-nums text-text">
            {{ 'shared.ui.select.selectedCount' | transloco: {count: countLabel()} }}
          </span>
          @if (showSelectAll()) {
            <app-button
              variant="ghost"
              tone="primary"
              size="md"
              [label]="'shared.ui.bulkActions.selectAll' | transloco"
              (clicked)="selectAll.emit()"
            />
          }
          <app-browse-bulk-actions-divider />
        </span>
      }
      <ng-content />
      @if (moreMenu(); as menu) {
        @if (moreShown()) {
          <app-button
            variant="ghost"
            size="md"
            iconOnly
            [disabled]="moreDisabled()"
            [ariaLabel]="'browse.moreActions' | transloco"
            [appMenuTriggerFor]="menu"
          >
            <svg lucideEllipsis aria-hidden="true"></svg>
          </app-button>
        }
      }
      <ng-content select="[appBrowseBulkActionsTrailing]" />
      </div>
    </div>
  `,
})
export class BrowseBulkActionsBarComponent {
  readonly count = input.required<number>();
  readonly total = input<number | null>(null);
  readonly moreMenu = input<AppMenuComponent | null>(null);
  readonly moreAlways = input(false);
  readonly moreDisabled = input(false);

  readonly clearSelection = output<void>();
  readonly selectAll = output<void>();

  private readonly overflowedIds = signal<ReadonlySet<string>>(new Set(), {equal: sameIds});
  readonly overflowed = this.overflowedIds.asReadonly();

  private readonly layout = inject(LayoutService);
  private readonly strip = viewChild.required<ElementRef<HTMLElement>>('strip');
  private readonly leading = viewChild<ElementRef<HTMLElement>>('leading');
  private readonly items = contentChildren(BrowseBulkActionsItemDirective);
  protected readonly mobileShell = computed(() => !this.layout.isDesktop());
  protected readonly countLabel = computed(() => this.count().toLocaleString());
  protected readonly showSelectAll = computed(() => {
    const total = this.total();
    return total !== null && this.count() < total;
  });
  protected readonly moreShown = computed(() => this.moreAlways() || this.overflowed().size > 0);

  constructor() {
    afterRenderEffect(onCleanup => {
      const strip = this.strip().nativeElement;
      const leading = this.leading()?.nativeElement;
      const items = this.items().map(item => ({id: item.id(), element: item.element}));
      const moreAlways = this.moreAlways();
      let availableWidth = 0;
      const observer = new ResizeObserver(entries => {
        const stripEntry = entries.find(entry => entry.target === strip);
        if (stripEntry) availableWidth = stripEntry.contentRect.width;
        const capacity = availableWidth - PILL_CHROME_WIDTH - (leading ? leading.offsetWidth + ITEM_GAP : 0);
        const widths = items.map(item => ({id: item.id, width: item.element.offsetWidth + ITEM_GAP}));
        const withoutMoreButton = overflowingIds(widths, capacity);
        this.overflowedIds.set(moreAlways || withoutMoreButton.size > 0
          ? overflowingIds(widths, capacity - MORE_BUTTON_WIDTH)
          : withoutMoreButton);
      });
      observer.observe(strip);
      if (leading) observer.observe(leading);
      for (const item of items) observer.observe(item.element);
      onCleanup(() => observer.disconnect());
    });
  }
}

function overflowingIds(items: readonly {id: string; width: number}[], capacity: number): ReadonlySet<string> {
  const overflowed = new Set<string>();
  let used = 0;
  for (const item of items) {
    if (overflowed.size === 0 && used + item.width <= capacity) {
      used += item.width;
    } else {
      overflowed.add(item.id);
    }
  }
  return overflowed;
}

function sameIds(a: ReadonlySet<string>, b: ReadonlySet<string>): boolean {
  return a.size === b.size && [...a].every(id => b.has(id));
}
