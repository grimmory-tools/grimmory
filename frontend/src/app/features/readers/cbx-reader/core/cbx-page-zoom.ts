export const CBX_MIN_ZOOM = 1;
export const CBX_MAX_ZOOM = 5;
export const CBX_DOUBLE_TAP_ZOOM = 2.5;
/** Zoom levels this close to 1 snap back to fit. */
const ZOOM_SNAP_EPSILON = 0.05;

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

/** Fling friction per millisecond; velocity keeps this share each ms. */
const FLING_DECAY_PER_MS = 0.996;
/** Fling stops below this speed (px/ms). */
const FLING_MIN_SPEED = 0.02;
/** Only pointer movement this recent (ms) counts toward fling velocity. */
const FLING_SAMPLE_WINDOW_MS = 100;

/** Zoom factor for one Ctrl+wheel / trackpad-pinch event. */
export function wheelZoomFactor(deltaY: number): number {
  return Math.exp(-deltaY * 0.01);
}

/** Position of a viewport point inside the page content, in unzoomed (zoom = 1) units. */
export function toContentPoint(viewport: Point, content: Rect, zoom: number): Point {
  return {x: (viewport.x - content.left) / zoom, y: (viewport.y - content.top) / zoom};
}

/** Scroll adjustment that puts an unzoomed content point back under a viewport point. */
export function anchorScrollDelta(contentPoint: Point, content: Rect, zoom: number, viewport: Point): Point {
  return {
    x: content.left + contentPoint.x * zoom - viewport.x,
    y: content.top + contentPoint.y * zoom - viewport.y,
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
 * Zooms the paginated CBX page(s) by resizing the page elements inside the scroll container,
 * so native scrolling pans the zoomed page. Zoom is transient: callers reset it when the page,
 * layout or window size changes.
 */
export class CbxPageZoom {
  private zoom = CBX_MIN_ZOOM;
  private targets: ZoomTarget[] = [];
  private pinchStartDistance = 0;
  private pinchStartZoom = CBX_MIN_ZOOM;
  private pinchContentPoint: Point | null = null;
  /** True while the inline zoom layout is applied, independent of the current zoom level. */
  private layoutActive = false;
  private panLast: Point | null = null;
  private panSamples: {x: number; y: number; t: number}[] = [];
  private flingFrame: number | null = null;

  constructor(private readonly getContainer: () => HTMLElement | null) {}

  get level(): number {
    return this.zoom;
  }

  get isZoomed(): boolean {
    return this.zoom > CBX_MIN_ZOOM;
  }

  get isPinching(): boolean {
    return this.pinchContentPoint !== null;
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

  /** Start a scripted pan (one finger or mouse drag); callers check `canPan` or `isZoomed` first. */
  beginPan(at: Point): void {
    this.stopFling();
    this.panLast = at;
    this.panSamples = [{...at, t: performance.now()}];
  }

  updatePan(at: Point): void {
    const container = this.getContainer();
    if (!this.panLast || !container) return;
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
    if (!this.ensureTargets()) return false;
    const content = this.contentRect();
    if (!content) return false;
    this.pinchStartDistance = Math.max(1, touchDistance(a, b));
    this.pinchStartZoom = this.zoom;
    this.pinchContentPoint = toContentPoint(touchMidpoint(a, b), content, this.zoom);
    return true;
  }

  updatePinch(a: Point, b: Point): void {
    if (!this.pinchContentPoint) return;
    const zoom = clampZoom(this.pinchStartZoom * touchDistance(a, b) / this.pinchStartDistance);
    this.applyAnchored(zoom, this.pinchContentPoint, touchMidpoint(a, b));
  }

  endPinch(): void {
    if (!this.pinchContentPoint) return;
    this.pinchContentPoint = null;
    if (snapZoom(this.zoom) === CBX_MIN_ZOOM) this.reset();
  }

  /** Zoom to an absolute level, keeping the given viewport point fixed. */
  zoomTo(zoom: number, at: Point): void {
    if (!this.ensureTargets()) return;
    const content = this.contentRect();
    if (!content) return;
    const target = snapZoom(zoom);
    if (target === CBX_MIN_ZOOM) {
      this.reset();
      return;
    }
    this.applyAnchored(target, toContentPoint(at, content, this.zoom), at);
  }

  zoomBy(factor: number, at: Point): void {
    this.zoomTo(this.zoom * factor, at);
  }

  reset(): void {
    this.stopFling();
    this.pinchContentPoint = null;
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
      for (const el of this.layoutElements(container)) {
        for (const prop of ['width', 'height', 'min-width', 'min-height', 'margin', 'flex-grow', 'flex-shrink', 'flex-basis', 'display']) {
          el.style.removeProperty(prop);
        }
      }
      container.style.removeProperty('overflow');
      container.style.removeProperty('touch-action');
      container.scrollLeft = 0;
      container.scrollTop = 0;
    }
    this.layoutActive = false;
    this.zoom = CBX_MIN_ZOOM;
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

  private contentRect(): Rect | null {
    return unionRect(this.targets.map(t => t.measured));
  }

  private layoutElements(container: HTMLElement): HTMLElement[] {
    return ['.pages-wrapper', '.current-page-layer']
      .map(sel => container.querySelector<HTMLElement>(sel))
      .filter((el): el is HTMLElement => el !== null);
  }

  private applyAnchored(zoom: number, contentPoint: Point, viewport: Point): void {
    const container = this.getContainer();
    if (!container) return;
    // Pinching in at fit: nothing to do, and entering the zoom layout at 1x would drop fit sizing.
    if (zoom === CBX_MIN_ZOOM && !this.layoutActive) return;

    if (!this.layoutActive) this.enterZoomLayout(container);
    this.zoom = zoom;
    for (const t of this.targets) {
      t.sized.style.width = `${t.baseWidth * zoom}px`;
      t.sized.style.height = `${t.baseHeight * zoom}px`;
    }

    const content = this.contentRect();
    if (!content) return;
    const delta = anchorScrollDelta(contentPoint, content, zoom, viewport);
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

  /** Inline styles so the zoomed page overrides every fit-mode layout rule. */
  private enterZoomLayout(container: HTMLElement): void {
    this.layoutActive = true;
    container.style.overflow = 'auto';
    // Panning is handled in script while zoomed so a late second finger never races native scrolling.
    container.style.touchAction = 'none';
    const [wrapper, layer] = this.layoutElements(container);
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
}
