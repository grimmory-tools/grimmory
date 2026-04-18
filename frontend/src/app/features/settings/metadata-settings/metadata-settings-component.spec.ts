import {signal, type WritableSignal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {getTranslocoModule} from '../../../core/testing/transloco-testing';
import {AppSettingKey, type AppSettings} from '../../../shared/model/app-settings.model';
import {AppSettingsService} from '../../../shared/service/app-settings.service';
import {SettingsHelperService} from '../../../shared/service/settings-helper.service';
import {type MetadataRefreshOptions} from '../../metadata/model/request/metadata-refresh-options.model';
import {MetadataSettingsComponent} from './metadata-settings-component';

describe('MetadataSettingsComponent', () => {
  let fixture: ComponentFixture<MetadataSettingsComponent>;
  let component: MetadataSettingsComponent;
  let appSettingsSignal: WritableSignal<AppSettings | null>;
  let settingsHelper: {saveSetting: ReturnType<typeof vi.fn>};

  beforeEach(async () => {
    appSettingsSignal = signal<AppSettings | null>(null);
    settingsHelper = {
      saveSetting: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [MetadataSettingsComponent, getTranslocoModule()],
      providers: [
        {provide: AppSettingsService, useValue: {appSettings: appSettingsSignal}},
        {provide: SettingsHelperService, useValue: settingsHelper},
      ],
    })
      .overrideComponent(MetadataSettingsComponent, {
        set: {
          template: '',
          imports: [],
        },
      })
      .compileComponents();

    fixture = TestBed.createComponent(MetadataSettingsComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('hydrates when metadata settings arrive after initial render', async () => {
    await render();

    expect(component.form.controls.metadataDownloadOnBookdrop.value).toBe(true);
    expect(component.currentMetadataOptions).toBeUndefined();

    const options = buildMetadataRefreshOptions();
    appSettingsSignal.set({
      defaultMetadataRefreshOptions: options,
      metadataDownloadOnBookdrop: false,
    } as AppSettings);
    await render();

    expect(component.form.controls.metadataDownloadOnBookdrop.value).toBe(false);
    expect(component.currentMetadataOptions).toEqual(options);
  });

  it('persists bookdrop download toggles and metadata option submissions', async () => {
    await render();

    component.onMetadataDownloadOnBookdropToggle(false);

    const options = buildMetadataRefreshOptions();
    component.onMetadataSubmit(options);

    expect(settingsHelper.saveSetting).toHaveBeenNthCalledWith(
      1,
      AppSettingKey.METADATA_DOWNLOAD_ON_BOOKDROP,
      false
    );
    expect(settingsHelper.saveSetting).toHaveBeenNthCalledWith(
      2,
      AppSettingKey.QUICK_BOOK_MATCH,
      options
    );
  });

  async function render(): Promise<void> {
    fixture.detectChanges();
    await new Promise(resolve => setTimeout(resolve, 0));
    await fixture.whenStable();
    fixture.detectChanges();
  }
});

function buildMetadataRefreshOptions(): MetadataRefreshOptions {
  const FIELDS = [
    'title', 'subtitle', 'description', 'authors', 'publisher', 'publishedDate',
    'seriesName', 'seriesNumber', 'seriesTotal', 'isbn13', 'isbn10', 'language',
    'pageCount', 'categories', 'cover', 'asin', 'goodreadsId', 'comicvineId',
    'hardcoverId', 'hardcoverBookId', 'googleId', 'lubimyczytacId',
    'amazonRating', 'amazonReviewCount', 'goodreadsRating', 'goodreadsReviewCount',
    'hardcoverRating', 'hardcoverReviewCount', 'lubimyczytacRating',
    'ranobedbId', 'ranobedbRating', 'audibleId', 'audibleRating',
    'audibleReviewCount', 'moods', 'tags',
  ] as const;

  const fieldOptions = Object.fromEntries(
    FIELDS.map(field => [field, {p1: field === 'title' ? 'Amazon' : null, p2: null, p3: null, p4: null}])
  ) as unknown as MetadataRefreshOptions['fieldOptions'];

  const enabledFields = Object.fromEntries(
    FIELDS.map(field => [field, true])
  ) as unknown as MetadataRefreshOptions['enabledFields'];

  return {
    libraryId: null,
    refreshCovers: false,
    mergeCategories: true,
    reviewBeforeApply: false,
    replaceMode: 'REPLACE_MISSING',
    fieldOptions,
    enabledFields,
  };
}
