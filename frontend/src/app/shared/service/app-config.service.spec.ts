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

function createThemeComputedStyle(): CSSStyleDeclaration {
  const values = new Map<string, string>();

  ['50', '100', '200', '300', '400', '500', '600', '700', '800', '900', '950'].forEach((stop) => {
    values.set(`--color-primary-${stop}`, `primary-${stop}`);
  });
  ['0', '50', '100', '200', '300', '400', '500', '600', '700', '800', '900', '950'].forEach((stop) => {
    values.set(`--color-surface-${stop}`, `surface-${stop}`);
  });

  return {
    getPropertyValue: (propertyName: string) => values.get(propertyName) ?? '',
  } as CSSStyleDeclaration;
}

describe('AppConfigService', () => {
  let service: AppConfigService;
  let localStorageMock: ReturnType<typeof createLocalStorageMock>;
  let faviconServiceMock: { updateFavicon: ReturnType<typeof vi.fn> };
  const root = document.documentElement;
  const rootStyle = root.style;

  beforeEach(() => {
    localStorageMock = createLocalStorageMock();
    faviconServiceMock = {
      updateFavicon: vi.fn(),
    };
    vi.stubGlobal('localStorage', localStorageMock);
    rootStyle.cssText = '';
    const computedStyle = createThemeComputedStyle();
    vi.spyOn(globalThis, 'getComputedStyle').mockReturnValue(computedStyle);
    vi.spyOn(window, 'getComputedStyle').mockReturnValue(computedStyle);
    root.classList.remove('dark');
    delete root.dataset['appTheme'];

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
    root.classList.remove('dark');
    delete root.dataset['appTheme'];
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('applies the default curated theme and dark color scheme on init', () => {
    expect(root.dataset['appTheme']).toBe('grimmory');
    expect(root.classList.contains('dark')).toBe(true);
    expect(rootStyle.getPropertyValue('color-scheme')).toBe('dark');
    expect(rootStyle.getPropertyValue('--primary-300')).toBe('');
    expect(rootStyle.getPropertyValue('--color-app')).toBe('');
    expect(rootStyle.getPropertyValue('--color-card')).toBe('');
    expect(faviconServiceMock.updateFavicon).toHaveBeenCalledWith(
      'primary-300',
      'primary-500'
    );
  });

  it('updates the root theme attributes without writing palette tokens inline', () => {
    service.appState.set({
      preset: 'Aura',
      theme: 'grimmory',
      colorScheme: 'light',
    });
    service.onPresetChange();

    expect(root.dataset['appTheme']).toBe('grimmory');
    expect(root.classList.contains('dark')).toBe(false);
    expect(rootStyle.getPropertyValue('color-scheme')).toBe('light');
    expect(rootStyle.getPropertyValue('--color-primary')).toBe('');
    expect(rootStyle.getPropertyValue('--primary-300')).toBe('');
    expect(rootStyle.getPropertyValue('--color-card')).toBe('');
    expect(faviconServiceMock.updateFavicon).toHaveBeenLastCalledWith(
      'primary-300',
      'primary-500'
    );
  });

  it('falls back to the default theme for legacy saved palette state', () => {
    localStorageMock.setItem('appConfigState', JSON.stringify({
      preset: 'Aura',
      primary: 'blue',
      surface: 'charcoal',
      colorScheme: 'light',
    }));

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        AppConfigService,
        { provide: FaviconService, useValue: faviconServiceMock },
      ],
    });

    service = TestBed.inject(AppConfigService);

    expect(service.appState()).toEqual({
      preset: 'Aura',
      theme: 'grimmory',
      colorScheme: 'light',
      customPrimary: 'orange',
    });
    expect(root.dataset['appTheme']).toBe('grimmory');
    expect(root.classList.contains('dark')).toBe(false);
  });
});
