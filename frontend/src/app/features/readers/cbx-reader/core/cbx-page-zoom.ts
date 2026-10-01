export const CBX_MIN_ZOOM = 1;
export const CBX_MAX_ZOOM = 5;
export const CBX_DOUBLE_TAP_ZOOM = 2.5;
/** Zoom levels this close to 1 snap back to fit. */
const ZOOM_SNAP_EPSILON = 0.05;

/** Fling friction per millisecond; velocity keeps this share each ms. */
const FLING_DECAY_PER_MS = 0.996;
/** Fling stops below this speed (px/ms). */
const FLING_MIN_SPEED = 0.02;
/** Only pointer movement this recent (ms) counts toward fling velocity. */
const FLING_SAMPLE_WINDOW_MS = 100;
/** Movement (px) before a pan picks its direction. */
const PAN_SLOP_PX = 6;
/** Browsers report DOM_DELTA_LINE wheel deltas in lines; roughly this many px each. */
const WHEEL_LINE_PX = 16;

/** CSS custom property the strip layouts multiply their fit sizes and column width by. */
export const CBX_ZOOM_CSS_VAR = '--cbx-zoom';

/**
 * page: resizes the visible page elements.
 * strip: sets {@link CBX_ZOOM_CSS_VAR}, which the strip CSS scales by, so lazily loaded pages zoom too.
 */
export type CbxZoomLayout = 'page' | 'strip';

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
  /** Element whose size is changed (an <img> or the canvas renderer host). */
  sized: HTMLElement;
  /** Element whose rendered box is the page (the <img> itself, or the <canvas>). */
  measured: HTMLElement;
  baseWidth: number;
  baseHeight: number;
}

/** A point fixed to the content while zooming: a box that scales, and a position inside it. */
interface ZoomAnchor {
  rect: () => Rect | null;
  fraction: Point;
}

type StyleTable = Record<string, string>;

// Paginated zoom layout. Inline so it beats every fit-mode rule; cleared from the same tables.
// Start-aligned container: a centered flex item that overflows is cut off at the top/left,
// while the wrapper's auto margins still center a page smaller than the screen.
const PAGE_CONTAINER_STYLES: StyleTable = {'justify-content': 'flex-start', 'align-items': 'flex-start'};
const PAGE_WRAPPER_STYLES: StyleTable = {
  'display': 'flex', 'flex-grow': '0', 'flex-shrink': '0', 'flex-basis': 'auto', 'margin': 'auto',
  'width': 'max-content', 'height': 'max-content', 'min-width': '0', 'min-height': '0',
};
const PAGE_LAYER_STYLES: StyleTable = {'width': 'max-content', 'height': 'max-content'};
const PAGE_TARGET_STYLES: StyleTable = {'max-width': 'none', 'max-height': 'none', 'flex-grow': '0', 'flex-shrink': '0'};

function applyStyles(el: HTMLElement, styles: StyleTable): void {
  for (const [prop, value] of Object.entries(styles)) el.style.setProperty(prop, value);
}

function clearStyles(el: HTMLElement, props: string[]): void {
  for (const prop of props) el.style.removeProperty(prop);
}

export function clampZoom(zoom: number): number {
  if (!Number.isFinite(zoom)) return CBX_MIN_ZOOM;
  return Math.min(CBX_MAX_ZOOM, Math.max(CBX_MIN_ZOOM, zoom));
}

export function snapZoom(zoom: number): number {
  const clamped = clampZoom(zoom);
  return clamped - CBX_MIN_ZOOM < ZOOM_SNAP_EPSILON ? CBX_MIN_ZOOM : clamped;
}

export function toPoint(e: {clientX: number; clientY: number}): Point {
  return {x: e.clientX, y: e.clientY};
}

export function touchDistance(a: Point, b: Point): number {
  return Math.hypot(a.x - b.x, a.y - b.y);
}

export function touchMidpoint(a: Point, b: Point): Point {
  return {x: (a.x + b.x) / 2, y: (a.y + b.y) / 2};
}

/** Zoom factor for one Ctrl+wheel / trackpad-pinch event. */
export function wheelZoomFactor(deltaY: number, deltaMode = 0): number {
  const px = deltaMode === 1 ? deltaY * WHEEL_LINE_PX : deltaY;
  return Math.exp(-px * 0.01);
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

function hasScrollRoom(container: HTMLElement, axis: 'x' | 'y'): boolean {
  return axis === 'x'
    ? container.scrollWidth > container.clientWidth + 1
    : container.scrollHeight > container.clientHeight + 1;
}

/**
 * Transient zoom and scripted panning for the CBX reader, inside the image scroll container.
 * Callers reset it when the page, layout or window size changes.
 */
export class CbxPageZoom {
  private zoom = CBX_MIN_ZOOM;
  /** Layout the current zoom was applied with; fixed until reset. */
  private layout: CbxZoomLayout = 'page';
  private targets: ZoomTarget[] = [];
  private pinchStartDistance = 0;
  private pinchStartZoom = CBX_MIN_ZOOM;
  private pinchAnchor: ZoomAnchor | null = null;
  /** Zoom styles applied; can be true at 1x mid-pinch. */
  private layoutActive = false;
  private panLast: Point | null = null;
  /** Set until the pan passes the slop and commits to a direction. */
  private panOrigin: Point | null = null;
  private panMovedPastSlop = false;
  private panSamples: {x: number; y: number; t: number}[] = [];
  private flingFrame: number | null = null;

  constructor(
    private readonly getContainer: () => HTMLElement | null,
    private readonly getLayout: () => CbxZoomLayout,
  ) {}

  get level(): number {
    return this.zoom;
  }

  get isZoomed(): boolean {
    return this.zoom > CBX_MIN_ZOOM;
  }

  get isPinching(): boolean {
    return this.pinchAnchor !== null;
  }

  get isPanning(): boolean {
    return this.panLast !== null;
  }

  /** Whether the last pan moved past the slop, even if it then let a swipe through. */
  get panMoved(): boolean {
    return this.panMovedPastSlop;
  }

  get canPan(): boolean {
    if (this.isZoomed) return true;
    const container = this.getContainer();
    return !!container && (hasScrollRoom(container, 'x') || hasScrollRoom(container, 'y'));
  }

  /** Callers check `canPan` first. */
  beginPan(at: Point): void {
    this.stopFling();
    this.panLast = at;
    this.panOrigin = at;
    this.panMovedPastSlop = false;
    this.panSamples = [{...at, t: performance.now()}];
  }

  updatePan(at: Point): void {
    const container = this.getContainer();
    if (!this.panLast || !container) return;
    if (this.panOrigin) {
      const dx = at.x - this.panOrigin.x;
      const dy = at.y - this.panOrigin.y;
      if (Math.hypot(dx, dy) < PAN_SLOP_PX) return;
      this.panMovedPastSlop = true;
      this.panOrigin = null;
      // Can't scroll the way it started: step aside so a sideways swipe can still turn the page.
      if (!hasScrollRoom(container, Math.abs(dx) > Math.abs(dy) ? 'x' : 'y')) {
        this.panLast = null;
        this.panSamples = [];
        return;
      }
    }
    container.scrollLeft -= at.x - this.panLast.x;
    container.scrollTop -= at.y - this.panLast.y;
    this.panLast = at;
    const now = performance.now();
    this.panSamples.push({...at, t: now});
    this.panSamples = this.panSamples.filter(s => now - s.t <= FLING_SAMPLE_WINDOW_MS);
  }

  /** With `fling`, keeps scrolling with decaying velocity. */
  endPan(fling = true): void {
    if (!this.panLast) return;
    this.panLast = null;
    const samples = this.panSamples;
    this.panSamples = [];
    if (!fling || samples.length < 2) return;

    const now = performance.now();
    const first = samples[0];
    const last = samples[samples.length - 1];
    const dt = now - first.t;
    if (dt <= 0 || now - last.t > FLING_SAMPLE_WINDOW_MS / 2) return;
    this.startFling((first.x - last.x) / dt, (first.y - last.y) / dt);
  }

  stopFling(): void {
    if (this.flingFrame !== null) {
      cancelAnimationFrame(this.flingFrame);
      this.flingFrame = null;
    }
  }

  beginPinch(a: Point, b: Point): boolean {
    this.stopFling();
    this.endPan(false);
    const anchor = this.anchorAt(touchMidpoint(a, b));
    if (!anchor) return false;
    this.pinchStartDistance = Math.max(1, touchDistance(a, b));
    this.pinchStartZoom = this.zoom;
    this.pinchAnchor = anchor;
    return true;
  }

  updatePinch(a: Point, b: Point): void {
    if (!this.pinchAnchor) return;
    const zoom = clampZoom(this.pinchStartZoom * touchDistance(a, b) / this.pinchStartDistance);
    this.applyAnchored(zoom, this.pinchAnchor, touchMidpoint(a, b));
  }

  endPinch(): void {
    if (!this.pinchAnchor) return;
    this.pinchAnchor = null;
    if (snapZoom(this.zoom) === CBX_MIN_ZOOM) this.reset();
  }

  /** Zoom to an absolute level, keeping the given viewport point fixed. */
  zoomTo(zoom: number, at: Point): void {
    const anchor = this.anchorAt(at);
    if (!anchor) return;
    const target = snapZoom(zoom);
    // Zooming back to fit goes through 1x anchored first, so strips keep their scroll position.
    this.applyAnchored(target, anchor, at);
    if (target === CBX_MIN_ZOOM) this.reset();
  }

  zoomBy(factor: number, at: Point): void {
    this.zoomTo(this.zoom * factor, at);
  }

  reset(): void {
    this.stopFling();
    this.pinchAnchor = null;
    this.panLast = null;
    this.panSamples = [];
    const container = this.getContainer();
    for (const t of this.targets) {
      clearStyles(t.sized, ['width', 'height', ...Object.keys(PAGE_TARGET_STYLES)]);
    }
    this.targets = [];
    if (container && this.layoutActive) {
      container.style.removeProperty('overflow');
      container.scrollLeft = 0;
      if (this.layout === 'page') {
        const [wrapper, layer] = this.pageLayoutElements(container);
        clearStyles(container, Object.keys(PAGE_CONTAINER_STYLES));
        if (wrapper) clearStyles(wrapper, Object.keys(PAGE_WRAPPER_STYLES));
        if (layer) clearStyles(layer, Object.keys(PAGE_LAYER_STYLES));
        container.scrollTop = 0;
      } else {
        // Strips keep their vertical reading position.
        container.style.removeProperty(CBX_ZOOM_CSS_VAR);
      }
    }
    this.layoutActive = false;
    this.zoom = CBX_MIN_ZOOM;
  }

  private anchorAt(at: Point): ZoomAnchor | null {
    const layout = this.layoutActive ? this.layout : this.getLayout();
    const rect = layout === 'page' ? this.pageAnchorRect() : this.stripAnchorRect(at);
    const box = rect();
    if (!box) return null;
    this.layout = layout;
    return {rect, fraction: toAnchorFraction(at, box)};
  }

  /** Paginated: the visible page(s) together. */
  private pageAnchorRect(): () => Rect | null {
    this.ensureTargets();
    const measured = this.targets.map(t => t.measured);
    return () => unionRect(measured);
  }

  /** Strip: the page image under (or vertically nearest to) the point. */
  private stripAnchorRect(at: Point): () => Rect | null {
    const images = this.getContainer()?.querySelectorAll<HTMLElement>('.strip-width-constrain img') ?? [];
    let best: HTMLElement | null = null;
    let bestDistance = Infinity;
    for (const img of Array.from(images)) {
      const r = img.getBoundingClientRect();
      if (r.height === 0) continue;
      const distance = Math.max(0, r.top - at.y, at.y - r.bottom);
      if (distance < bestDistance) {
        best = img;
        bestDistance = distance;
      }
    }
    const img = best;
    return () => img?.isConnected ? img.getBoundingClientRect() : null;
  }

  private ensureTargets(): void {
    if (this.targets.length > 0) return;
    const layer = this.getContainer()?.querySelector<HTMLElement>('.current-page-layer');
    for (const child of Array.from(layer?.children ?? []) as HTMLElement[]) {
      const measured = child.tagName === 'APP-CANVAS-RENDERER'
        ? child.querySelector<HTMLElement>('canvas')
        : child.tagName === 'IMG' ? child : null;
      if (!measured) continue;
      const r = measured.getBoundingClientRect();
      if (r.width === 0 || r.height === 0) continue;
      this.targets.push({sized: child, measured, baseWidth: r.width, baseHeight: r.height});
    }
  }

  private applyAnchored(zoom: number, anchor: ZoomAnchor, viewport: Point): void {
    const container = this.getContainer();
    if (!container) return;
    // At fit with no zoom layout yet: entering it at 1x would drop the fit sizing.
    if (zoom === CBX_MIN_ZOOM && !this.layoutActive) return;

    if (!this.layoutActive) this.enterZoomLayout(container);
    this.zoom = zoom;
    if (this.layout === 'page') {
      for (const t of this.targets) {
        t.sized.style.width = `${t.baseWidth * zoom}px`;
        t.sized.style.height = `${t.baseHeight * zoom}px`;
      }
    } else {
      container.style.setProperty(CBX_ZOOM_CSS_VAR, String(zoom));
    }

    const box = anchor.rect();
    if (!box) return;
    const delta = anchorScrollDelta(anchor.fraction, box, viewport);
    container.scrollLeft += delta.x;
    container.scrollTop += delta.y;
  }

  private startFling(vx: number, vy: number): void {
    const container = this.getContainer();
    if (!container) return;
    let last = performance.now();
    const step = (now: number) => {
      const dt = now - last;
      last = now;
      const decay = Math.pow(FLING_DECAY_PER_MS, dt);
      vx *= decay;
      vy *= decay;
      if (Math.hypot(vx, vy) < FLING_MIN_SPEED) {
        this.flingFrame = null;
        return;
      }
      container.scrollLeft += vx * dt;
      container.scrollTop += vy * dt;
      this.flingFrame = requestAnimationFrame(step);
    };
    this.flingFrame = requestAnimationFrame(step);
  }

  private enterZoomLayout(container: HTMLElement): void {
    this.layoutActive = true;
    container.style.overflow = 'auto';
    if (this.layout === 'strip') return;

    const [wrapper, layer] = this.pageLayoutElements(container);
    applyStyles(container, PAGE_CONTAINER_STYLES);
    if (wrapper) applyStyles(wrapper, PAGE_WRAPPER_STYLES);
    if (layer) applyStyles(layer, PAGE_LAYER_STYLES);
    for (const t of this.targets) applyStyles(t.sized, PAGE_TARGET_STYLES);
  }

  private pageLayoutElements(container: HTMLElement): (HTMLElement | null)[] {
    return [
      container.querySelector<HTMLElement>('.pages-wrapper'),
      container.querySelector<HTMLElement>('.current-page-layer'),
    ];
  }
}
