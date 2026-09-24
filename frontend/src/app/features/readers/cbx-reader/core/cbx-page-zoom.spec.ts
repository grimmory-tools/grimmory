import {afterEach, describe, expect, it, vi} from 'vitest';
import {
  anchorScrollDelta,
  CbxPageZoom,
  CBX_MAX_ZOOM,
  CBX_MIN_ZOOM,
  clampZoom,
  snapZoom,
  toContentPoint,
  touchDistance,
  touchMidpoint,
  wheelZoomFactor
} from './cbx-page-zoom';

describe('cbx page zoom math', () => {
  it('clamps zoom to the supported range', () => {
    expect(clampZoom(0.5)).toBe(CBX_MIN_ZOOM);
    expect(clampZoom(12)).toBe(CBX_MAX_ZOOM);
    expect(clampZoom(2.2)).toBe(2.2);
    expect(clampZoom(NaN)).toBe(CBX_MIN_ZOOM);
  });

  it('snaps near-fit zoom back to fit', () => {
    expect(snapZoom(1.03)).toBe(CBX_MIN_ZOOM);
    expect(snapZoom(0.7)).toBe(CBX_MIN_ZOOM);
    expect(snapZoom(1.2)).toBe(1.2);
  });

  it('measures pinch distance and midpoint', () => {
    expect(touchDistance({x: 0, y: 0}, {x: 3, y: 4})).toBe(5);
    expect(touchMidpoint({x: 10, y: 20}, {x: 30, y: 60})).toEqual({x: 20, y: 40});
  });

  it('zooms in for wheel up and out for wheel down', () => {
    expect(wheelZoomFactor(-100)).toBeGreaterThan(1);
    expect(wheelZoomFactor(100)).toBeLessThan(1);
    expect(wheelZoomFactor(0)).toBe(1);
  });

  it('keeps the anchored content point under the pinch midpoint after zooming', () => {
    const before = {left: 100, top: 50, width: 400, height: 600};
    const midpoint = {x: 300, y: 350};
    const contentPoint = toContentPoint(midpoint, before, 1);
    expect(contentPoint).toEqual({x: 200, y: 300});

    // Page doubled in size and was laid out starting at the same place, before any scroll correction.
    const after = {left: 100, top: 50, width: 800, height: 1200};
    const delta = anchorScrollDelta(contentPoint, after, 2, midpoint);
    expect(delta).toEqual({x: 200, y: 300});

    // Scrolling by the delta moves the content back so the point sits under the midpoint again.
    const scrolled = {...after, left: after.left - delta.x, top: after.top - delta.y};
    expect(toContentPoint(midpoint, scrolled, 2)).toEqual(contentPoint);
  });

  it('follows the midpoint when two fingers pan while pinching', () => {
    const content = {left: 0, top: 0, width: 1000, height: 1500};
    const delta = anchorScrollDelta({x: 100, y: 100}, content, 2, {x: 150, y: 120});
    expect(delta).toEqual({x: 50, y: 80});
  });
});

describe('CbxPageZoom', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    document.body.innerHTML = '';
  });

  function setup(): {container: HTMLElement; img: HTMLImageElement; zoom: CbxPageZoom} {
    document.body.innerHTML = `
      <div class="image-container">
        <div class="pages-wrapper"><div class="current-page-layer"><img class="page-image"></div></div>
      </div>`;
    const container = document.querySelector<HTMLElement>('.image-container')!;
    const img = document.querySelector<HTMLImageElement>('img')!;
    vi.spyOn(img, 'getBoundingClientRect').mockReturnValue(
      {left: 0, top: 0, right: 400, bottom: 600, width: 400, height: 600, x: 0, y: 0, toJSON: () => ({})} as DOMRect
    );
    return {container, img, zoom: new CbxPageZoom(() => container)};
  }

  it('leaves the fit layout untouched when pinching in at fit', () => {
    const {container, img, zoom} = setup();
    expect(zoom.beginPinch({x: 100, y: 300}, {x: 300, y: 300})).toBe(true);
    zoom.updatePinch({x: 150, y: 300}, {x: 250, y: 300});
    zoom.endPinch();

    expect(zoom.level).toBe(1);
    expect(img.getAttribute('style')).toBeFalsy();
    expect(container.getAttribute('style')).toBeFalsy();
    expect(container.querySelector<HTMLElement>('.pages-wrapper')!.getAttribute('style')).toBeFalsy();
  });

  it('can pan only when zoomed or when the page overflows the viewport', () => {
    const {container, zoom} = setup();
    expect(zoom.canPan).toBe(false);

    zoom.zoomTo(2, {x: 200, y: 300});
    expect(zoom.canPan).toBe(true);

    zoom.reset();
    vi.spyOn(container, 'scrollHeight', 'get').mockReturnValue(2000);
    vi.spyOn(container, 'clientHeight', 'get').mockReturnValue(800);
    expect(zoom.canPan).toBe(true);
  });

  it('pans the container opposite to the drag direction', () => {
    const {container, zoom} = setup();
    zoom.zoomTo(2, {x: 0, y: 0});
    container.scrollLeft = 0;
    container.scrollTop = 0;
    zoom.beginPan({x: 300, y: 300});
    zoom.updatePan({x: 250, y: 280});
    zoom.endPan(false);
    expect(container.scrollLeft).toBe(50);
    expect(container.scrollTop).toBe(20);
  });

  it('removes every inline style when a pinch-out is undone past fit', () => {
    const {container, img, zoom} = setup();
    zoom.beginPinch({x: 100, y: 300}, {x: 300, y: 300});
    zoom.updatePinch({x: 0, y: 300}, {x: 400, y: 300});
    expect(zoom.level).toBe(2);
    expect(img.style.width).toBe('800px');

    zoom.updatePinch({x: 150, y: 300}, {x: 250, y: 300});
    zoom.endPinch();

    expect(zoom.isZoomed).toBe(false);
    expect(img.getAttribute('style')).toBeFalsy();
    expect(container.style.overflow).toBe('');
    expect(container.querySelector<HTMLElement>('.pages-wrapper')!.style.width).toBe('');
  });
});
