import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {LocalStorageService} from '../../../shared/service/local-storage.service';
import {AuthorScalePreferenceService} from './author-scale-preference.service';

describe('AuthorScalePreferenceService', () => {
  const localStorageService = {
    get: vi.fn(),
    set: vi.fn(),
  };

  beforeEach(() => {
    vi.useFakeTimers();
    vi.restoreAllMocks();
    localStorageService.get.mockReset();
    localStorageService.set.mockReset();
    localStorageService.get.mockReturnValue(1.5);

    TestBed.configureTestingModule({
      providers: [
        AuthorScalePreferenceService,
        {provide: LocalStorageService, useValue: localStorageService},
      ],
    });
  });

  afterEach(() => {
    vi.useRealTimers();
    TestBed.resetTestingModule();
  });

  it('loads the persisted scale and does not persist unchanged values', () => {
    const service = TestBed.inject(AuthorScalePreferenceService);

    expect(service.scaleFactor()).toBe(1.5);

    service.setScale(1.5);
    vi.runAllTimers();

    expect(localStorageService.set).not.toHaveBeenCalled();
  });

  it('debounces and persists the most recent scale value', () => {
    const service = TestBed.inject(AuthorScalePreferenceService);

    service.setScale(1.2);
    service.setScale(1.4);

    vi.advanceTimersByTime(999);
    expect(localStorageService.set).not.toHaveBeenCalled();

    vi.advanceTimersByTime(1);

    expect(localStorageService.set).toHaveBeenCalledOnce();
    expect(localStorageService.set).toHaveBeenCalledWith('authorScalePreference', 1.4);
  });

  it('falls back to the default scale when storage contains an invalid value', () => {
    localStorageService.get.mockReturnValue(Number.NaN);

    const service = TestBed.inject(AuthorScalePreferenceService);

    expect(service.scaleFactor()).toBe(1);
  });
});
