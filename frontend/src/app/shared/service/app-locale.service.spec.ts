import {TestBed} from '@angular/core/testing';
import {TranslocoService} from '@jsverse/transloco';
import {of} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {AppLocaleService} from './app-locale.service';

describe('AppLocaleService', () => {
  const translocoService = {
    load: vi.fn(),
    setActiveLang: vi.fn(),
  };

  beforeEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
    translocoService.load.mockReset();
    translocoService.setActiveLang.mockReset();
    translocoService.load.mockReturnValue(of({}));

    TestBed.configureTestingModule({
      providers: [
        AppLocaleService,
        {provide: TranslocoService, useValue: translocoService},
      ],
    });
  });

  afterEach(() => {
    localStorage.clear();
    TestBed.resetTestingModule();
  });

  it('defaults the display locale to english', () => {
    const service = TestBed.inject(AppLocaleService);

    expect(service.getDisplayLocale()).toBe('en');
  });

  it('uses a supported stored display locale', () => {
    localStorage.setItem('appLocale', 'de');
    const service = TestBed.inject(AppLocaleService);

    expect(service.getDisplayLocale()).toBe('de');
  });

  it('ignores an unsupported stored display locale', () => {
    localStorage.setItem('appLocale', 'zz');
    const service = TestBed.inject(AppLocaleService);

    expect(service.getDisplayLocale()).toBe('en');
  });

  it('loads, activates, and stores an applied locale', async () => {
    const service = TestBed.inject(AppLocaleService);

    await service.applyLocale('de');

    expect(translocoService.load).toHaveBeenCalledWith('de');
    expect(translocoService.setActiveLang).toHaveBeenCalledWith('de');
    expect(localStorage.getItem('appLocale')).toBe('de');
  });

  it('falls back to english for unsupported applied locales', async () => {
    const service = TestBed.inject(AppLocaleService);

    await service.applyLocale('zz');

    expect(translocoService.load).toHaveBeenCalledWith('en');
    expect(translocoService.setActiveLang).toHaveBeenCalledWith('en');
    expect(localStorage.getItem('appLocale')).toBe('en');
  });
});
