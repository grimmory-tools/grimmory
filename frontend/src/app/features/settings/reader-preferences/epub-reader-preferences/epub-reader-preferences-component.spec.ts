import {ComponentFixture, TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {getTranslocoModule} from '../../../../core/testing/transloco-testing';
import {CustomFontService} from '../../../../shared/service/custom-font.service';
import {COLUMN_COUNT_LIMITS} from '../../../readers/ebook-reader/state/reader-layout.constant';
import {UserSettings} from '../../user-management/user.service';
import {ReaderPreferencesService} from '../reader-preferences.service';
import {EpubReaderPreferencesComponent} from './epub-reader-preferences-component';

function createUserSettings(maxColumnCount: number): UserSettings {
  return {
    ebookReaderSetting: {lineHeight: 1.5, justify: true, hyphenate: true, maxColumnCount, gap: 1, fontSize: 16, theme: 'light', maxInlineSize: 100, maxBlockSize: 100, fontFamily: 'serif', isDark: false, flow: 'paginated'},
  } as UserSettings;
}

describe('EpubReaderPreferencesComponent max columns', () => {
  let fixture: ComponentFixture<EpubReaderPreferencesComponent>;
  let updatePreference: ReturnType<typeof vi.fn>;

  function createComponent(maxColumnCount: number): EpubReaderPreferencesComponent {
    fixture.componentRef.setInput('userSettings', createUserSettings(maxColumnCount));
    return fixture.componentInstance;
  }

  beforeEach(() => {
    updatePreference = vi.fn();

    TestBed.configureTestingModule({
      imports: [EpubReaderPreferencesComponent, getTranslocoModule()],
      providers: [
        {provide: ReaderPreferencesService, useValue: {updatePreference}},
        {
          provide: CustomFontService,
          useValue: {
            fonts: () => [],
            isFontsReady: () => true,
            isFontsLoading: () => false,
            loadAllFonts: vi.fn(() => Promise.resolve()),
          },
        },
      ],
    });

    fixture = TestBed.createComponent(EpubReaderPreferencesComponent);
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('increases up to the shared maximum and saves the change', () => {
    const component = createComponent(COLUMN_COUNT_LIMITS.max - 1);

    component.increaseMaxColumnCount();

    expect(component.maxColumnCount).toBe(COLUMN_COUNT_LIMITS.max);
    expect(updatePreference).toHaveBeenCalledWith(['ebookReaderSetting', 'maxColumnCount'], COLUMN_COUNT_LIMITS.max);
  });

  it('does not go above the maximum or save when already at it', () => {
    const component = createComponent(COLUMN_COUNT_LIMITS.max);

    component.increaseMaxColumnCount();

    expect(component.maxColumnCount).toBe(COLUMN_COUNT_LIMITS.max);
    expect(updatePreference).not.toHaveBeenCalled();
  });

  it('does not go below the minimum or save when already at it', () => {
    const component = createComponent(COLUMN_COUNT_LIMITS.min);

    component.decreaseMaxColumnCount();

    expect(component.maxColumnCount).toBe(COLUMN_COUNT_LIMITS.min);
    expect(updatePreference).not.toHaveBeenCalled();
  });

  it('uses the same 1 to 10 range as the in-reader setting', () => {
    expect(COLUMN_COUNT_LIMITS).toEqual({min: 1, max: 10});
  });
});

// TODO(seam): EPUB reader preferences need a mounted settings harness around form controls,
// custom-font selection, and persisted user-setting mutations.
describe.skip('EpubReaderPreferencesComponent', () => {
  it('needs a mounted settings harness for theme, flow, and font preference branches', () => {
    expect.hasAssertions();
  });
});