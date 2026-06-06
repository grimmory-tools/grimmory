import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {describe, expect, it, vi} from 'vitest';

import {ReaderPreferencesService} from './reader-preferences.service';
import {User, UserService, UserSettings} from '../user-management/user.service';
import {MessageService} from 'primeng/api';
import {TranslocoService} from '@jsverse/transloco';

describe('ReaderPreferencesService', () => {
  function createUserSettings(): UserSettings {
    return {
      perBookSetting: {pdf: 'pdf', epub: 'epub', cbx: 'cbx'},
      pdfReaderSetting: {pageSpread: 'off', pageZoom: '100%', showSidebar: true},
      epubReaderSetting: {theme: 'light', font: 'serif', fontSize: 16, flow: 'paginated', spread: 'auto', lineHeight: 1.5, margin: 1, letterSpacing: 0},
      ebookReaderSetting: {lineHeight: 1.5, justify: true, hyphenate: true, maxColumnCount: 1, gap: 1, fontSize: 16, theme: 'light', maxInlineSize: 100, maxBlockSize: 100, fontFamily: 'serif', isDark: false, flow: 'paginated'},
      cbxReaderSetting: {pageSpread: 'EVEN', pageViewMode: 'SINGLE_PAGE', fitMode: 'AUTO'},
      newPdfReaderSetting: {pageSpread: 'EVEN', pageViewMode: 'SINGLE_PAGE', fitMode: 'AUTO'},
      sidebarLibrarySorting: {field: 'name', order: 'ASC'},
      sidebarShelfSorting: {field: 'name', order: 'ASC'},
      sidebarMagicShelfSorting: {field: 'name', order: 'ASC'},
      filterMode: 'and',
      metadataCenterViewMode: 'route',
      enableSeriesView: true,
      entityViewPreferences: {global: {sortKey: 'title', sortDir: 'ASC', view: 'GRID', coverSize: 100, seriesCollapsed: false, overlayBookType: false}, overrides: []},
      koReaderEnabled: false,
      autoSaveMetadata: true,
    } as UserSettings;
  }

  it('updates a nested preference and notifies the backend', () => {
    const userSettings = createUserSettings();
    const currentUser = signal<User | null>({
      id: 7,
      userSettings,
      permissions: {
        admin: false,
        canUpload: false,
        canDownload: false,
        canEmailBook: false,
        canDeleteBook: false,
        canEditMetadata: false,
        canManageLibrary: false,
        canManageMetadataConfig: false,
        canSyncKoReader: false,
        canSyncKobo: false,
        canAccessOpds: false,
        canAccessBookdrop: false,
        canAccessLibraryStats: false,
        canAccessUserStats: false,
        canAccessTaskManager: false,
        canManageEmailConfig: false,
        canManageGlobalPreferences: false,
        canManageIcons: false,
        canManageFonts: true,
        demoUser: false,
        canBulkAutoFetchMetadata: false,
        canBulkCustomFetchMetadata: false,
        canBulkEditMetadata: false,
        canBulkRegenerateCover: false,
        canMoveOrganizeFiles: false,
        canBulkLockUnlockMetadata: false,
      } as unknown as User['permissions'],
      username: 'reader',
      name: 'Reader',
      email: 'reader@example.test',
      assignedLibraries: [],
    } as unknown as User);
    const updateUserSetting = vi.fn();
    const messageAdd = vi.fn();
    const translate = vi.fn(key => key);

    TestBed.configureTestingModule({
      providers: [
        ReaderPreferencesService,
        {provide: UserService, useValue: {currentUser, updateUserSetting}},
        {provide: MessageService, useValue: {add: messageAdd}},
        {provide: TranslocoService, useValue: {translate}},
      ]
    });

    const service = TestBed.inject(ReaderPreferencesService);
    TestBed.flushEffects();

    service.updatePreference(['ebookReaderSetting', 'fontSize'], 22);

    expect(updateUserSetting).toHaveBeenCalledWith(7, 'ebookReaderSetting', expect.objectContaining({fontSize: 22}));
    expect(messageAdd).toHaveBeenCalledWith({
      severity: 'success',
      summary: 'settingsReader.toast.preferencesUpdated',
      detail: 'settingsReader.toast.preferencesUpdatedDetail',
      life: 2000
    });
  });

  it('ignores updates until the current user is available', () => {
    const currentUser = signal(null);
    const updateUserSetting = vi.fn();

    TestBed.configureTestingModule({
      providers: [
        ReaderPreferencesService,
        {provide: UserService, useValue: {currentUser, updateUserSetting}},
        {provide: MessageService, useValue: {add: vi.fn()}},
        {provide: TranslocoService, useValue: {translate: vi.fn()}},
      ]
    });

    const service = TestBed.inject(ReaderPreferencesService);

    expect(() => service.updatePreference(['ebookReaderSetting', 'fontSize'], 22)).not.toThrow();
    expect(updateUserSetting).not.toHaveBeenCalled();
  });

  it('creates missing nested branches before persisting a preference', () => {
    const userSettings = createUserSettings();
    const currentUser = signal<User | null>({
      id: 7,
      userSettings,
      permissions: {
        admin: false,
        canUpload: false,
        canDownload: false,
        canEmailBook: false,
        canDeleteBook: false,
        canEditMetadata: false,
        canManageLibrary: false,
        canManageMetadataConfig: false,
        canSyncKoReader: false,
        canSyncKobo: false,
        canAccessOpds: false,
        canAccessBookdrop: false,
        canAccessLibraryStats: false,
        canAccessUserStats: false,
        canAccessTaskManager: false,
        canManageEmailConfig: false,
        canManageGlobalPreferences: false,
        canManageIcons: false,
        canManageFonts: true,
        demoUser: false,
        canBulkAutoFetchMetadata: false,
        canBulkCustomFetchMetadata: false,
        canBulkEditMetadata: false,
        canBulkRegenerateCover: false,
        canMoveOrganizeFiles: false,
        canBulkLockUnlockMetadata: false,
      } as unknown as User['permissions'],
      username: 'reader',
      name: 'Reader',
      email: 'reader@example.test',
      assignedLibraries: [],
    } as unknown as User);
    const updateUserSetting = vi.fn();

    TestBed.configureTestingModule({
      providers: [
        ReaderPreferencesService,
        {provide: UserService, useValue: {currentUser, updateUserSetting}},
        {provide: MessageService, useValue: {add: vi.fn()}},
        {provide: TranslocoService, useValue: {translate: vi.fn()}},
      ]
    });

    const service = TestBed.inject(ReaderPreferencesService);
    TestBed.flushEffects();

    service.updatePreference(['ebookReaderSetting', 'advanced', 'fontSize'], 22);

    expect(updateUserSetting).toHaveBeenCalledWith(7, 'ebookReaderSetting', expect.objectContaining({
      advanced: {fontSize: 22}
    }));
    expect(userSettings.ebookReaderSetting).toEqual(expect.objectContaining({
      advanced: {fontSize: 22}
    }));
  });
});
