import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {LayoutService} from './app.layout.service';

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

  it('detects when the configured theme style changes', () => {
    expect(service.updateStyle(service.config())).toBe(false);

    service.config.set({
      ...service.config(),
      theme: 'lara-dark-indigo',
    });

    expect(service.updateStyle(service.config())).toBe(true);
  });

  it('toggles the overlay menu and emits overlay-open notifications on desktop', () => {
    let overlayOpenCount = 0;
    service.config.set({
      ...service.config(),
      menuMode: 'overlay',
    });
    service.overlayOpen$.subscribe(() => {
      overlayOpenCount += 1;
    });

    Object.defineProperty(window, 'innerWidth', {configurable: true, value: 1280});
    service.onMenuToggle();

    expect(service.state.overlayMenuActive).toBe(true);
    expect(service.state.staticMenuDesktopInactive).toBe(true);
    expect(overlayOpenCount).toBe(1);
  });

  it('toggles the mobile static menu and emits overlay-open notifications on small screens', () => {
    let overlayOpenCount = 0;
    service.overlayOpen$.subscribe(() => {
      overlayOpenCount += 1;
    });

    Object.defineProperty(window, 'innerWidth', {configurable: true, value: 768});
    service.onMenuToggle();

    expect(service.state.staticMenuMobileActive).toBe(true);
    expect(overlayOpenCount).toBe(1);
  });

  it('derives the replacement theme href from the current config', () => {
    const replaceThemeLinkSpy = vi.spyOn(service, 'replaceThemeLink').mockImplementation(() => undefined);
    const themeLink = document.createElement('link');
    themeLink.id = 'theme-css';
    themeLink.setAttribute('href', '/themes/lara-light-indigo/theme-light/theme.css');
    document.body.appendChild(themeLink);

    service._config = {
      ...service._config,
      theme: 'lara-light-indigo',
      colorScheme: 'light',
    };
    service.config.set({
      ...service.config(),
      theme: 'lara-dark-indigo',
      colorScheme: 'dark',
    });

    service.changeTheme();

    expect(replaceThemeLinkSpy).toHaveBeenCalledWith('/themes/lara-dark-indigo/theme-dark/theme.css');
    themeLink.remove();
  });

  it('updates the document root font size when the scale changes', () => {
    service.changeScale(18);

    expect(document.documentElement.style.fontSize).toBe('18px');
  });
});
