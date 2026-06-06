import {TestBed} from '@angular/core/testing';
import {of} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {TranslocoService} from '@jsverse/transloco';

import {initializeLanguage, LANG_STORAGE_KEY, LEGACY_LANG_STORAGE_KEY} from './language-initializer';

describe('initializeLanguage', () => {
  const translocoService = {
    setActiveLang: vi.fn(),
    load: vi.fn(),
  };

  beforeEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
    translocoService.setActiveLang.mockReset();
    translocoService.load.mockReset();
    translocoService.load.mockReturnValue(of({}));

    TestBed.configureTestingModule({
      providers: [
        {provide: TranslocoService, useValue: translocoService},
      ]
    });
  });

  afterEach(() => {
    localStorage.clear();
    TestBed.resetTestingModule();
  });

  it('uses the current persisted language when it is available', async () => {
    localStorage.setItem(LANG_STORAGE_KEY, 'de');

    const initializer = TestBed.runInInjectionContext(() => initializeLanguage());

    await TestBed.runInInjectionContext(() => initializer());

    expect(translocoService.setActiveLang).toHaveBeenCalledWith('de');
    expect(translocoService.load).toHaveBeenCalledWith('de');
    expect(localStorage.getItem(LANG_STORAGE_KEY)).toBe('de');
  });

  it('migrates the legacy persisted language key', async () => {
    localStorage.setItem(LEGACY_LANG_STORAGE_KEY, 'fr');

    const initializer = TestBed.runInInjectionContext(() => initializeLanguage());

    await TestBed.runInInjectionContext(() => initializer());

    expect(translocoService.setActiveLang).toHaveBeenCalledWith('fr');
    expect(localStorage.getItem(LANG_STORAGE_KEY)).toBe('fr');
  });

  it('prefers the exact browser locale when it is available', async () => {
    vi.spyOn(window.navigator, 'language', 'get').mockReturnValue('pt');

    const initializer = TestBed.runInInjectionContext(() => initializeLanguage());

    await TestBed.runInInjectionContext(() => initializer());

    expect(translocoService.setActiveLang).toHaveBeenCalledWith('pt');
    expect(localStorage.getItem(LANG_STORAGE_KEY)).toBe('pt');
  });

  it('falls back to the browser base language when the full locale is unavailable', async () => {
    vi.spyOn(window.navigator, 'language', 'get').mockReturnValue('pt-BR');

    const initializer = TestBed.runInInjectionContext(() => initializeLanguage());

    await TestBed.runInInjectionContext(() => initializer());

    expect(translocoService.setActiveLang).toHaveBeenCalledWith('pt');
    expect(localStorage.getItem(LANG_STORAGE_KEY)).toBe('pt');
  });

  it('falls back to english when there is no supported saved or browser language', async () => {
    localStorage.setItem(LANG_STORAGE_KEY, 'zz');
    vi.spyOn(window.navigator, 'language', 'get').mockReturnValue('zz-ZZ');

    const initializer = TestBed.runInInjectionContext(() => initializeLanguage());

    await TestBed.runInInjectionContext(() => initializer());

    expect(translocoService.setActiveLang).toHaveBeenCalledWith('en');
    expect(translocoService.load).toHaveBeenCalledWith('en');
    expect(localStorage.getItem(LANG_STORAGE_KEY)).toBe('en');
  });
});
