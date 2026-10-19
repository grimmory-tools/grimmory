export const CBX_MIN_ZOOM = 1;
export const CBX_MAX_ZOOM = 5;
export const CBX_DOUBLE_TAP_ZOOM = 2.5;

export interface Point {
  x: number;
  y: number;
}

interface Rect {
  left: number;
  top: number;
  width: number;
  height: number;
}

interface ZoomTarget {
  img: HTMLImageElement;
  baseWidth: number;
  baseHeight: number;
}

type StyleTable = Record<string, string>;

// Zoom layout. Inline so it beats every fit-mode rule; cleared from the same tables (longhands,
// so each removal is exact).
// The container is start-aligned: a centered flex item that overflows is cut off at the top/left,
// while the wrapper's auto margins still center a page smaller than the screen. Fit width only
// allows vertical touch panning, so it is widened to both axes while zoomed.
const CONTAINER_STYLES: StyleTable = {
  'overflow': 'auto', 'justify-content': 'flex-start', 'align-items': 'flex-start', 'touch-action': 'pan-x pan-y',
};
const WRAPPER_STYLES: StyleTable = {
  'display': 'flex', 'flex-grow': '0', 'flex-shrink': '0', 'flex-basis': 'auto',
  'margin-top': 'auto', 'margin-right': 'auto', 'margin-bottom': 'auto', 'margin-left': 'auto',
  'width': 'max-content', 'height': 'max-content', 'min-width': '0', 'min-height': '0',
};
const LAYER_STYLES: StyleTable = {'width': 'max-content', 'height': 'max-content'};
const TARGET_STYLES: StyleTable = {'max-width': 'none', 'max-height': 'none', 'flex-grow': '0', 'flex-shrink': '0'};

function applyStyles(el: HTMLElement, styles: StyleTable): void {
  for (const [prop, value] of Object.entries(styles)) el.style.setProperty(prop, value);
}

function clearStyles(el: HTMLElement, styles: StyleTable): void {
  for (const prop of Object.keys(styles)) el.style.removeProperty(prop);
}

export function clampZoom(zoom: number): number {
  if (!Number.isFinite(zoom)) return CBX_MIN_ZOOM;
  return Math.min(CBX_MAX_ZOOM, Math.max(CBX_MIN_ZOOM, zoom));
}

export function toPoint(e: {clientX: number; clientY: number}): Point {
  return {x: e.clientX, y: e.clientY};
}

/** Where a viewport point sits inside a box, as a fraction of the box size. */
export function toAnchorFraction(viewport: Point, box: Rect): Point {
  return {
    x: box.width > 0 ? (viewport.x - box.left) / box.width : 0,
    y: box.height > 0 ? (viewport.y - box.top) / box.height : 0,
  };
}

/** Scroll adjustment that puts the anchored spot of a (resized) box back under a viewport point. */
export function anchorScrollDelta(fraction: Point, box: Rect, viewport: Point): Point {
  return {
    x: box.left + fraction.x * box.width - viewport.x,
    y: box.top + fraction.y * box.height - viewport.y,
  };
}

function unionRect(elements: HTMLElement[]): Rect | null {
  let left = Infinity, top = Infinity, right = -Infinity, bottom = -Infinity;
  for (const el of elements) {
    const r = el.getBoundingClientRect();
    if (r.width === 0 || r.height === 0) continue;
    left = Math.min(left, r.left);
    top = Math.min(top, r.top);
    right = Math.max(right, r.right);
    bottom = Math.max(bottom, r.bottom);
  }
  return left === Infinity ? null : {left, top, width: right - left, height: bottom - top};
}

/**
 * Transient zoom of the visible page(s) in the paginated CBX reader, inside the image scroll
 * container. It never changes the saved fit mode; callers reset it when the page, layout or
 * window width changes. Panning a zoomed page is the container's own scrolling.
 */
export class CbxPageZoom {
  private zoom = CBX_MIN_ZOOM;
  private targets: ZoomTarget[] = [];
  /** Scroll position at fit before zooming in, restored when zooming back out. */
  private fitScroll: Point | null = null;

  constructor(private readonly getContainer: () => HTMLElement | null) {}

  get level(): number {
    return this.zoom;
  }

  get isZoomed(): boolean {
    return this.zoom > CBX_MIN_ZOOM;
  }

  /** Zoom to an absolute level, keeping the page spot under the given viewport point fixed. */
  zoomTo(zoom: number, at: Point): void {
    const target = clampZoom(zoom);
    const container = this.getContainer();
    if (target === CBX_MIN_ZOOM) {
      // Back to where the reader was at fit (fit width / height can scroll); reset() goes to the top.
      const fitScroll = this.fitScroll;
      this.reset();
      if (container && fitScroll) {
        container.scrollLeft = fitScroll.x;
        container.scrollTop = fitScroll.y;
      }
      return;
    }
    if (!container) return;
    this.ensureTargets();
    const images = this.targets.map(t => t.img);
    const before = unionRect(images);
    if (!before) return;
    const fraction = toAnchorFraction(at, before);

    if (!this.isZoomed) {
      this.fitScroll = {x: container.scrollLeft, y: container.scrollTop};
      this.enterZoomLayout(container);
    }
    this.zoom = target;
    for (const t of this.targets) {
      t.img.style.width = `${t.baseWidth * target}px`;
      t.img.style.height = `${t.baseHeight * target}px`;
    }

    const after = unionRect(images);
    if (!after) return;
    const delta = anchorScrollDelta(fraction, after, at);
    container.scrollLeft += delta.x;
    container.scrollTop += delta.y;
  }

  reset(): void {
    for (const t of this.targets) {
      clearStyles(t.img, TARGET_STYLES);
      t.img.style.removeProperty('width');
      t.img.style.removeProperty('height');
    }
    this.targets = [];
    this.fitScroll = null;
    const container = this.getContainer();
    if (container && this.isZoomed) {
      clearStyles(container, CONTAINER_STYLES);
      const [wrapper, layer] = this.layoutElements(container);
      if (wrapper) clearStyles(wrapper, WRAPPER_STYLES);
      if (layer) clearStyles(layer, LAYER_STYLES);
      container.scrollLeft = 0;
      container.scrollTop = 0;
    }
    this.zoom = CBX_MIN_ZOOM;
  }

  /** Page images only; the split-page canvas renderer (no settings control) is not zoomed. */
  private ensureTargets(): void {
    if (this.targets.length > 0) return;
    const images = this.getContainer()?.querySelectorAll<HTMLImageElement>('.current-page-layer > img') ?? [];
    for (const img of Array.from(images)) {
      const r = img.getBoundingClientRect();
      if (r.width === 0 || r.height === 0) continue;
      this.targets.push({img, baseWidth: r.width, baseHeight: r.height});
    }
  }

  private enterZoomLayout(container: HTMLElement): void {
    const [wrapper, layer] = this.layoutElements(container);
    applyStyles(container, CONTAINER_STYLES);
    if (wrapper) applyStyles(wrapper, WRAPPER_STYLES);
    if (layer) applyStyles(layer, LAYER_STYLES);
    for (const t of this.targets) applyStyles(t.img, TARGET_STYLES);
  }

  private layoutElements(container: HTMLElement): (HTMLElement | null)[] {
    return [
      container.querySelector<HTMLElement>('.pages-wrapper'),
      container.querySelector<HTMLElement>('.current-page-layer'),
    ];
  }
}
