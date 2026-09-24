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

/** CSS custom property the strip layouts multiply their fit sizes by. */
export const CBX_ZOOM_CSS_VAR = '--cbx-zoom';

/**
 * `page`: the paginated view, zoomed by resizing the visible page elements.
 * `strip`: long strip / infinite scroll, zoomed by widening the strip column and scaling the
 * fit-mode size limits through {@link CBX_ZOOM_CSS_VAR}, so pages loaded later zoom too.
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

export function clampZoom(zoom: number): number {
  if (!Number.isFinite(zoom)) return CBX_MIN_ZOOM;
  return Math.min(CBX_MAX_ZOOM, Math.max(CBX_MIN_ZOOM, zoom));
}

export function snapZoom(zoom: number): number {
  const clamped = clampZoom(zoom);
  return clamped - CBX_MIN_ZOOM < ZOOM_SNAP_EPSILON ? CBX_MIN_ZOOM : clamped;
}

export function touchDistance(a: Point, b: Point): number {
  return Math.hypot(a.x - b.x, a.y - b.y);
}

export function touchMidpoint(a: Point, b: Point): Point {
  return {x: (a.x + b.x) / 2, y: (a.y + b.y) / 2};
}

/** Zoom factor for one Ctrl+wheel / trackpad-pinch event. */
export function wheelZoomFactor(deltaY: number): number {
  return Math.exp(-deltaY * 0.01);
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
 * Transient zoom for the CBX reader, applied inside the image scroll container so scrolling
 * pans the zoomed content. Callers reset it when the page, layout or window size changes.
 */
export class CbxPageZoom {
  private zoom = CBX_MIN_ZOOM;
  /** Layout the current zoom was applied with; fixed until reset. */
  private layout: CbxZoomLayout = 'page';
  private targets: ZoomTarget[] = [];
  /** Strip column width binding (e.g. `80%`) captured before zooming. */
  private stripBaseWidth: string | null = null;
  private pinchStartDistance = 0;
  private pinchStartZoom = CBX_MIN_ZOOM;
  private pinchAnchor: ZoomAnchor | null = null;
  /** True while the inline zoom layout is applied, independent of the current zoom level. */
  private layoutActive = false;
  private panLast: Point | null = null;
  /** False until the pan moved past the slop and confirmed it can scroll that way. */
  private panLocked = false;
  private panOrigin: Point | null = null;
  private panSamples: {x: number; y: number; t: number}[] = [];
  private flingFrame: number | null = null;

  constructor(
    private readonly getContainer: () => HTMLElement | null,
    private readonly getLayout: () => CbxZoomLayout = () => 'page',
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

  /** True when the page is zoomed or otherwise larger than the viewport. */
  get canPan(): boolean {
    if (this.isZoomed) return true;
    const container = this.getContainer();
    return !!container && (
      container.scrollWidth > container.clientWidth + 1 || container.scrollHeight > container.clientHeight + 1
    );
  }

  /**
   * Start a scripted pan (one finger or mouse drag); callers check `canPan` first.
   * The pan cancels itself if its first movement goes a way the content cannot scroll,
   * so a sideways swipe on a vertically scrolling page still reaches swipe navigation.
   */
  beginPan(at: Point): void {
    this.stopFling();
    this.panLast = at;
    this.panOrigin = at;
    this.panLocked = false;
    this.panSamples = [{...at, t: performance.now()}];
  }

  updatePan(at: Point): void {
    const container = this.getContainer();
    if (!this.panLast || !container) return;
    if (!this.panLocked) {
      const origin = this.panOrigin ?? at;
      const dx = at.x - origin.x;
      const dy = at.y - origin.y;
      if (Math.hypot(dx, dy) < PAN_SLOP_PX) return;
      const horizontal = Math.abs(dx) > Math.abs(dy);
      const hasRoom = horizontal
        ? container.scrollWidth > container.clientWidth + 1
        : container.scrollHeight > container.clientHeight + 1;
      if (!hasRoom) {
        this.panLast = null;
        this.panSamples = [];
        return;
      }
      this.panLocked = true;
    }
    container.scrollLeft -= at.x - this.panLast.x;
    container.scrollTop -= at.y - this.panLast.y;
    this.panLast = at;
    const now = performance.now();
    this.panSamples.push({...at, t: now});
    this.panSamples = this.panSamples.filter(s => now - s.t <= FLING_SAMPLE_WINDOW_MS);
  }

  /** End the pan; with `fling`, keep scrolling with decaying velocity. */
  endPan(fling = true): void {
    if (!this.panLast) return;
    this.panLast = null;
    const samples = this.panSamples;
    this.panSamples = [];
    if (!fling || samples.length < 2) return;

    const first = samples[0];
    const last = samples[samples.length - 1];
    const dt = performance.now() - first.t;
    if (dt <= 0 || performance.now() - last.t > FLING_SAMPLE_WINDOW_MS / 2) return;
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
      for (const prop of ['width', 'height', 'max-width', 'max-height', 'flex-grow', 'flex-shrink']) {
        t.sized.style.removeProperty(prop);
      }
    }
    this.targets = [];
    if (container && this.layoutActive) {
      if (this.layout === 'page') {
        this.resetPageLayout(container);
      } else {
        this.resetStripLayout(container);
      }
      container.style.removeProperty('overflow');
      container.style.removeProperty('touch-action');
    }
    this.stripBaseWidth = null;
    this.layoutActive = false;
    this.zoom = CBX_MIN_ZOOM;
  }

  private anchorAt(at: Point): ZoomAnchor | null {
    const layout = this.layoutActive ? this.layout : this.getLayout();
    const rect = layout === 'page' ? this.pageAnchorRect() : this.stripAnchorRect(at);
    const box = rect?.();
    if (!rect || !box) return null;
    this.layout = layout;
    return {rect, fraction: toAnchorFraction(at, box)};
  }

  /** Paginated: the visible page(s) together. */
  private pageAnchorRect(): (() => Rect | null) | null {
    if (!this.ensureTargets()) return null;
    return () => unionRect(this.targets.map(t => t.measured));
  }

  /** Strip: the page image under (or vertically nearest to) the point. */
  private stripAnchorRect(at: Point): (() => Rect | null) | null {
    const images = Array.from(this.getContainer()?.querySelectorAll<HTMLElement>('.strip-width-constrain img') ?? []);
    let best: HTMLElement | null = null;
    let bestDistance = Infinity;
    for (const img of images) {
      const r = img.getBoundingClientRect();
      if (r.height === 0) continue;
      const distance = at.y < r.top ? r.top - at.y : at.y > r.bottom ? at.y - r.bottom : 0;
      if (distance < bestDistance) {
        best = img;
        bestDistance = distance;
      }
    }
    if (!best) return null;
    const img = best;
    return () => img.isConnected ? img.getBoundingClientRect() : null;
  }

  private ensureTargets(): boolean {
    if (this.targets.length > 0) return true;
    const layer = this.getContainer()?.querySelector<HTMLElement>('.current-page-layer');
    if (!layer) return false;

    const targets: ZoomTarget[] = [];
    for (const child of Array.from(layer.children) as HTMLElement[]) {
      const measured = child.tagName === 'APP-CANVAS-RENDERER'
        ? child.querySelector<HTMLElement>('canvas')
        : child.tagName === 'IMG' ? child : null;
      if (!measured) continue;
      const r = measured.getBoundingClientRect();
      if (r.width === 0 || r.height === 0) continue;
      targets.push({sized: child, measured, baseWidth: r.width, baseHeight: r.height});
    }
    this.targets = targets;
    return targets.length > 0;
  }

  private applyAnchored(zoom: number, anchor: ZoomAnchor, viewport: Point): void {
    const container = this.getContainer();
    if (!container) return;
    // Pinching in at fit: nothing to do, and entering the zoom layout at 1x would drop fit sizing.
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
      const column = container.querySelector<HTMLElement>('.strip-width-constrain');
      if (column) column.style.width = `calc(${this.stripBaseWidth} * ${zoom})`;
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

  /** Inline styles so the zoomed content overrides every fit-mode layout rule. */
  private enterZoomLayout(container: HTMLElement): void {
    this.layoutActive = true;
    container.style.overflow = 'auto';
    // Panning is handled in script while zoomed so a late second finger never races native scrolling.
    container.style.touchAction = 'none';

    if (this.layout === 'strip') {
      const column = container.querySelector<HTMLElement>('.strip-width-constrain');
      this.stripBaseWidth = column?.style.width || '100%';
      column?.style.setProperty('max-width', 'none');
      return;
    }

    const [wrapper, layer] = this.pageLayoutElements(container);
    if (wrapper) {
      Object.assign(wrapper.style, {
        display: 'flex', flexGrow: '0', flexShrink: '0', flexBasis: 'auto', margin: 'auto',
        width: 'max-content', height: 'max-content', minWidth: '0', minHeight: '0',
      });
    }
    if (layer) {
      Object.assign(layer.style, {width: 'max-content', height: 'max-content'});
    }
    for (const t of this.targets) {
      Object.assign(t.sized.style, {maxWidth: 'none', maxHeight: 'none', flexGrow: '0', flexShrink: '0'});
    }
  }

  private pageLayoutElements(container: HTMLElement): HTMLElement[] {
    return ['.pages-wrapper', '.current-page-layer']
      .map(sel => container.querySelector<HTMLElement>(sel))
      .filter((el): el is HTMLElement => el !== null);
  }

  private resetPageLayout(container: HTMLElement): void {
    for (const el of this.pageLayoutElements(container)) {
      for (const prop of ['width', 'height', 'min-width', 'min-height', 'margin', 'flex-grow', 'flex-shrink', 'flex-basis', 'display']) {
        el.style.removeProperty(prop);
      }
    }
    container.scrollLeft = 0;
    container.scrollTop = 0;
  }

  /** Keeps the vertical reading position; only the horizontal pan goes back to the start. */
  private resetStripLayout(container: HTMLElement): void {
    container.style.removeProperty(CBX_ZOOM_CSS_VAR);
    const column = container.querySelector<HTMLElement>('.strip-width-constrain');
    if (column) {
      column.style.removeProperty('max-width');
      if (this.stripBaseWidth) column.style.width = this.stripBaseWidth;
    }
    container.scrollLeft = 0;
  }
}
