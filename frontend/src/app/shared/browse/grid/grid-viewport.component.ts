import {
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  OnInit,
  TemplateRef,
  computed,
  effect,
  inject,
  input,
  runInInjectionContext,
  signal,
  untracked,
} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {type VirtualItem} from '@tanstack/angular-virtual';

import {RouteScrollPositionService} from '../../service/route-scroll-position.service';
import {runOnNextTwoFrames} from '../../util/frames';
import {type GridDensityGrid} from '../../util/grid-density.util';
import {createVirtualGrid, type VirtualGridScrollMode} from '../../util/virtual-grid.util';
import {sameRenderedRange, type BrowseRenderedRange} from '../results';
import {type BrowseGridItemContext} from './grid.directives';
import {BrowseGridItemComponent, type BrowseGridItemState} from './grid-item.component';

type VirtualGrid = ReturnType<typeof createVirtualGrid>;

const MIN_ADMISSIONS_PER_FRAME = 3;

@Component({
  selector: 'app-browse-grid-viewport',
  imports: [BrowseGridItemComponent],
  host: {class: 'block w-full min-w-0'},
  template: `
    @if (grid(); as g) {
      <div class="relative w-full" [style.height.px]="g.virtualizer.getTotalSize()">
        @for (item of g.virtualizer.getVirtualItems(); track item.key) {
          <app-browse-grid-item
            [data]="items()[item.index]"
            [index]="item.index"
            [width]="g.itemWidth()"
            [transform]="g.itemTransform(item)"
            [state]="itemState(item)"
            [itemTemplate]="itemTemplate()"
            [skeletonTemplate]="skeletonTemplate()" />
        }
      </div>
    }
  `,
})
export class BrowseGridViewportComponent<T> implements OnInit {
  private readonly hostRef = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly destroyRef = inject(DestroyRef);
  private readonly route = inject(ActivatedRoute);
  private readonly scrollPosition = inject(RouteScrollPositionService);

  readonly items = input.required<readonly T[]>();
  readonly itemKey = input.required<(item: T) => VirtualItem['key']>();
  readonly hasNextPage = input.required<boolean>();
  readonly fixedColumns = input.required<number | undefined>();
  readonly minItemWidth = input.required<number>();
  readonly gap = input.required<number>();
  readonly rowGap = input.required<number | undefined>();
  readonly estimateItemHeight = input.required<(itemWidth: number) => number>();
  readonly itemTemplate = input.required<TemplateRef<BrowseGridItemContext<T>>>();
  readonly skeletonTemplate = input.required<TemplateRef<unknown>>();
  readonly skeletonFill = input.required<boolean>();
  readonly scrollMode = input.required<VirtualGridScrollMode>();
  readonly scrollHost = input.required<ElementRef<HTMLElement>>();

  readonly renderedRange = signal<BrowseRenderedRange | null>(null, {equal: sameRenderedRange});

  protected readonly grid = signal<VirtualGrid | null>(null);
  private readonly scrollElementRef = signal<ElementRef<HTMLElement> | undefined>(undefined);
  private readonly scrollMargin = signal(0);
  private readonly initialScrollOffset = () =>
    this.scrollPosition.getPosition(this.scrollPosition.keyFor(this.route, 'grid')) ?? 0;
  private scrollRestored = false;

  private readonly mountedKeys = signal<ReadonlySet<VirtualItem['key']>>(new Set());
  private admissionFrame: number | null = null;

  constructor() {
    this.scrollPosition.trackRoute({
      scrollElement: this.scrollElementRef,
      route: this.route,
      destroyRef: this.destroyRef,
      keySuffix: 'grid',
    });
    this.destroyRef.onDestroy(() => {
      if (this.admissionFrame !== null) {
        cancelAnimationFrame(this.admissionFrame);
      }
    });

    effect(() => {
      const g = this.grid();
      if (!g || this.scrollRestored || this.skeletonFill() || this.items().length === 0) {
        return;
      }
      this.scrollRestored = true;
      const offset = this.initialScrollOffset();
      if (offset > 0) {
        runOnNextTwoFrames(() => g.virtualizer.scrollToOffset(offset));
      }
    });

    effect(() => {
      const g = this.grid();
      if (!g || this.skeletonFill()) {
        return;
      }
      const virtualItems = g.virtualizer.getVirtualItems();
      if (virtualItems.length > 0 && this.items().length > 0) {
        this.renderedRange.set({
          start: virtualItems[0].index,
          end: virtualItems[virtualItems.length - 1].index,
        });
      }
      untracked(() => this.admitItems(g, virtualItems));
    });
  }

  ngOnInit(): void {
    if (this.scrollMode() === 'window') {
      this.enterWindowMode();
    } else {
      this.enterElementMode();
    }

    runInInjectionContext(this.injector, () => {
      this.grid.set(
        createVirtualGrid({
          items: this.items,
          itemKey: item => this.itemKey()(item),
          scrollElement: this.scrollElementRef,
          minItemWidth: this.minItemWidth,
          gap: this.gap,
          rowGap: computed(() => this.rowGap() ?? this.gap()),
          estimateItemHeight: width => this.estimateItemHeight()(width),
          trailingRows: computed(() => this.hasNextPage() ? 1 : 0),
          columns: this.fixedColumns,
          fillItemWidth: true,
          scrollMode: this.scrollMode(),
          scrollMargin: this.scrollMargin,
          measureElement: this.hostRef,
          minimumCount: metrics =>
            this.skeletonFill()
              ? skeletonFillCount(metrics.viewportHeight, metrics.columns, metrics.itemHeight, metrics.gap)
              : 0,
          initialOffset: this.initialScrollOffset,
        }),
      );
    });
  }

  scrollToTop(): void {
    if (this.scrollMode() === 'window') {
      window.scrollTo({top: 0});
      return;
    }
    const scroller = this.scrollElementRef()?.nativeElement;
    if (scroller) {
      scroller.scrollTop = 0;
    }
  }

  densityGrid(): GridDensityGrid | null {
    return this.grid();
  }

  protected itemState(item: VirtualItem): BrowseGridItemState {
    if (this.skeletonFill() || this.items()[item.index] === undefined) {
      return 'skeleton';
    }
    return this.mountedKeys().has(item.key) ? 'mounted' : 'pending';
  }

  private enterElementMode(): void {
    this.scrollElementRef.set(this.scrollHost());
  }

  private enterWindowMode(): void {
    const previousRestoration = history.scrollRestoration;
    history.scrollRestoration = 'manual';
    const scroller = document.scrollingElement instanceof HTMLElement
      ? document.scrollingElement
      : document.documentElement;
    this.scrollElementRef.set(new ElementRef(scroller));
    this.destroyRef.onDestroy(() => {
      history.scrollRestoration = previousRestoration;
    });
    this.trackScrollMargin(() => this.hostRef.nativeElement.getBoundingClientRect().top + window.scrollY);
  }

  private trackScrollMargin(measure: () => number): void {
    const measureMargin = (): void => {
      this.scrollMargin.set(Math.max(0, Math.round(measure())));
    };
    measureMargin();

    window.addEventListener('resize', measureMargin, {passive: true});

    const target = this.hostRef.nativeElement.closest<HTMLElement>('.app-page') ?? this.hostRef.nativeElement;
    const marginObserver = new ResizeObserver(measureMargin);
    marginObserver.observe(target);

    this.destroyRef.onDestroy(() => {
      window.removeEventListener('resize', measureMargin);
      marginObserver.disconnect();
    });
  }

  private admitItems(g: VirtualGrid, virtualItems: readonly VirtualItem[]): void {
    const items = this.items();
    const {scrollOffset, viewportEnd} = this.viewportBounds(g);
    const mounted = this.mountedKeys();
    const next = new Set<VirtualItem['key']>();
    let added = 0;
    let pending = false;
    for (const item of virtualItems) {
      if (mounted.has(item.key)) {
        next.add(item.key);
      } else if (items[item.index] !== undefined) {
        if (item.start < viewportEnd && item.end > scrollOffset) {
          next.add(item.key);
          added++;
        } else {
          pending = true;
        }
      }
    }
    if (pending) {
      this.scheduleOverscanAdmission(g);
    }
    if (added > 0 || next.size !== mounted.size) {
      this.mountedKeys.set(next);
    }
  }

  private scheduleOverscanAdmission(g: VirtualGrid): void {
    if (this.admissionFrame !== null) {
      return;
    }
    this.admissionFrame = requestAnimationFrame(() => {
      this.admissionFrame = null;
      const items = this.items();
      const {scrollOffset, viewportEnd} = this.viewportBounds(g);
      const distance = (item: VirtualItem) =>
        item.end <= scrollOffset ? scrollOffset - item.end : item.start - viewportEnd;
      const mounted = new Set(this.mountedKeys());
      const pending = g.virtualizer.getVirtualItems()
        .filter(item => !mounted.has(item.key) && items[item.index] !== undefined)
        .sort((a, b) => distance(a) - distance(b));
      if (pending.length === 0) {
        return;
      }
      const perFrame = Math.max(MIN_ADMISSIONS_PER_FRAME, Math.ceil(g.gridColumns() / 2));
      for (const item of pending.slice(0, perFrame)) {
        mounted.add(item.key);
      }
      if (pending.length > perFrame) {
        this.scheduleOverscanAdmission(g);
      }
      this.mountedKeys.set(mounted);
    });
  }

  private viewportBounds(g: VirtualGrid) {
    const scrollOffset = g.virtualizer.scrollOffset() ?? 0;
    return {scrollOffset, viewportEnd: scrollOffset + (g.virtualizer.scrollRect()?.height ?? 0)};
  }
}

const SKELETON_OVERSCAN_ROWS = 2;

function skeletonFillCount(
  viewportHeight: number,
  columns: number,
  itemHeight: number,
  gap: number,
): number {
  const safeColumns = Math.max(1, Math.floor(columns));
  const rowStride = itemHeight + gap;
  const visibleRows = Math.ceil(viewportHeight / rowStride);
  const rows = Math.max(1, visibleRows + SKELETON_OVERSCAN_ROWS);
  return safeColumns * rows;
}
