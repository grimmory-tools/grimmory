import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {LocalStorageService} from '../../../shared/service/local-storage.service';
import {SeriesScalePreferenceService} from './series-scale-preference.service';

describe('SeriesScalePreferenceService', () => {
  const localStorageService = {
    get: vi.fn(),
    set: vi.fn(),
  };

  beforeEach(() => {
    vi.useFakeTimers();
    vi.restoreAllMocks();
    localStorageService.get.mockReset();
    localStorageService.set.mockReset();
    localStorageService.get.mockReturnValue(0.75);

    TestBed.configureTestingModule({
      providers: [
        SeriesScalePreferenceService,
        {provide: LocalStorageService, useValue: localStorageService},
      ],
    });
  });

  afterEach(() => {
    vi.useRealTimers();
    TestBed.resetTestingModule();
  });

  it('loads the persisted scale and does not persist unchanged values', () => {
    const service = TestBed.inject(SeriesScalePreferenceService);

    expect(service.scaleFactor()).toBe(0.75);

    service.setScale(0.75);
    vi.runAllTimers();

    expect(localStorageService.set).not.toHaveBeenCalled();
  });

  it('debounces and persists the most recent scale value', () => {
    const service = TestBed.inject(SeriesScalePreferenceService);

    service.setScale(0.9);
    service.setScale(1.1);

    vi.advanceTimersByTime(1000);

    expect(localStorageService.set).toHaveBeenCalledOnce();
    expect(localStorageService.set).toHaveBeenCalledWith('seriesScalePreference', 1.1);
  });

  it('falls back to the default scale when storage contains an invalid value', () => {
    localStorageService.get.mockReturnValue(undefined);

    const service = TestBed.inject(SeriesScalePreferenceService);

    expect(service.scaleFactor()).toBe(1);
  });
});
