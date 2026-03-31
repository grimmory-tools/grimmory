import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { AppConfigService } from './app-config.service';

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
  const rootStyle = document.documentElement.style;

  beforeEach(() => {
    localStorageMock = createLocalStorageMock();
    vi.stubGlobal('localStorage', localStorageMock);
    rootStyle.cssText = '';

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [AppConfigService],
    });

    service = TestBed.inject(AppConfigService);
  });

  afterEach(() => {
    localStorageMock.clear();
    rootStyle.cssText = '';
    vi.unstubAllGlobals();
  });

  it('applies the default app-owned tokens on init', () => {
    expect(rootStyle.getPropertyValue('--primary-color')).toBe('#4ade80');
    expect(rootStyle.getPropertyValue('--ground-background')).toBe('#0d1012');
    expect(rootStyle.getPropertyValue('--content-border-color')).toBe('#464f56');
  });

  it('updates app-owned tokens for custom palettes', () => {
    service.appState.set({
      preset: 'Aura',
      primary: 'coralSunset',
      surface: 'midnight-blue',
    });
    service.onPresetChange();

    expect(rootStyle.getPropertyValue('--primary-color')).toBe('#f59673');
    expect(rootStyle.getPropertyValue('--primary-color-rgb')).toBe('239, 117, 80');
    expect(rootStyle.getPropertyValue('--ground-background')).toBe('#121518');
    expect(rootStyle.getPropertyValue('--card-background')).toBe('#1f252c');
  });

  it('maps noir to the selected surface palette', () => {
    service.appState.set({
      preset: 'Aura',
      primary: 'noir',
      surface: 'charcoal',
    });
    service.onPresetChange();

    expect(rootStyle.getPropertyValue('--primary-color')).toBe('#f0f0f0');
    expect(rootStyle.getPropertyValue('--primary-contrast-color')).toBe('#141414');
    expect(rootStyle.getPropertyValue('--primary-hover-color')).toBe('#d1d1d1');
  });
});
