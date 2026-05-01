import { describe, expect, it } from 'vitest';

import {
  CbxBackgroundColor,
  CbxFitMode,
  CbxMagnifierLensSize,
  CbxMagnifierZoom,
  CbxPageSpread,
  CbxPageViewMode,
  CbxReadingDirection,
  CbxScrollMode,
  CbxSlideshowInterval,
} from '../../../../settings/user-management/user.service';
import { CbxQuickSettingsService } from './cbx-quick-settings.service';

describe('CbxQuickSettingsService', () => {
  it('tracks visibility and settings state and resets cleanly', () => {
    const service = new CbxQuickSettingsService();

    service.show();
    service.setFitMode(CbxFitMode.FIT_WIDTH);
    service.setScrollMode(CbxScrollMode.INFINITE);
    service.setPageViewMode(CbxPageViewMode.TWO_PAGE);
    service.setPageSpread(CbxPageSpread.EVEN);
    service.setBackgroundColor(CbxBackgroundColor.BLACK);
    service.setReadingDirection(CbxReadingDirection.RTL);
    service.setSlideshowInterval(CbxSlideshowInterval.TEN_SECONDS);
    service.setMagnifierZoom(CbxMagnifierZoom.ZOOM_4X);
    service.setMagnifierLensSize(CbxMagnifierLensSize.EXTRA_LARGE);
    service.setBrightness(50);
    service.setStripMaxWidthPercent(85);
    service.setEmulateBook(true);
    service.setClickToPaginate(true);
    service.setAutoCloseMenu(true);

    expect(service.visible()).toBe(true);
    expect(service.state()).toEqual({
      fitMode: CbxFitMode.FIT_WIDTH,
      scrollMode: CbxScrollMode.INFINITE,
      pageViewMode: CbxPageViewMode.TWO_PAGE,
      pageSpread: CbxPageSpread.EVEN,
      backgroundColor: CbxBackgroundColor.BLACK,
      readingDirection: CbxReadingDirection.RTL,
      slideshowInterval: CbxSlideshowInterval.TEN_SECONDS,
      magnifierZoom: CbxMagnifierZoom.ZOOM_4X,
      magnifierLensSize: CbxMagnifierLensSize.EXTRA_LARGE,
      brightness: 50,
      stripMaxWidthPercent: 85,
      emulateBook: true,
      clickToPaginate: true,
      autoCloseMenu: true
    });

    service.close();
    expect(service.visible()).toBe(false);

    service.reset();
    expect(service.visible()).toBe(false);
    expect(service.state()).toEqual({
      fitMode: CbxFitMode.FIT_PAGE,
      scrollMode: CbxScrollMode.PAGINATED,
      pageViewMode: CbxPageViewMode.SINGLE_PAGE,
      pageSpread: CbxPageSpread.EVEN,
      backgroundColor: CbxBackgroundColor.GRAY,
      readingDirection: CbxReadingDirection.LTR,
      slideshowInterval: CbxSlideshowInterval.FIVE_SECONDS,
      magnifierZoom: CbxMagnifierZoom.ZOOM_3X,
      magnifierLensSize: CbxMagnifierLensSize.MEDIUM,
      brightness: 100,
      stripMaxWidthPercent: 100,
      emulateBook: false,
      clickToPaginate: false,
      autoCloseMenu: false
    });
  });

  it('emits all quick settings change events', () => {
    const service = new CbxQuickSettingsService();
    const fitModeEvents: CbxFitMode[] = [];
    const scrollModeEvents: CbxScrollMode[] = [];
    const pageViewEvents: CbxPageViewMode[] = [];
    const pageSpreadEvents: CbxPageSpread[] = [];
    const backgroundEvents: CbxBackgroundColor[] = [];
    const directionEvents: CbxReadingDirection[] = [];
    const slideshowEvents: CbxSlideshowInterval[] = [];
    const zoomEvents: CbxMagnifierZoom[] = [];
    const lensSizeEvents: CbxMagnifierLensSize[] = [];

    service.fitModeChange$.subscribe(value => fitModeEvents.push(value));
    service.scrollModeChange$.subscribe(value => scrollModeEvents.push(value));
    service.pageViewModeChange$.subscribe(value => pageViewEvents.push(value));
    service.pageSpreadChange$.subscribe(value => pageSpreadEvents.push(value));
    service.backgroundColorChange$.subscribe(value => backgroundEvents.push(value));
    service.readingDirectionChange$.subscribe(value => directionEvents.push(value));
    service.slideshowIntervalChange$.subscribe(value => slideshowEvents.push(value));
    service.magnifierZoomChange$.subscribe(value => zoomEvents.push(value));
    service.magnifierLensSizeChange$.subscribe(value => lensSizeEvents.push(value));

    service.emitFitModeChange(CbxFitMode.ACTUAL_SIZE);
    service.emitScrollModeChange(CbxScrollMode.INFINITE);
    service.emitPageViewModeChange(CbxPageViewMode.TWO_PAGE);
    service.emitPageSpreadChange(CbxPageSpread.EVEN);
    service.emitBackgroundColorChange(CbxBackgroundColor.WHITE);
    service.emitReadingDirectionChange(CbxReadingDirection.RTL);
    service.emitSlideshowIntervalChange(CbxSlideshowInterval.FIFTEEN_SECONDS);
    service.emitMagnifierZoomChange(CbxMagnifierZoom.ZOOM_2X);
    service.emitMagnifierLensSizeChange(CbxMagnifierLensSize.LARGE);

    expect(fitModeEvents).toEqual([CbxFitMode.ACTUAL_SIZE]);
    expect(scrollModeEvents).toEqual([CbxScrollMode.INFINITE]);
    expect(pageViewEvents).toEqual([CbxPageViewMode.TWO_PAGE]);
    expect(pageSpreadEvents).toEqual([CbxPageSpread.EVEN]);
    expect(backgroundEvents).toEqual([CbxBackgroundColor.WHITE]);
    expect(directionEvents).toEqual([CbxReadingDirection.RTL]);
    expect(slideshowEvents).toEqual([CbxSlideshowInterval.FIFTEEN_SECONDS]);
    expect(zoomEvents).toEqual([CbxMagnifierZoom.ZOOM_2X]);
    expect(lensSizeEvents).toEqual([CbxMagnifierLensSize.LARGE]);
  });

  it('emits strip width changes when setStripMaxWidthPercent is called', () => {
    const service = new CbxQuickSettingsService();
    const stripEvents: number[] = [];
    service.stripMaxWidthChange$.subscribe((v) => stripEvents.push(v));

    service.setStripMaxWidthPercent(72);

    expect(stripEvents).toEqual([72]);
    expect(service.state().stripMaxWidthPercent).toBe(72);
  });
});
