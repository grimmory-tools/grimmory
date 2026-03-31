import {TestBed} from '@angular/core/testing';
import {MessageService} from 'primeng/api';
import {TranslocoService} from '@jsverse/transloco';
import {vi, describe, beforeEach, afterEach, expect, it} from 'vitest';

import {TableColumnPreferenceService} from './table-column-preference.service';
import {TableColumnPreference, UserService} from '../../../settings/user-management/user.service';

describe('TableColumnPreferenceService', () => {
  let service: TableColumnPreferenceService;
  let userService: { getCurrentUser: ReturnType<typeof vi.fn>; updateUserSetting: ReturnType<typeof vi.fn> };
  let messageService: { add: ReturnType<typeof vi.fn> };
  let translocoService: { translate: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    userService = {
      getCurrentUser: vi.fn(),
      updateUserSetting: vi.fn()
    };
    messageService = {
      add: vi.fn()
    };
    translocoService = {
      translate: vi.fn((key: string) => `t:${key}`)
    };

    TestBed.configureTestingModule({
      providers: [
        TableColumnPreferenceService,
        {provide: UserService, useValue: userService},
        {provide: MessageService, useValue: messageService},
        {provide: TranslocoService, useValue: translocoService}
      ]
    });

    service = TestBed.inject(TableColumnPreferenceService);
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('merges saved preferences with all available columns', () => {
    const savedPrefs: TableColumnPreference[] = [
      {field: 'title', visible: false, order: 4},
      {field: 'authors', visible: true, order: 1},
      {field: 'fileName', visible: true, order: 0}
    ];

    service.initPreferences(savedPrefs);

    expect(service.preferences().find(pref => pref.field === 'title')).toEqual({
      field: 'title',
      visible: false,
      order: 4
    });
    expect(service.preferences().find(pref => pref.field === 'authors')).toEqual({
      field: 'authors',
      visible: true,
      order: 1
    });
    expect(service.allColumns[0]).toEqual({
      field: 'readStatus',
      header: 't:book.columnPref.columns.readStatus'
    });
    expect(service.visibleColumns.map(column => column.field).slice(0, 2)).toEqual(['readStatus', 'fileName']);
  });

  it('falls back to defaults when no saved preferences exist', () => {
    service.initPreferences(undefined);

    expect(service.preferences().length).toBe(service.allColumns.length);
    expect(service.visibleColumns[0]).toEqual({
      field: 'readStatus',
      header: 't:book.columnPref.columns.readStatus'
    });
  });

  it('skips persistence when there is no current user', () => {
    userService.getCurrentUser.mockReturnValue(null);

    service.saveVisibleColumns([{field: 'title'}]);

    expect(userService.updateUserSetting).not.toHaveBeenCalled();
    expect(messageService.add).not.toHaveBeenCalled();
    expect(service.preferences().find(pref => pref.field === 'title')).toEqual({
      field: 'title',
      visible: true,
      order: 0
    });
  });

  it('saves preferences and shows a success toast for the current user', () => {
    userService.getCurrentUser.mockReturnValue({id: 42});

    service.saveVisibleColumns([{field: 'title'}, {field: 'authors'}]);

    expect(userService.updateUserSetting).toHaveBeenCalledWith(42, 'tableColumnPreference', expect.any(Array));
    const savedPrefs = userService.updateUserSetting.mock.calls[0][2] as TableColumnPreference[];
    expect(savedPrefs.find(pref => pref.field === 'title')).toEqual({
      field: 'title',
      visible: true,
      order: 0
    });
    expect(savedPrefs.find(pref => pref.field === 'authors')).toEqual({
      field: 'authors',
      visible: true,
      order: 1
    });
    expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({
      severity: 'success',
      summary: 't:book.columnPref.toast.savedSummary',
      detail: 't:book.columnPref.toast.savedDetail',
      life: 1500
    }));
  });
});
