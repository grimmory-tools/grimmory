import {TestBed} from '@angular/core/testing';
import {MessageService} from 'primeng/api';
import {TranslocoService} from '@jsverse/transloco';
import {vi, describe, beforeEach, afterEach, expect, it} from 'vitest';

import {CoverScalePreferenceService} from './cover-scale-preference.service';
import {LocalStorageService} from '../../../../shared/service/local-storage.service';

describe('CoverScalePreferenceService', () => {
  let service: CoverScalePreferenceService;
  let localStorageService: { get: ReturnType<typeof vi.fn>; set: ReturnType<typeof vi.fn> };
  let messageService: { add: ReturnType<typeof vi.fn> };
  let translocoService: { translate: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    vi.useFakeTimers();
    localStorageService = {
      get: vi.fn(),
      set: vi.fn()
    };
    messageService = {
      add: vi.fn()
    };
    translocoService = {
      translate: vi.fn((key: string, params?: Record<string, unknown>) =>
        params ? `${key}:${JSON.stringify(params)}` : `t:${key}`
      )
    };
  });

  afterEach(() => {
    vi.runOnlyPendingTimers();
    vi.useRealTimers();
    TestBed.resetTestingModule();
  });

  it('loads the saved scale and derives the card metrics', () => {
    localStorageService.get.mockReturnValue(1.5);

    TestBed.configureTestingModule({
      providers: [
        CoverScalePreferenceService,
        {provide: LocalStorageService, useValue: localStorageService},
        {provide: MessageService, useValue: messageService},
        {provide: TranslocoService, useValue: translocoService}
      ]
    });

    service = TestBed.inject(CoverScalePreferenceService);

    expect(service.scaleFactor()).toBe(1.5);
    expect(service.currentCardSize()).toEqual({width: 203, height: 330});
    expect(service.gridColumnMinWidth()).toBe('203px');
    expect(service.getCardHeight({} as never)).toBe(330);
  });

  it('falls back to the default scale and skips persistence when unchanged', () => {
    localStorageService.get.mockReturnValue(null);

    TestBed.configureTestingModule({
      providers: [
        CoverScalePreferenceService,
        {provide: LocalStorageService, useValue: localStorageService},
        {provide: MessageService, useValue: messageService},
        {provide: TranslocoService, useValue: translocoService}
      ]
    });

    service = TestBed.inject(CoverScalePreferenceService);

    service.setScale(1.0);
    vi.runOnlyPendingTimers();

    expect(localStorageService.set).not.toHaveBeenCalled();
    expect(messageService.add).not.toHaveBeenCalled();
  });

  it('persists updated scale after debounce and reports failures', () => {
    localStorageService.get.mockReturnValue(1.0);

    TestBed.configureTestingModule({
      providers: [
        CoverScalePreferenceService,
        {provide: LocalStorageService, useValue: localStorageService},
        {provide: MessageService, useValue: messageService},
        {provide: TranslocoService, useValue: translocoService}
      ]
    });

    service = TestBed.inject(CoverScalePreferenceService);

    service.setScale(1.2);
    expect(localStorageService.set).not.toHaveBeenCalled();

    vi.advanceTimersByTime(1000);

    expect(localStorageService.set).toHaveBeenCalledWith('coverScalePreference', 1.2);
    expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({
      severity: 'success',
      summary: 't:book.coverPref.toast.savedSummary',
      detail: 'book.coverPref.toast.savedDetail:{"scale":"1.20"}',
      life: 1500
    }));

    vi.clearAllMocks();
    localStorageService.set.mockImplementation(() => {
      throw new Error('storage unavailable');
    });

    service.setScale(1.3);
    vi.advanceTimersByTime(1000);

    expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({
      severity: 'error',
      summary: 't:book.coverPref.toast.saveFailedSummary',
      detail: 't:book.coverPref.toast.saveFailedDetail',
      life: 3000
    }));
  });
});
