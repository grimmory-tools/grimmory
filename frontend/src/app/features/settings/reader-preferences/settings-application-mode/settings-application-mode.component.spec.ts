import {ComponentFixture, TestBed} from '@angular/core/testing';
import {beforeEach, describe, expect, it, vi} from 'vitest';

import {SettingsApplicationModeComponent} from './settings-application-mode.component';
import {ReaderPreferencesService} from '../reader-preferences.service';
import {TranslocoService} from '@jsverse/transloco';
import {UserSettings} from '../../user-management/user.service';

describe('SettingsApplicationModeComponent', () => {
  function createUserSettings(): UserSettings {
    return {
      perBookSetting: {pdf: 'Global', epub: 'Individual', cbx: 'Global'},
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

  let fixture: ComponentFixture<SettingsApplicationModeComponent>;
  let updatePreference: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    updatePreference = vi.fn();

    TestBed.configureTestingModule({
      imports: [SettingsApplicationModeComponent],
      providers: [
        {provide: ReaderPreferencesService, useValue: {updatePreference}},
        {provide: TranslocoService, useValue: {translate: vi.fn((key: string) => key)}},
      ]
    });

    fixture = TestBed.createComponent(SettingsApplicationModeComponent);
    fixture.componentInstance.userSettings = createUserSettings();
  });

  it('loads preferences and forwards updates for each scope', () => {
    const component = fixture.componentInstance;

    component.ngOnInit();

    expect(component.selectedPdfScope).toBe('Global');
    expect(component.selectedNewPdfScope).toBe('Global');
    expect(component.selectedEpubScope).toBe('Individual');
    expect(component.selectedCbxScope).toBe('Global');

    component.selectedPdfScope = 'Individual';
    component.selectedNewPdfScope = 'Individual';
    component.selectedEpubScope = 'Global';
    component.selectedCbxScope = 'Individual';

    component.onPdfScopeChange();
    component.onNewPdfScopeChange();
    component.onEpubScopeChange();
    component.onCbxScopeChange();

    expect(updatePreference).toHaveBeenNthCalledWith(1, ['perBookSetting', 'pdf'], 'Individual');
    expect(updatePreference).toHaveBeenNthCalledWith(2, ['perBookSetting', 'newPdf'], 'Individual');
    expect(updatePreference).toHaveBeenNthCalledWith(3, ['perBookSetting', 'epub'], 'Global');
    expect(updatePreference).toHaveBeenNthCalledWith(4, ['perBookSetting', 'cbx'], 'Individual');
  });
});
