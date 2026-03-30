import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {of} from 'rxjs';
import {describe, expect, it, vi} from 'vitest';

import {MetaCenterViewModeComponent} from './meta-center-view-mode-component';
import {User, UserService} from '../../user-management/user.service';
import {MessageService} from 'primeng/api';
import {TranslocoService} from '@jsverse/transloco';

describe('MetaCenterViewModeComponent', () => {
  function createUser(preferences: Partial<User['userSettings']> = {}): User {
    return {
      id: 7,
      userSettings: {
        metadataCenterViewMode: 'dialog',
        enableSeriesView: true,
        perBookSetting: {pdf: 'Global', epub: 'Global', cbx: 'Global'},
        pdfReaderSetting: {pageSpread: 'off', pageZoom: '100%', showSidebar: true},
        epubReaderSetting: {theme: 'light', font: 'serif', fontSize: 16, flow: 'paginated', spread: 'auto', lineHeight: 1.5, margin: 1, letterSpacing: 0},
        ebookReaderSetting: {lineHeight: 1.5, justify: true, hyphenate: true, maxColumnCount: 1, gap: 1, fontSize: 16, theme: 'light', maxInlineSize: 100, maxBlockSize: 100, fontFamily: 'serif', isDark: false, flow: 'paginated'},
        cbxReaderSetting: {pageSpread: 'EVEN', pageViewMode: 'SINGLE_PAGE', fitMode: 'AUTO'},
        newPdfReaderSetting: {pageSpread: 'EVEN', pageViewMode: 'SINGLE_PAGE', fitMode: 'AUTO'},
        sidebarLibrarySorting: {field: 'name', order: 'ASC'},
        sidebarShelfSorting: {field: 'name', order: 'ASC'},
        sidebarMagicShelfSorting: {field: 'name', order: 'ASC'},
        filterMode: 'and',
        entityViewPreferences: {global: {sortKey: 'title', sortDir: 'ASC', view: 'GRID', coverSize: 100, seriesCollapsed: false, overlayBookType: false}, overrides: []},
        koReaderEnabled: false,
        autoSaveMetadata: true,
        ...preferences,
      },
      permissions: {admin: false, canManageFonts: true},
      username: 'reader',
      name: 'Reader',
      email: 'reader@example.test',
      assignedLibraries: [],
    } as unknown as User;
  }

  it('hydrates preferences from the current user and persists changes', () => {
    const currentUser = signal<User | null>(null);
    const updateUserSetting = vi.fn();
    const messageAdd = vi.fn();
    const translate = vi.fn((key: string) => key);

    TestBed.configureTestingModule({
      imports: [MetaCenterViewModeComponent],
      providers: [
        {provide: UserService, useValue: {currentUser, getCurrentUser: vi.fn(() => currentUser()), updateUserSetting}},
        {provide: MessageService, useValue: {add: messageAdd}},
        {provide: TranslocoService, useValue: {
          translate,
          config: {reRenderOnLangChange: false},
          langChanges$: of('en'),
          _loadDependencies: vi.fn(() => of([])),
        }},
      ]
    });

    currentUser.set(createUser());

    const fixture = TestBed.createComponent(MetaCenterViewModeComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    expect(component.viewMode).toBe('dialog');
    expect(component.seriesViewMode).toBe(true);

    component.onViewModeChange('route');
    component.onSeriesViewModeChange(false);

    expect(updateUserSetting).toHaveBeenNthCalledWith(1, 7, 'metadataCenterViewMode', 'route');
    expect(updateUserSetting).toHaveBeenNthCalledWith(2, 7, 'enableSeriesView', false);
    expect(messageAdd).toHaveBeenNthCalledWith(1, {
      severity: 'success',
      summary: 'settingsView.metaCenter.prefsUpdated',
      detail: 'settingsView.metaCenter.metaCenterSaved',
      life: 1500,
    });
    expect(messageAdd).toHaveBeenNthCalledWith(2, {
      severity: 'success',
      summary: 'settingsView.metaCenter.prefsUpdated',
      detail: 'settingsView.metaCenter.seriesViewSaved',
      life: 1500,
    });
  });

  it('ignores save attempts until a user is available', () => {
    const currentUser = signal<User | null>(null);
    const updateUserSetting = vi.fn();

    TestBed.configureTestingModule({
      imports: [MetaCenterViewModeComponent],
      providers: [
        {provide: UserService, useValue: {currentUser, getCurrentUser: vi.fn(() => null), updateUserSetting}},
        {provide: MessageService, useValue: {add: vi.fn()}},
        {provide: TranslocoService, useValue: {
          translate: vi.fn(),
          config: {reRenderOnLangChange: false},
          langChanges$: of('en'),
          _loadDependencies: vi.fn(() => of([])),
        }},
      ]
    });

    const fixture = TestBed.createComponent(MetaCenterViewModeComponent);
    const component = fixture.componentInstance;

    component.onViewModeChange('dialog');
    component.onSeriesViewModeChange(true);

    expect(updateUserSetting).not.toHaveBeenCalled();
  });
});
