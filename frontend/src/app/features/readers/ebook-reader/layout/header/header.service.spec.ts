import {Location} from '@angular/common';
import {TestBed} from '@angular/core/testing';
import {of} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {BookService} from '../../../../book/service/book.service';
import {ReaderSidebarService} from '../sidebar/sidebar.service';
import {ReaderLeftSidebarService} from '../panel/panel.service';
import {ReaderStateService} from '../../state/reader-state.service';
import {themes} from '../../state/themes.constant';
import {ReaderHeaderService} from './header.service';

describe('ReaderHeaderService', () => {
  const readerState = {
    state: vi.fn(() => ({
      lineHeight: 1.5,
      justify: true,
      hyphenate: true,
      maxColumnCount: 2,
      gap: 0.05,
      fontSize: 16,
      theme: {...themes[0], fg: themes[0].dark.fg, bg: themes[0].dark.bg, link: themes[0].dark.link},
      maxInlineSize: 720,
      maxBlockSize: 1440,
      fontFamily: null,
      isDark: true,
      flow: 'paginated',
    })),
    toggleDarkMode: vi.fn(),
    updateFontSize: vi.fn(),
  };

  const sidebarService = {
    open: vi.fn(),
    toggleBookmark: vi.fn(),
  };

  const leftSidebarService = {
    open: vi.fn(),
  };

  const bookService = {
    updateViewerSetting: vi.fn(() => of(void 0)),
  };

  const location = {
    back: vi.fn(),
  };

  let service: ReaderHeaderService;

  beforeEach(() => {
    readerState.state.mockClear();
    readerState.toggleDarkMode.mockReset();
    readerState.updateFontSize.mockReset();
    sidebarService.open.mockReset();
    sidebarService.toggleBookmark.mockReset();
    leftSidebarService.open.mockReset();
    bookService.updateViewerSetting.mockReset();
    bookService.updateViewerSetting.mockReturnValue(of(void 0));
    location.back.mockReset();

    TestBed.configureTestingModule({
      providers: [
        ReaderHeaderService,
        {provide: ReaderStateService, useValue: readerState},
        {provide: ReaderSidebarService, useValue: sidebarService},
        {provide: ReaderLeftSidebarService, useValue: leftSidebarService},
        {provide: BookService, useValue: bookService},
        {provide: Location, useValue: location},
      ]
    });

    service = TestBed.inject(ReaderHeaderService);
    service.initialize(12, 'Book Title');
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('tracks title, visibility, bookmarks, fullscreen, and resets cleanly', () => {
    service.setForceVisible(true);
    service.setCurrentCfiBookmarked(true);
    service.setFullscreen(true);

    expect(service.bookTitle()).toBe('Book Title');
    expect(service.forceVisible()).toBe(true);
    expect(service.isCurrentCfiBookmarked()).toBe(true);
    expect(service.isFullscreen()).toBe(true);
    expect(service.theme()).toMatchObject({name: 'default'});

    service.reset();

    expect(service.bookTitle()).toBe('');
    expect(service.forceVisible()).toBe(false);
    expect(service.isCurrentCfiBookmarked()).toBe(false);
    expect(service.isFullscreen()).toBe(false);
  });

  it('delegates sidebar and metadata actions and emits UI events', () => {
    const controlsEvents: void[] = [];
    const metadataEvents: void[] = [];
    const fullscreenEvents: void[] = [];
    const shortcutsEvents: void[] = [];

    service.showControls$.subscribe(() => controlsEvents.push(undefined));
    service.showMetadata$.subscribe(() => metadataEvents.push(undefined));
    service.toggleFullscreen$.subscribe(() => fullscreenEvents.push(undefined));
    service.showShortcutsHelp$.subscribe(() => shortcutsEvents.push(undefined));

    service.openSidebar();
    service.openLeftSidebar('notes');
    service.createBookmark();
    service.openControls();
    service.openMetadata();
    service.toggleFullscreen();
    service.showShortcutsHelp();
    service.close();

    expect(sidebarService.open).toHaveBeenCalledOnce();
    expect(leftSidebarService.open).toHaveBeenCalledWith('notes');
    expect(sidebarService.toggleBookmark).toHaveBeenCalledOnce();
    expect(controlsEvents).toHaveLength(1);
    expect(metadataEvents).toHaveLength(1);
    expect(fullscreenEvents).toHaveLength(1);
    expect(shortcutsEvents).toHaveLength(1);
    expect(location.back).toHaveBeenCalledOnce();
  });

  it('syncs dark mode and font size updates back to the viewer settings endpoint', () => {
    service.toggleDarkMode();
    service.increaseFontSize();

    expect(readerState.toggleDarkMode).toHaveBeenCalledOnce();
    expect(readerState.updateFontSize).toHaveBeenCalledWith(1);
    expect(bookService.updateViewerSetting).toHaveBeenCalledTimes(2);
    expect(bookService.updateViewerSetting).toHaveBeenCalledWith({
      ebookSettings: {
        lineHeight: 1.5,
        justify: true,
        hyphenate: true,
        maxColumnCount: 2,
        gap: 0.05,
        fontSize: 16,
        theme: 'default',
        maxInlineSize: 720,
        maxBlockSize: 1440,
        fontFamily: null,
        isDark: true,
        flow: 'paginated',
      }
    }, 12);
  });
});
