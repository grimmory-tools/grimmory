import {afterEach, describe, expect, it, vi} from 'vitest';
import {
  anchorScrollDelta,
  CbxPageZoom,
  CBX_MAX_ZOOM,
  CBX_ZOOM_CSS_VAR,
  CBX_MIN_ZOOM,
  clampZoom,
  snapZoom,
  toAnchorFraction,
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

  it('keeps the anchored spot under the pinch midpoint after zooming', () => {
    const before = {left: 100, top: 50, width: 400, height: 600};
    const midpoint = {x: 300, y: 350};
    const fraction = toAnchorFraction(midpoint, before);
    expect(fraction).toEqual({x: 0.5, y: 0.5});

    // Page doubled in size and was laid out starting at the same place, before any scroll correction.
    const after = {left: 100, top: 50, width: 800, height: 1200};
    const delta = anchorScrollDelta(fraction, after, midpoint);
    expect(delta).toEqual({x: 200, y: 300});

    // Scrolling by the delta moves the content back so the spot sits under the midpoint again.
    const scrolled = {...after, left: after.left - delta.x, top: after.top - delta.y};
    expect(toAnchorFraction(midpoint, scrolled)).toEqual(fraction);
  });

  it('follows the midpoint when two fingers pan while pinching', () => {
    const box = {left: 0, top: 0, width: 1000, height: 1500};
    const delta = anchorScrollDelta({x: 0.1, y: 0.1}, box, {x: 150, y: 120});
    expect(delta).toEqual({x: -50, y: 30});
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

  function giveScrollRoom(container: HTMLElement): void {
    vi.spyOn(container, 'scrollWidth', 'get').mockReturnValue(1600);
    vi.spyOn(container, 'clientWidth', 'get').mockReturnValue(400);
    vi.spyOn(container, 'scrollHeight', 'get').mockReturnValue(2400);
    vi.spyOn(container, 'clientHeight', 'get').mockReturnValue(600);
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
    giveScrollRoom(container);
    zoom.zoomTo(2, {x: 0, y: 0});
    container.scrollLeft = 0;
    container.scrollTop = 0;
    zoom.beginPan({x: 300, y: 300});
    zoom.updatePan({x: 250, y: 280});
    zoom.endPan(false);
    expect(container.scrollLeft).toBe(50);
    expect(container.scrollTop).toBe(20);
  });

  it('does not scroll until the pan moves past the slop', () => {
    const {container, zoom} = setup();
    giveScrollRoom(container);
    zoom.zoomTo(2, {x: 0, y: 0});
    container.scrollLeft = 100;
    zoom.beginPan({x: 300, y: 300});
    zoom.updatePan({x: 297, y: 300});
    expect(container.scrollLeft).toBe(100);
    zoom.updatePan({x: 280, y: 300});
    expect(container.scrollLeft).toBe(120);
  });

  it('lets a sideways swipe through when the page only scrolls vertically', () => {
    const {container, zoom} = setup();
    vi.spyOn(container, 'scrollHeight', 'get').mockReturnValue(2000);
    vi.spyOn(container, 'clientHeight', 'get').mockReturnValue(800);
    vi.spyOn(container, 'scrollWidth', 'get').mockReturnValue(400);
    vi.spyOn(container, 'clientWidth', 'get').mockReturnValue(400);

    zoom.beginPan({x: 300, y: 300});
    zoom.updatePan({x: 200, y: 290});
    expect(zoom.isPanning).toBe(false);
    expect(container.scrollTop).toBe(0);

    zoom.beginPan({x: 300, y: 300});
    zoom.updatePan({x: 295, y: 200});
    expect(zoom.isPanning).toBe(true);
    expect(container.scrollTop).toBe(100);
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

describe('CbxPageZoom strip layout', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    document.body.innerHTML = '';
  });

  function setupStrip(): {container: HTMLElement; column: HTMLElement; zoom: CbxPageZoom} {
    document.body.innerHTML = `
      <div class="image-container">
        <div class="long-strip-wrapper">
          <div class="strip-width-constrain" style="width: 80%">
            <img class="long-strip-image"><img class="long-strip-image">
          </div>
        </div>
      </div>`;
    const container = document.querySelector<HTMLElement>('.image-container')!;
    const column = document.querySelector<HTMLElement>('.strip-width-constrain')!;
    const [first, second] = Array.from(document.querySelectorAll<HTMLElement>('img'));
    const rect = (top: number) => ({left: 0, top, right: 400, bottom: top + 600, width: 400, height: 600, x: 0, y: top, toJSON: () => ({})}) as DOMRect;
    vi.spyOn(first, 'getBoundingClientRect').mockReturnValue(rect(0));
    vi.spyOn(second, 'getBoundingClientRect').mockReturnValue(rect(600));
    return {container, column, zoom: new CbxPageZoom(() => container, () => 'strip')};
  }

  it('scales the strip column and exposes the zoom to the fit-mode CSS', () => {
    const {container, column, zoom} = setupStrip();
    zoom.zoomTo(2, {x: 200, y: 900});
    expect(zoom.level).toBe(2);
    // jsdom folds the calc(); browsers keep calc(80% * 2).
    expect(column.style.width).toMatch(/^calc\((80% \* 2|160%)\)$/);
    expect(column.style.maxWidth).toBe('none');
    expect(container.style.getPropertyValue(CBX_ZOOM_CSS_VAR)).toBe('2');
  });

  it('restores the strip column and keeps the vertical position when zooming back to fit', () => {
    const {container, column, zoom} = setupStrip();
    zoom.zoomTo(2, {x: 200, y: 900});
    container.scrollTop = 700;
    container.scrollLeft = 150;
    zoom.reset();

    expect(column.style.width).toBe('80%');
    expect(column.style.maxWidth).toBe('');
    expect(container.style.getPropertyValue(CBX_ZOOM_CSS_VAR)).toBe('');
    expect(container.scrollTop).toBe(700);
    expect(container.scrollLeft).toBe(0);
  });
});
