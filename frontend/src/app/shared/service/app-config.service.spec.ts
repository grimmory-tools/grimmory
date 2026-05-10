import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { AppConfigService } from './app-config.service';
import { FaviconService } from '../layout/theme-configurator/favicon-service';

function createLocalStorageMock() {
  const store = new Map<string, string>();

  return {
    getItem: (key: string) => store.get(key) ?? null,
    setItem: (key: string, value: string) => {
      store.set(key, value);
    },
    removeItem: (key: string) => {
      store.delete(key);
    },
    clear: () => {
      store.clear();
    },
  };
}

describe('AppConfigService', () => {
  let service: AppConfigService;
  let localStorageMock: ReturnType<typeof createLocalStorageMock>;
  let faviconServiceMock: { updateFavicon: ReturnType<typeof vi.fn> };
  const rootStyle = document.documentElement.style;

  beforeEach(() => {
    localStorageMock = createLocalStorageMock();
    faviconServiceMock = {
      updateFavicon: vi.fn(),
    };
    vi.stubGlobal('localStorage', localStorageMock);
    rootStyle.cssText = '';

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        AppConfigService,
        { provide: FaviconService, useValue: faviconServiceMock },
      ],
    });

    service = TestBed.inject(AppConfigService);
  });

  afterEach(() => {
    localStorageMock.clear();
    rootStyle.cssText = '';
    vi.unstubAllGlobals();
  });

  it('applies the default app-owned tokens on init', () => {
    expect(rootStyle.getPropertyValue('--primary-300')).toBe('#fdba74');
    expect(rootStyle.getPropertyValue('--primary-400')).toBe('#fb923c');
    expect(rootStyle.getPropertyValue('--primary-500')).toBe('#f97316');
    expect(rootStyle.getPropertyValue('--primary-color')).toBe('#fb923c');
    expect(rootStyle.getPropertyValue('--app-background')).toBe('#0d1012');
    expect(rootStyle.getPropertyValue('--page-background')).toBe('color-mix(in srgb, #1a1e21 82%, #0d1012)');
    expect(rootStyle.getPropertyValue('--surface-content')).toBe('#1a1e21');
    expect(rootStyle.getPropertyValue('--ground-background')).toBe('var(--app-background)');
    expect(rootStyle.getPropertyValue('--content-border-color')).toBe('var(--border-subtle)');
    expect(faviconServiceMock.updateFavicon).toHaveBeenCalledWith('#fdba74', '#f97316');
  });

  it('updates app-owned tokens for custom palettes', () => {
    service.appState.set({
      preset: 'Aura',
      primary: 'coralSunset',
      surface: 'midnight-blue',
    });
    service.onPresetChange();

    expect(rootStyle.getPropertyValue('--primary-color')).toBe('#f59673');
    expect(rootStyle.getPropertyValue('--primary-300')).toBe('#f9be9e');
    expect(rootStyle.getPropertyValue('--primary-500')).toBe('#ef7550');
    expect(rootStyle.getPropertyValue('--primary-color-rgb')).toBe('239, 117, 80');
    expect(rootStyle.getPropertyValue('--app-background')).toBe('#121518');
    expect(rootStyle.getPropertyValue('--page-background')).toBe('color-mix(in srgb, #1f252c 82%, #121518)');
    expect(rootStyle.getPropertyValue('--ground-background')).toBe('var(--app-background)');
    expect(rootStyle.getPropertyValue('--surface-card')).toBe('color-mix(in srgb, #1f252c 62%, #38424d)');
    expect(rootStyle.getPropertyValue('--card-background')).toBe('var(--surface-card)');
    expect(faviconServiceMock.updateFavicon).toHaveBeenLastCalledWith('#f9be9e', '#ef7550');
  });

  it('maps noir to the selected surface palette', () => {
    service.appState.set({
      preset: 'Aura',
      primary: 'noir',
      surface: 'charcoal',
    });
    service.onPresetChange();

    expect(rootStyle.getPropertyValue('--primary-color')).toBe('#f0f0f0');
    expect(rootStyle.getPropertyValue('--primary-300')).toBe('#b8b8b8');
    expect(rootStyle.getPropertyValue('--primary-500')).toBe('#7d7d7d');
    expect(rootStyle.getPropertyValue('--primary-contrast-color')).toBe('#141414');
    expect(rootStyle.getPropertyValue('--primary-hover-color')).toBe('#d1d1d1');
    expect(faviconServiceMock.updateFavicon).toHaveBeenLastCalledWith('#f0f0f0', '#b8b8b8');
  });
});
