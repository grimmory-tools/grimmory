import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { LayoutService } from './layout.service';

describe('LayoutService', () => {
  let service: LayoutService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [LayoutService],
    });

    service = TestBed.inject(LayoutService);
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('toggles sidebarOpen on desktop', () => {
    vi.spyOn(service, 'isDesktop').mockReturnValue(true);

    expect(service.sidebarOpen()).toBe(true);
    service.onMenuToggle();
    expect(service.sidebarOpen()).toBe(false);
    service.onMenuToggle();
    expect(service.sidebarOpen()).toBe(true);
  });

  it('toggles mobileSidebarOpen on mobile', () => {
    vi.spyOn(service, 'isDesktop').mockReturnValue(false);

    expect(service.mobileSidebarOpen()).toBe(false);
    service.onMenuToggle();
    expect(service.mobileSidebarOpen()).toBe(true);
    service.onMenuToggle();
    expect(service.mobileSidebarOpen()).toBe(false);
  });

  it('closeMobileSidebar resets mobileSidebarOpen to false', () => {
    service.mobileSidebarOpen.set(true);
    service.closeMobileSidebar();
    expect(service.mobileSidebarOpen()).toBe(false);
  });

  it('updates the document root font size when the scale changes', () => {
    service.scale.set(18);
    TestBed.flushEffects();

    expect(document.documentElement.style.fontSize).toBe('18px');
  });
});
