import {signal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {getTranslocoModule} from '../../../../../core/testing/transloco-testing';
import {CbxHeaderComponent} from './cbx-header.component';
import {CbxHeaderService} from './cbx-header.service';

describe('CbxHeaderComponent', () => {
  let fixture: ComponentFixture<CbxHeaderComponent>;
  let component: CbxHeaderComponent;
  let headerService: {
    forceVisible: ReturnType<typeof signal<boolean>>;
    state: ReturnType<typeof signal<{isFullscreen: boolean; isSlideshowActive: boolean; isMagnifierActive: boolean}>>;
    bookTitle: ReturnType<typeof signal<string>>;
    openSidebar: ReturnType<typeof vi.fn>;
    openQuickSettings: ReturnType<typeof vi.fn>;
    toggleBookmark: ReturnType<typeof vi.fn>;
    openNoteDialog: ReturnType<typeof vi.fn>;
    toggleFullscreen: ReturnType<typeof vi.fn>;
    toggleSlideshow: ReturnType<typeof vi.fn>;
    toggleMagnifier: ReturnType<typeof vi.fn>;
    showShortcutsHelp: ReturnType<typeof vi.fn>;
    close: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    headerService = {
      forceVisible: signal(true),
      state: signal({isFullscreen: false, isSlideshowActive: false, isMagnifierActive: false}),
      bookTitle: signal('Chapter One'),
      openSidebar: vi.fn(),
      openQuickSettings: vi.fn(),
      toggleBookmark: vi.fn(),
      openNoteDialog: vi.fn(),
      toggleFullscreen: vi.fn(),
      toggleSlideshow: vi.fn(),
      toggleMagnifier: vi.fn(),
      showShortcutsHelp: vi.fn(),
      close: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [CbxHeaderComponent, getTranslocoModule()],
      providers: [{provide: CbxHeaderService, useValue: headerService}],
    }).compileComponents();

    fixture = TestBed.createComponent(CbxHeaderComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it('exposes the header service signals and default overflow state', () => {
    expect(component.forceVisible()).toBe(true);
    expect(component.bookTitle()).toBe('Chapter One');
    expect(component.state()).toEqual({
      isFullscreen: false,
      isSlideshowActive: false,
      isMagnifierActive: false,
    });
    expect(component.overflowOpen).toBe(false);
  });

  it('delegates all header actions to the header service', () => {
    component.onOpenSidebar();
    component.onOpenSettings();
    component.onToggleBookmark();
    component.onOpenNoteDialog();
    component.onToggleFullscreen();
    component.onToggleSlideshow();
    component.onToggleMagnifier();
    component.onShowShortcutsHelp();
    component.onClose();

    expect(headerService.openSidebar).toHaveBeenCalledOnce();
    expect(headerService.openQuickSettings).toHaveBeenCalledOnce();
    expect(headerService.toggleBookmark).toHaveBeenCalledOnce();
    expect(headerService.openNoteDialog).toHaveBeenCalledOnce();
    expect(headerService.toggleFullscreen).toHaveBeenCalledOnce();
    expect(headerService.toggleSlideshow).toHaveBeenCalledOnce();
    expect(headerService.toggleMagnifier).toHaveBeenCalledOnce();
    expect(headerService.showShortcutsHelp).toHaveBeenCalledOnce();
    expect(headerService.close).toHaveBeenCalledOnce();
  });
});
