import {signal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {MessageService} from 'primeng/api';

import {getTranslocoModule} from '../../../../core/testing/transloco-testing';
import {User, UserService, UserSettings} from '../../user-management/user.service';
import {SidebarSortingPreferencesComponent} from './sidebar-sorting-preferences.component';

function createUserSettings(): UserSettings {
  return {
    perBookSetting: {pdf: 'Global', epub: 'Individual', cbx: 'Global'},
    pdfReaderSetting: {pageSpread: 'off', pageZoom: '100%', showSidebar: true},
    epubReaderSetting: {theme: 'light', font: 'serif', fontSize: 16, flow: 'paginated', spread: 'auto', lineHeight: 1.5, margin: 1, letterSpacing: 0},
    ebookReaderSetting: {lineHeight: 1.5, justify: true, hyphenate: true, maxColumnCount: 1, gap: 1, fontSize: 16, theme: 'light', maxInlineSize: 100, maxBlockSize: 100, fontFamily: 'serif', isDark: false, flow: 'paginated'},
    cbxReaderSetting: {pageSpread: 'EVEN', pageViewMode: 'SINGLE_PAGE', fitMode: 'AUTO'},
    newPdfReaderSetting: {pageSpread: 'EVEN', pageViewMode: 'SINGLE_PAGE', fitMode: 'AUTO'},
    sidebarLibrarySorting: {field: 'name', order: 'asc'},
    sidebarShelfSorting: {field: 'id', order: 'desc'},
    sidebarMagicShelfSorting: {field: 'name', order: 'desc'},
    filterMode: 'and',
    metadataCenterViewMode: 'route',
    enableSeriesView: true,
    entityViewPreferences: {global: {sortKey: 'title', sortDir: 'ASC', view: 'GRID', coverSize: 100, seriesCollapsed: false, overlayBookType: false}, overrides: []},
    koReaderEnabled: false,
    autoSaveMetadata: true,
  } as UserSettings;
}

function createUser(): User {
  return {
    id: 9,
    username: 'tester',
    name: 'Tester',
    email: 'tester@example.com',
    assignedLibraries: [],
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
      canManageFonts: false,
      demoUser: false,
      canBulkAutoFetchMetadata: false,
      canBulkCustomFetchMetadata: false,
      canBulkEditMetadata: false,
      canBulkRegenerateCover: false,
      canMoveOrganizeFiles: false,
      canBulkLockUnlockMetadata: false,
    },
    userSettings: createUserSettings(),
  };
}

describe('SidebarSortingPreferencesComponent', () => {
  let fixture: ComponentFixture<SidebarSortingPreferencesComponent>;
  let updateUserSetting: ReturnType<typeof vi.fn>;
  let messageService: {add: ReturnType<typeof vi.fn>};

  beforeEach(() => {
    updateUserSetting = vi.fn();
    messageService = {add: vi.fn()};

    TestBed.configureTestingModule({
      imports: [SidebarSortingPreferencesComponent, getTranslocoModule({translocoConfig: {reRenderOnLangChange: false}})],
      providers: [
        {provide: UserService, useValue: {currentUser: signal(createUser()), updateUserSetting}},
        {provide: MessageService, useValue: messageService},
      ],
    });

    fixture = TestBed.createComponent(SidebarSortingPreferencesComponent);
    TestBed.flushEffects();
    fixture.detectChanges();
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('hydrates sidebar sorting selections from the current user', () => {
    const component = fixture.componentInstance;

    expect(component.selectedLibrarySorting).toEqual({field: 'name', order: 'asc'});
    expect(component.selectedShelfSorting).toEqual({field: 'id', order: 'desc'});
    expect(component.sortingOptions).not.toHaveLength(0);
  });

  it('persists library sorting changes and shows a success toast', () => {
    const component = fixture.componentInstance;
    component.selectedLibrarySorting = {field: 'id', order: 'asc'};

    component.onLibrarySortingChange();

    expect(updateUserSetting).toHaveBeenCalledWith(9, 'sidebarLibrarySorting', {field: 'id', order: 'asc'});
    expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'success'}));
  });
});
