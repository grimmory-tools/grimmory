import {Location} from '@angular/common';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {CbxHeaderService} from './cbx-header.service';
import {CbxSidebarService} from '../sidebar/cbx-sidebar.service';

describe('CbxHeaderService', () => {
  let service: CbxHeaderService;

  const sidebarService = {
    open: vi.fn(),
  };

  const location = {
    back: vi.fn(),
  };

  beforeEach(() => {
    sidebarService.open.mockReset();
    location.back.mockReset();

    TestBed.configureTestingModule({
      providers: [
        CbxHeaderService,
        {provide: CbxSidebarService, useValue: sidebarService},
        {provide: Location, useValue: location},
      ]
    });

    service = TestBed.inject(CbxHeaderService);
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('tracks title, visibility, and state changes and resets to defaults', () => {
    service.initialize('Saga');
    service.setForceVisible(false);
    service.updateState({isFullscreen: true, isSlideshowActive: true});

    expect(service.bookTitle()).toBe('Saga');
    expect(service.forceVisible()).toBe(false);
    expect(service.state()).toEqual({
      isFullscreen: true,
      isSlideshowActive: true,
      isMagnifierActive: false
    });

    service.reset();

    expect(service.bookTitle()).toBe('');
    expect(service.forceVisible()).toBe(true);
    expect(service.state()).toEqual({
      isFullscreen: false,
      isSlideshowActive: false,
      isMagnifierActive: false
    });
  });

  it('falls back to an empty title when initialized without one', () => {
    service.initialize(undefined);

    expect(service.bookTitle()).toBe('');
  });

  it('delegates sidebar opening, close navigation, and emits header actions', () => {
    const quickSettingsEvents: void[] = [];
    const bookmarkEvents: void[] = [];
    const noteEvents: void[] = [];
    const fullscreenEvents: void[] = [];
    const slideshowEvents: void[] = [];
    const magnifierEvents: void[] = [];
    const shortcutsEvents: void[] = [];

    service.showQuickSettings$.subscribe(() => quickSettingsEvents.push(undefined));
    service.toggleBookmark$.subscribe(() => bookmarkEvents.push(undefined));
    service.openNoteDialog$.subscribe(() => noteEvents.push(undefined));
    service.toggleFullscreen$.subscribe(() => fullscreenEvents.push(undefined));
    service.toggleSlideshow$.subscribe(() => slideshowEvents.push(undefined));
    service.toggleMagnifier$.subscribe(() => magnifierEvents.push(undefined));
    service.showShortcutsHelp$.subscribe(() => shortcutsEvents.push(undefined));

    service.openSidebar();
    service.openQuickSettings();
    service.toggleBookmark();
    service.openNoteDialog();
    service.toggleFullscreen();
    service.toggleSlideshow();
    service.toggleMagnifier();
    service.showShortcutsHelp();
    service.close();

    expect(sidebarService.open).toHaveBeenCalledOnce();
    expect(location.back).toHaveBeenCalledOnce();
    expect(quickSettingsEvents).toHaveLength(1);
    expect(bookmarkEvents).toHaveLength(1);
    expect(noteEvents).toHaveLength(1);
    expect(fullscreenEvents).toHaveLength(1);
    expect(slideshowEvents).toHaveLength(1);
    expect(magnifierEvents).toHaveLength(1);
    expect(shortcutsEvents).toHaveLength(1);
  });
});
