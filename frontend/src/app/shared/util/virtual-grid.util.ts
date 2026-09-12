import {DestroyRef, ElementRef, Signal, computed, effect, inject, signal} from '@angular/core';
import {runOnNextTwoFrames} from './frames';
import {
  injectVirtualizer,
  injectWindowVirtualizer,
  observeElementRect,
  type Rect,
  type VirtualItem,
} from '@tanstack/angular-virtual';

const DEFAULT_OVERSCAN_ROWS = 2;
const DEFAULT_ITEM_SIZE = 1;

export type VirtualGridScrollMode = 'element' | 'window';

export interface VirtualGridOptions<T> {
  items: Signal<readonly T[]>;
  itemKey?: (item: T, index: number) => VirtualItem['key'];
  scrollElement: Signal<ElementRef<HTMLElement> | undefined>;
  minItemWidth: Signal<number>;
  estimateItemHeight: (itemWidth: number) => number;
  gap: number | Signal<number>;
  rowGap?: Signal<number>;
  trailingRows?: Signal<number>;
  minimumCount?: (metrics: VirtualGridMetrics) => number;
  columns?: Signal<number | undefined>;
  initialOffset?: () => number;
  fillItemWidth?: boolean;
  scrollMode?: VirtualGridScrollMode;
  scrollMargin?: Signal<number>;
  measureElement?: ElementRef<HTMLElement>;
}

export interface VirtualGridMetrics {
  viewportWidth: number;
  viewportHeight: number;
  columns: number;
  itemHeight: number;
  gap: number;
}

function getScrollContentWidth(element: HTMLElement | null): number {
  if (!element) {
    return 0;
  }

  const style = getComputedStyle(element);
  const horizontalPadding = parseFloat(style.paddingLeft) + parseFloat(style.paddingRight);
  return Math.max(0, element.clientWidth - horizontalPadding);
}

function computeGridColumns(containerWidth: number, minColumnWidth: number, gap: number): number {
  if (containerWidth <= 0 || minColumnWidth <= 0) {
    return 1;
  }
  return toSafeInteger((containerWidth + gap) / (minColumnWidth + gap), 1);
}

function toSafeInteger(value: number | undefined, fallback = 0): number {
  return typeof value === 'number' && Number.isFinite(value) && value > 0 ? Math.floor(value) : fallback;
}

function toSafeSize(value: number | undefined, fallback: number): number {
  return typeof value === 'number' && Number.isFinite(value) && value > 0 ? value : fallback;
}

export function scaleForGridColumns(
  viewportWidth: number,
  gap: number,
  columns: number,
  baseWidth: number,
  minScale: number,
  maxScale: number
): number {
  const targetColumns = Math.max(1, Math.round(columns));
  // scaleForGridColumns uses +0.5 so computeGridColumns' floor settles on targetColumns.
  const targetWidth = ((viewportWidth + gap) / (targetColumns + 0.5)) - gap;
  const scale = targetWidth / baseWidth;
  return Math.min(maxScale, Math.max(minScale, scale));
}

/**
 * Creates a TanStack virtual grid inside Angular's injection context.
 *
 * Because this calls injectVirtualizer() and effect(), call it from a field
 * initializer, constructor, or runInInjectionContext(); lifecycle hooks such as
 * ngOnInit will throw NG0203 unless wrapped in runInInjectionContext().
 */
export function createVirtualGrid<T>(options: VirtualGridOptions<T>) {
  const destroyRef = inject(DestroyRef);
  const viewportWidth = signal(0);
  const viewportHeight = signal(0);
  const gap = computed(() => typeof options.gap === 'number' ? options.gap : options.gap());

  const setViewportSizeIfChanged = (width: number, height: number): void => {
    if (viewportWidth() === width && viewportHeight() === height) {
      return;
    }

    viewportWidth.set(width);
    viewportHeight.set(height);
  };

  const gridColumns = computed(() => {
    const columns = options.columns?.();
    const safeColumns = toSafeInteger(columns);
    if (safeColumns > 0) {
      return safeColumns;
    }

    return computeGridColumns(viewportWidth(), options.minItemWidth(), gap());
  });
  const itemWidth = computed(() => {
    if (!options.fillItemWidth) {
      return options.minItemWidth();
    }

    const columns = gridColumns();
    const availableWidth = viewportWidth();
    if (columns <= 0 || availableWidth <= 0) {
      return options.minItemWidth();
    }

    const totalGap = (columns - 1) * gap();
    return Math.max(options.minItemWidth(), (availableWidth - totalGap) / columns);
  });
  const itemHeight = computed(() => options.estimateItemHeight(itemWidth()));
  const columnGap = computed(() => {
    if (options.fillItemWidth) {
      return gap();
    }

    const columns = gridColumns();
    if (columns <= 1) {
      return gap();
    }

    const remainingWidth = viewportWidth() - (columns * itemWidth());
    return Math.max(gap(), remainingWidth / (columns - 1));
  });
  const rowGap = computed(() => {
    if (options.rowGap === undefined) {
      return columnGap();
    }
    return options.rowGap();
  });
  const minimumCount = computed(() => {
    const getMinimumCount = options.minimumCount;
    if (!getMinimumCount) {
      return 0;
    }

    return toSafeInteger(getMinimumCount({
      viewportWidth: viewportWidth(),
      viewportHeight: viewportHeight(),
      columns: gridColumns(),
      itemHeight: itemHeight(),
      gap: rowGap(),
    }));
  });

  const scrollMargin = computed(() => options.scrollMargin?.() ?? 0);

  const overscanRows = computed(() => {
    const rowHeight = itemHeight() + rowGap();
    if (rowHeight <= 0) {
      return DEFAULT_OVERSCAN_ROWS;
    }
    return Math.max(DEFAULT_OVERSCAN_ROWS, Math.ceil(viewportHeight() / rowHeight));
  });

  const sharedVirtualizerOptions = () => ({
    count: Math.max(
      options.items().length + (gridColumns() * (options.trailingRows?.() ?? 0)),
      minimumCount(),
    ),
    getItemKey: (index: number): VirtualItem['key'] => {
      const item = options.items()[index];
      return item === undefined
        ? `skeleton-${index}`
        : (options.itemKey?.(item, index) ?? index);
    },
    estimateSize: () => toSafeSize(itemHeight(), DEFAULT_ITEM_SIZE),
    overscan: toSafeInteger(gridColumns() * overscanRows(), DEFAULT_OVERSCAN_ROWS),
    gap: toSafeSize(rowGap(), DEFAULT_ITEM_SIZE),
    lanes: toSafeInteger(gridColumns(), 1),
    initialOffset: () => options.initialOffset?.() ?? 0,
  });

  const windowObserveElementRect = (
    instance: {scrollElement: Window | null},
    callback: (rect: Rect) => void,
  ): (() => void) => {
    const win = instance.scrollElement ?? window;
    let latestWidth = 0;
    let latestHeight = 0;
    const emit = (): void => setViewportSizeIfChanged(latestWidth, latestHeight);
    const measuredElement = options.measureElement?.nativeElement;

    const onWindowResize = (): void => {
      const width = win.innerWidth;
      const height = win.innerHeight;
      callback({width, height});
      latestHeight = Math.round(height);
      if (!measuredElement) {
        latestWidth = Math.round(width);
      }
      emit();
    };

    win.addEventListener('resize', onWindowResize, {passive: true});
    onWindowResize();

    let resizeObserver: ResizeObserver | undefined;
    if (measuredElement) {
      resizeObserver = new ResizeObserver(() => {
        latestWidth = Math.round(getScrollContentWidth(measuredElement));
        emit();
      });
      resizeObserver.observe(measuredElement);
    }

    return () => {
      win.removeEventListener('resize', onWindowResize);
      resizeObserver?.disconnect();
    };
  };

  const virtualizer = options.scrollMode === 'window'
    ? injectWindowVirtualizer<HTMLElement>(() => ({
        ...sharedVirtualizerOptions(),
        scrollMargin: scrollMargin(),
        observeElementRect: windowObserveElementRect,
      }))
    : injectVirtualizer<HTMLElement, HTMLElement>(() => ({
        scrollElement: options.scrollElement(),
        ...sharedVirtualizerOptions(),
        scrollMargin: scrollMargin(),
        observeElementRect: (instance, callback) => {
          const emitViewport = (rect: Rect): void => {
            const measured = options.measureElement?.nativeElement ?? instance.scrollElement;
            callback(rect);
            setViewportSizeIfChanged(Math.round(getScrollContentWidth(measured)), Math.round(rect.height));
          };

          const cleanup = observeElementRect(instance, emitViewport);

          let measureObserver: ResizeObserver | undefined;
          const measured = options.measureElement?.nativeElement;
          if (measured) {
            measureObserver = new ResizeObserver(() => {
              const scroller = instance.scrollElement;
              if (!scroller) return;
              const bounds = scroller.getBoundingClientRect();
              emitViewport({width: bounds.width, height: bounds.height});
            });
            measureObserver.observe(measured);
          }

          return () => {
            cleanup?.();
            measureObserver?.disconnect();
          };
        },
      }));

  // Reset measured sizes when geometry changes; otherwise density toggles flash.
  effect(() => {
    itemHeight();
    gridColumns();
    columnGap();
    rowGap();
    queueMicrotask(() => virtualizer.measure());
  });

  const updatePreservingScrollPosition = (update: () => void): void => {
    const scrollElement = options.scrollElement()?.nativeElement;
    if (!scrollElement) {
      update();
      return;
    }

    const scrollTop = scrollElement.scrollTop;
    const maxScrollTop = Math.max(0, scrollElement.scrollHeight - scrollElement.clientHeight);
    const scrollRatio = maxScrollTop > 0 ? scrollTop / maxScrollTop : 0;

    update();

    const restoreScrollPosition = (): void => {
      virtualizer.measure();
      const nextMaxScrollTop = Math.max(0, scrollElement.scrollHeight - scrollElement.clientHeight);
      virtualizer.scrollToOffset(nextMaxScrollTop * scrollRatio);
    };

    runOnNextTwoFrames(restoreScrollPosition, destroyRef);
  };

  return {
    viewportWidth,
    gridColumns,
    itemWidth,
    itemHeight,
    itemTransform: (item: VirtualItem) =>
      `translateX(${item.lane * (itemWidth() + columnGap())}px) translateY(${item.start - scrollMargin()}px)`,
    updatePreservingScrollPosition,
    virtualizer,
  };
}
