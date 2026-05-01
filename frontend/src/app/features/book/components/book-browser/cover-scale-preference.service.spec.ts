import {TestBed} from '@angular/core/testing';
import {vi, describe, beforeEach, afterEach, expect, it} from 'vitest';

import {CoverScalePreferenceService} from './cover-scale-preference.service';
import {LocalStorageService} from '../../../../shared/service/local-storage.service';

describe('CoverScalePreferenceService', () => {
  let service: CoverScalePreferenceService;
  let localStorageService: { get: ReturnType<typeof vi.fn>; set: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    localStorageService = {
      get: vi.fn(),
      set: vi.fn()
    };
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('loads the saved scale and derives the card metrics', () => {
    localStorageService.get.mockReturnValue(1.5);

    TestBed.configureTestingModule({
      providers: [
        CoverScalePreferenceService,
        {provide: LocalStorageService, useValue: localStorageService},
      ]
    });

    service = TestBed.inject(CoverScalePreferenceService);

    expect(service.scaleFactor()).toBe(1.5);
    expect(service.currentCardSize()).toEqual({width: 203, height: 330});
  });

  it('normalizes out-of-range saved scale values', () => {
    localStorageService.get.mockReturnValue(3);

    TestBed.configureTestingModule({
      providers: [
        CoverScalePreferenceService,
        {provide: LocalStorageService, useValue: localStorageService},
      ]
    });

    service = TestBed.inject(CoverScalePreferenceService);

    expect(service.scaleFactor()).toBe(1.5);
    expect(localStorageService.set).toHaveBeenCalledWith('coverScalePreference', 1.5);
  });

  it('falls back to the default scale and skips persistence when unchanged', () => {
    localStorageService.get.mockReturnValue(null);

    TestBed.configureTestingModule({
      providers: [
        CoverScalePreferenceService,
        {provide: LocalStorageService, useValue: localStorageService},
      ]
    });

    service = TestBed.inject(CoverScalePreferenceService);

    service.setScale(1.0);

    expect(localStorageService.set).not.toHaveBeenCalled();
  });

  it('persists updated scale immediately without showing a toast', () => {
    localStorageService.get.mockReturnValue(1.0);

    TestBed.configureTestingModule({
      providers: [
        CoverScalePreferenceService,
        {provide: LocalStorageService, useValue: localStorageService},
      ]
    });

    service = TestBed.inject(CoverScalePreferenceService);

    service.setScale(1.2);

    expect(localStorageService.set).toHaveBeenCalledWith('coverScalePreference', 1.2);
  });
});
