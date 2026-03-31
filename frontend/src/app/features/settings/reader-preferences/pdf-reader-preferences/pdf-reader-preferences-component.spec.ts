import {ComponentFixture, TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {getTranslocoModule} from '../../../../core/testing/transloco-testing';
import {ReaderPreferencesService} from '../reader-preferences.service';
import {UserSettings} from '../../user-management/user.service';
import {PdfReaderPreferencesComponent} from './pdf-reader-preferences-component';

function createUserSettings(): UserSettings {
  return {
    perBookSetting: {pdf: 'Global', epub: 'Individual', cbx: 'Global'},
    pdfReaderSetting: {pageSpread: 'off', pageZoom: 'page-fit', showSidebar: true},
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

describe('PdfReaderPreferencesComponent', () => {
  let fixture: ComponentFixture<PdfReaderPreferencesComponent>;
  let updatePreference: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    updatePreference = vi.fn();

    TestBed.configureTestingModule({
      imports: [PdfReaderPreferencesComponent, getTranslocoModule()],
      providers: [
        {provide: ReaderPreferencesService, useValue: {updatePreference}},
      ],
    });

    fixture = TestBed.createComponent(PdfReaderPreferencesComponent);
    fixture.componentInstance.userSettings = createUserSettings();
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('reads the current spread and zoom from user settings', () => {
    const component = fixture.componentInstance;

    expect(component.selectedSpread).toBe('off');
    expect(component.selectedZoom).toBe('page-fit');
  });

  it('persists PDF reader preference changes through the shared preferences service', () => {
    const component = fixture.componentInstance;

    component.selectedSpread = 'even';
    component.selectedZoom = 'page-width';

    expect(updatePreference).toHaveBeenNthCalledWith(1, ['pdfReaderSetting', 'pageSpread'], 'even');
    expect(updatePreference).toHaveBeenNthCalledWith(2, ['pdfReaderSetting', 'pageZoom'], 'page-width');
  });
});
