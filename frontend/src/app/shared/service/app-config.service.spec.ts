import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { AppConfigService } from './app-config.service';
import { FaviconService } from '../layout/theme/favicon-service';

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

function createMatchMediaMock(matches: boolean) {
  return vi.fn().mockImplementation((query: string) => ({
    matches,
    media: query,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  }) as unknown as MediaQueryList);
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
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      value: createMatchMediaMock(false),
    });
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

  it('applies the default curated theme and system appearance on init', () => {
    expect(service.appState()).toEqual({
      themePreference: 'grimmory',
      appearancePreference: 'system',
      customPrimary: 'orange',
    });
    expect(localStorageMock.getItem('appConfigState')).toBeNull();
    expect(root.dataset['appTheme']).toBe('grimmory');
    expect(root.classList.contains('dark')).toBe(false);
    expect(rootStyle.getPropertyValue('color-scheme')).toBe('light');
    expect(rootStyle.getPropertyValue('--primary-300')).toBe('');
    expect(rootStyle.getPropertyValue('--color-app')).toBe('');
    expect(rootStyle.getPropertyValue('--color-card')).toBe('');
    expect(faviconServiceMock.updateFavicon).toHaveBeenCalledWith(
      'primary-300',
      'primary-500'
    );
  });

  it('updates the root theme attributes without writing palette tokens inline', () => {
    service.setThemePreference('grimmory');
    service.setAppearancePreference('light');

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

  it('resets legacy saved palette state to the default theme and system appearance', () => {
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
      themePreference: 'grimmory',
      appearancePreference: 'system',
      customPrimary: 'orange',
    });
    expect(root.dataset['appTheme']).toBe('grimmory');
    expect(root.classList.contains('dark')).toBe(false);
    expect(localStorageMock.getItem('appConfigState')).toBe(JSON.stringify({
      themePreference: 'grimmory',
      appearancePreference: 'system',
      customPrimary: 'orange',
    }));
  });
});
