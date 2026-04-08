import {ComponentFixture, TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {getTranslocoModule} from '../../../../core/testing/transloco-testing';
import {ReaderPreferencesService} from '../reader-preferences.service';
import {CbxBackgroundColor, CbxFitMode, CbxPageSpread, CbxPageViewMode, CbxScrollMode, UserSettings} from '../../user-management/user.service';
import {CbxReaderPreferencesComponent} from './cbx-reader-preferences-component';

function createUserSettings(): UserSettings {
  return {
    perBookSetting: {pdf: 'Global', epub: 'Individual', cbx: 'Global'},
    pdfReaderSetting: {pageSpread: 'off', pageZoom: '100%', showSidebar: true},
    epubReaderSetting: {theme: 'light', font: 'serif', fontSize: 16, flow: 'paginated', spread: 'auto', lineHeight: 1.5, margin: 1, letterSpacing: 0},
    ebookReaderSetting: {lineHeight: 1.5, justify: true, hyphenate: true, maxColumnCount: 1, gap: 1, fontSize: 16, theme: 'light', maxInlineSize: 100, maxBlockSize: 100, fontFamily: 'serif', isDark: false, flow: 'paginated'},
    cbxReaderSetting: {pageSpread: CbxPageSpread.EVEN, pageViewMode: CbxPageViewMode.SINGLE_PAGE, fitMode: CbxFitMode.AUTO},
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

describe('CbxReaderPreferencesComponent', () => {
  let fixture: ComponentFixture<CbxReaderPreferencesComponent>;
  let updatePreference: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    updatePreference = vi.fn();

    TestBed.configureTestingModule({
      imports: [CbxReaderPreferencesComponent, getTranslocoModule()],
      providers: [
        {provide: ReaderPreferencesService, useValue: {updatePreference}},
      ],
    });

    fixture = TestBed.createComponent(CbxReaderPreferencesComponent);
    fixture.componentInstance.userSettings = createUserSettings();
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('returns configured defaults from the supplied user settings', () => {
    const component = fixture.componentInstance;

    expect(component.selectedCbxSpread).toBe(CbxPageSpread.EVEN);
    expect(component.selectedCbxViewMode).toBe(CbxPageViewMode.SINGLE_PAGE);
    expect(component.selectedCbxFitMode).toBe(CbxFitMode.AUTO);
    expect(component.selectedCbxScrollMode).toBe(CbxScrollMode.PAGINATED);
    expect(component.selectedCbxBackgroundColor).toBe(CbxBackgroundColor.GRAY);
    expect(component.selectedCbxStripMaxWidthPercent).toBe(100);
  });

  it('persists each CBX reader preference through the shared preferences service', () => {
    const component = fixture.componentInstance;

    component.selectedCbxSpread = CbxPageSpread.ODD;
    component.selectedCbxViewMode = CbxPageViewMode.TWO_PAGE;
    component.selectedCbxFitMode = CbxFitMode.FIT_WIDTH;
    component.selectedCbxScrollMode = CbxScrollMode.INFINITE;
    component.selectedCbxBackgroundColor = CbxBackgroundColor.BLACK;

    expect(updatePreference).toHaveBeenNthCalledWith(1, ['cbxReaderSetting', 'pageSpread'], CbxPageSpread.ODD);
    expect(updatePreference).toHaveBeenNthCalledWith(2, ['cbxReaderSetting', 'pageViewMode'], CbxPageViewMode.TWO_PAGE);
    expect(updatePreference).toHaveBeenNthCalledWith(3, ['cbxReaderSetting', 'fitMode'], CbxFitMode.FIT_WIDTH);
    expect(updatePreference).toHaveBeenNthCalledWith(4, ['cbxReaderSetting', 'scrollMode'], CbxScrollMode.INFINITE);
    expect(updatePreference).toHaveBeenNthCalledWith(5, ['cbxReaderSetting', 'backgroundColor'], CbxBackgroundColor.BLACK);
  });

  it('persists strip max width with silent preference updates', () => {
    const component = fixture.componentInstance;

    component.selectedCbxStripMaxWidthPercent = 75;

    expect(updatePreference).toHaveBeenCalledWith(
      ['cbxReaderSetting', 'stripMaxWidthPercent'],
      75,
      { silent: true }
    );
  });
});
