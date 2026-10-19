import {afterEach, describe, expect, it, vi} from 'vitest';
import {
  anchorScrollDelta,
  CbxPageZoom,
  CBX_MAX_ZOOM,
  CBX_MIN_ZOOM,
  clampZoom,
  toAnchorFraction,
} from './cbx-page-zoom';

describe('cbx page zoom math', () => {
  it('clamps zoom to the supported range', () => {
    expect(clampZoom(0.5)).toBe(CBX_MIN_ZOOM);
    expect(clampZoom(12)).toBe(CBX_MAX_ZOOM);
    expect(clampZoom(2.2)).toBe(2.2);
    expect(clampZoom(NaN)).toBe(CBX_MIN_ZOOM);
  });

  it('keeps the anchored spot under the tap point after zooming', () => {
    const before = {left: 100, top: 50, width: 400, height: 600};
    const tap = {x: 300, y: 350};
    const fraction = toAnchorFraction(tap, before);
    expect(fraction).toEqual({x: 0.5, y: 0.5});

    // Page doubled in size and was laid out starting at the same place, before any scroll correction.
    const after = {left: 100, top: 50, width: 800, height: 1200};
    const delta = anchorScrollDelta(fraction, after, tap);
    expect(delta).toEqual({x: 200, y: 300});

    // Scrolling by the delta moves the content back so the spot sits under the tap point again.
    const scrolled = {...after, left: after.left - delta.x, top: after.top - delta.y};
    expect(toAnchorFraction(tap, scrolled)).toEqual(fraction);
  });

  it('computes the scroll delta for an off-centre anchor', () => {
    const box = {left: 0, top: 0, width: 1000, height: 1500};
    const delta = anchorScrollDelta({x: 0.1, y: 0.1}, box, {x: 150, y: 120});
    expect(delta).toEqual({x: -50, y: 30});
  });
});

afterEach(() => {
  vi.restoreAllMocks();
  document.body.innerHTML = '';
});

describe('CbxPageZoom', () => {
  function setup(): {container: HTMLElement; img: HTMLImageElement; zoom: CbxPageZoom} {
    document.body.innerHTML = `
      <div class="image-container">
        <div class="pages-wrapper"><div class="current-page-layer"><img class="page-image"></div></div>
      </div>`;
    const container = document.querySelector<HTMLElement>('.image-container')!;
    const img = document.querySelector<HTMLImageElement>('img')!;
    // The image box follows its inline size, like a laid-out page starting at the viewport origin.
    vi.spyOn(img, 'getBoundingClientRect').mockImplementation(() => {
      const width = parseFloat(img.style.width) || 400;
      const height = parseFloat(img.style.height) || 600;
      return {left: 0, top: 0, right: width, bottom: height, width, height, x: 0, y: 0, toJSON: () => ({})} as DOMRect;
    });
    return {container, img, zoom: new CbxPageZoom(() => container)};
  }

  it('resizes the page and scrolls so the tapped spot stays under the tap', () => {
    const {container, img, zoom} = setup();
    zoom.zoomTo(2.5, {x: 200, y: 300});

    expect(zoom.level).toBe(2.5);
    expect(zoom.isZoomed).toBe(true);
    expect(img.style.width).toBe('1000px');
    expect(img.style.height).toBe('1500px');
    // The tap was at the page centre, which is now at (500, 750).
    expect(container.scrollLeft).toBe(300);
    expect(container.scrollTop).toBe(450);
  });

  it('start-aligns the container and allows panning on both axes while zoomed', () => {
    const {container, zoom} = setup();
    zoom.zoomTo(2, {x: 0, y: 0});

    // So the overflowing page can be scrolled to its top and left edges.
    expect(container.style.justifyContent).toBe('flex-start');
    expect(container.style.alignItems).toBe('flex-start');
    expect(container.style.overflow).toBe('auto');
    expect(container.style.touchAction).toBe('pan-x pan-y');
  });

  it('removes every inline style and scrolls back when zooming back to fit', () => {
    const {container, img, zoom} = setup();
    zoom.zoomTo(2, {x: 200, y: 300});
    zoom.zoomTo(CBX_MIN_ZOOM, {x: 200, y: 300});

    expect(zoom.isZoomed).toBe(false);
    expect(img.getAttribute('style')).toBeFalsy();
    expect(container.getAttribute('style')).toBeFalsy();
    // Checked per property: jsdom keeps serializing removed longhands in the style attribute.
    const wrapper = container.querySelector<HTMLElement>('.pages-wrapper')!.style;
    expect([wrapper.display, wrapper.flexGrow, wrapper.marginTop, wrapper.width, wrapper.minHeight]).toEqual(['', '', '', '', '']);
    expect(container.querySelector<HTMLElement>('.current-page-layer')!.getAttribute('style')).toBeFalsy();
    expect(container.scrollLeft).toBe(0);
    expect(container.scrollTop).toBe(0);
  });

  it('does nothing at fit or before the page has a size', () => {
    const {container, img, zoom} = setup();
    zoom.zoomTo(CBX_MIN_ZOOM, {x: 200, y: 300});
    expect(container.getAttribute('style')).toBeFalsy();

    vi.spyOn(img, 'getBoundingClientRect').mockReturnValue(
      {left: 0, top: 0, right: 0, bottom: 0, width: 0, height: 0, x: 0, y: 0, toJSON: () => ({})} as DOMRect
    );
    zoom.zoomTo(2.5, {x: 200, y: 300});
    expect(zoom.isZoomed).toBe(false);
    expect(container.getAttribute('style')).toBeFalsy();
  });

  it('returns to the fit scroll position when zooming back out', () => {
    const {container, zoom} = setup();
    // Fit width: scrolled partway down the page before zooming.
    container.scrollTop = 300;
    zoom.zoomTo(2.5, {x: 200, y: 300});
    container.scrollTop = 1200;
    zoom.zoomTo(CBX_MIN_ZOOM, {x: 200, y: 300});

    expect(container.scrollTop).toBe(300);
    expect(container.scrollLeft).toBe(0);
  });

  it('goes to the top on reset and forgets the fit scroll position', () => {
    const {container, zoom} = setup();
    container.scrollTop = 300;
    zoom.zoomTo(2.5, {x: 200, y: 300});
    // Page turn or setting change.
    zoom.reset();
    expect(container.scrollTop).toBe(0);

    container.scrollTop = 50;
    zoom.zoomTo(CBX_MIN_ZOOM, {x: 200, y: 300});
    expect(container.scrollTop).toBe(50);
  });

  it('zooms both pages of a spread together, anchored on the pair', () => {
    document.body.innerHTML = `
      <div class="image-container">
        <div class="pages-wrapper"><div class="current-page-layer"><img><img></div></div>
      </div>`;
    const container = document.querySelector<HTMLElement>('.image-container')!;
    const [left, right] = Array.from(document.querySelectorAll<HTMLImageElement>('img'));
    // Side by side from the viewport origin, each box following its inline size.
    const box = (img: HTMLImageElement, offsetOf: () => number) => vi.spyOn(img, 'getBoundingClientRect').mockImplementation(() => {
      const width = parseFloat(img.style.width) || 300;
      const height = parseFloat(img.style.height) || 450;
      const x = offsetOf();
      return {left: x, top: 0, right: x + width, bottom: height, width, height, x, y: 0, toJSON: () => ({})} as DOMRect;
    });
    box(left, () => 0);
    box(right, () => parseFloat(left.style.width) || 300);

    new CbxPageZoom(() => container).zoomTo(2, {x: 300, y: 225});

    expect(left.style.width).toBe('600px');
    expect(right.style.width).toBe('600px');
    // The tap was at the spread's centre (the gutter), which is now at (600, 450).
    expect(container.scrollLeft).toBe(300);
    expect(container.scrollTop).toBe(225);
  });

  it('remeasures the page after a reset, since a new page can differ in size', () => {
    const {img, zoom} = setup();
    zoom.zoomTo(2, {x: 0, y: 0});
    zoom.reset();

    vi.spyOn(img, 'getBoundingClientRect').mockImplementation(() => {
      const width = parseFloat(img.style.width) || 300;
      const height = parseFloat(img.style.height) || 500;
      return {left: 0, top: 0, right: width, bottom: height, width, height, x: 0, y: 0, toJSON: () => ({})} as DOMRect;
    });
    zoom.zoomTo(2, {x: 0, y: 0});
    expect(img.style.width).toBe('600px');
  });
});
