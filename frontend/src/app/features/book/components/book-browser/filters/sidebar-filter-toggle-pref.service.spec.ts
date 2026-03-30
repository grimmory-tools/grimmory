import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {TranslocoService} from '@jsverse/transloco';
import {MessageService} from 'primeng/api';

import {LocalStorageService} from '../../../../../shared/service/local-storage.service';
import {SidebarFilterTogglePrefService} from './sidebar-filter-toggle-pref.service';

describe('SidebarFilterTogglePrefService', () => {
  let originalInnerWidth: number;
  let messageService: {add: ReturnType<typeof vi.fn>};
  let localStorageService: {
    get: ReturnType<typeof vi.fn>;
    set: ReturnType<typeof vi.fn>;
  };

  const setViewport = (width: number) => {
    Object.defineProperty(window, 'innerWidth', {
      configurable: true,
      value: width,
    });
  };

  beforeEach(() => {
    originalInnerWidth = window.innerWidth;
    messageService = {add: vi.fn()};
    localStorageService = {
      get: vi.fn(() => null),
      set: vi.fn(),
    };
  });

  afterEach(() => {
    setViewport(originalInnerWidth);
    TestBed.resetTestingModule();
  });

  const createService = () => {
    TestBed.configureTestingModule({
      providers: [
        SidebarFilterTogglePrefService,
        {provide: MessageService, useValue: messageService},
        {provide: LocalStorageService, useValue: localStorageService},
        {provide: TranslocoService, useValue: {translate: (key: string) => key}},
      ],
    });

    return TestBed.inject(SidebarFilterTogglePrefService);
  };

  it('loads the stored preference on wide screens', () => {
    setViewport(1280);
    localStorageService.get.mockReturnValue(false);

    const service = createService();

    expect(service.showFilter()).toBe(false);
    expect(localStorageService.get).toHaveBeenCalledWith('showSidebarFilter');
  });

  it('forces the filter closed on narrow screens', () => {
    setViewport(640);

    const service = createService();

    expect(service.showFilter()).toBe(false);
    expect(localStorageService.get).not.toHaveBeenCalled();
  });

  it('toggles the visibility and persists the updated value', () => {
    setViewport(1280);
    const service = createService();

    service.toggle();

    expect(service.showFilter()).toBe(false);
    expect(localStorageService.set).toHaveBeenCalledWith('showSidebarFilter', false);
  });

  it('shows an error toast when persistence fails', () => {
    setViewport(1280);
    localStorageService.set.mockImplementation(() => {
      throw new Error('storage failed');
    });
    const service = createService();

    service.setShowFilter(false);

    expect(messageService.add).toHaveBeenCalledWith(
      expect.objectContaining({
        severity: 'error',
        summary: 'book.filterPref.toast.saveFailedSummary',
        detail: 'book.filterPref.toast.saveFailedDetail',
      })
    );
  });
});
